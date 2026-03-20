package com.pkv.chat.service;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.domain.ChatResponseStatus;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.ChatSendRequest;
import com.pkv.chat.dto.ChatSendResponse;
import com.pkv.chat.dto.ChatSourceResponse;
import com.pkv.chat.repository.ChatHistoryRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.chat.repository.ChatSessionRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.repository.SourceRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    static final String NO_SEARCHABLE_SOURCE_MESSAGE = "임베딩한 파일이 없습니다";
    static final String IRRELEVANT_MESSAGE = "질문과 관련된 내용을 찾을 수 없습니다";
    static final String FAILED_MESSAGE = "답변 생성에 실패했습니다";

    private static final String UNKNOWN_FILE_NAME = "알 수 없는 파일";
    private static final int DEFAULT_PAGE_NUMBER = 1;

    private final SourceRepository sourceRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final PromptTemplateService promptTemplateService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistorySourceRepository chatHistorySourceRepository;

    @Transactional
    public ChatSendResponse sendMessage(Long memberId, ChatSendRequest request) {
        ChatSession session = resolveSession(memberId, request);
        if (session.isQuestionLimitReached(ChatPolicy.MAX_SESSION_QUESTION_COUNT)) {
            throw new PkvException(ErrorCode.CHAT_SESSION_LIMIT_EXCEEDED);
        }

        List<ConversationContext> conversationContexts = loadConversationContexts(session);
        ChatResult result = sendMessageCore(memberId, request.content(), conversationContexts);

        ChatHistory chatHistory = saveChatHistory(memberId, session, request.content(), result);
        saveChatHistorySources(chatHistory, result.retrievedSources());
        session.incrementQuestionCount();

        return new ChatSendResponse(session.getSessionKey(), result.content(), result.responseSources());
    }

    private ChatResult sendMessageCore(Long memberId, String question, List<ConversationContext> conversationContexts) {
        if (!sourceRepository.existsByMemberIdAndStatus(memberId, SourceStatus.COMPLETED)) {
            return failed(NO_SEARCHABLE_SOURCE_MESSAGE);
        }

        try {
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(ChatPolicy.MAX_RESULTS)
                    .minScore(ChatPolicy.MIN_SCORE)
                    .filter(metadataKey("memberId").isEqualTo(memberId))
                    .build();

            List<RetrievedSource> retrievedSources = embeddingStore.search(searchRequest).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .filter(Objects::nonNull)
                    .map(this::toRetrievedSource)
                    .toList();

            if (retrievedSources.isEmpty()) {
                return irrelevant();
            }

            String sourceBlock = buildSourceBlock(toResponseSources(retrievedSources));
            String contextBlock = buildConversationContextBlock(conversationContexts);
            String prompt = promptTemplateService.renderUserPrompt(question, sourceBlock, contextBlock);
            var modelResponse = chatModel.chat(List.of(
                    SystemMessage.from(promptTemplateService.systemPrompt()),
                    UserMessage.from(prompt)
            ));
            String answer = modelResponse.aiMessage() != null ? modelResponse.aiMessage().text() : null;

            if (answer == null || answer.isBlank()) {
                log.warn("LLM 응답이 비어있습니다. memberId={}", memberId);
                return failed(FAILED_MESSAGE);
            }

            return completed(answer, retrievedSources);
        } catch (Exception e) {
            log.error("채팅 처리 실패. memberId={}", memberId, e);
            return failed(FAILED_MESSAGE);
        }
    }

    private ChatSession resolveSession(Long memberId, ChatSendRequest request) {
        if (!StringUtils.hasText(request.sessionId())) {
            return createSession(memberId, request.content());
        }

        return chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(memberId, request.sessionId())
                .orElseThrow(() -> new PkvException(ErrorCode.CHAT_SESSION_NOT_FOUND));
    }

    private ChatSession createSession(Long memberId, String firstQuestion) {
        ChatSession session = ChatSession.create(
                memberId,
                UUID.randomUUID().toString(),
                firstQuestion,
                ChatPolicy.MAX_SESSION_TITLE_LENGTH
        );
        return chatSessionRepository.save(session);
    }

    private List<ConversationContext> loadConversationContexts(ChatSession session) {
        PageRequest contextLimit = PageRequest.of(0, ChatPolicy.MAX_CONTEXT_HISTORY);
        return chatHistoryRepository.findBySession_IdOrderByCreatedAtDesc(session.getId(), contextLimit).stream()
                .sorted(Comparator.comparing(ChatHistory::getCreatedAt))
                .map(chatHistory -> new ConversationContext(chatHistory.getQuestion(), chatHistory.getAnswer()))
                .toList();
    }

    private ChatHistory saveChatHistory(Long memberId, ChatSession session, String question, ChatResult result) {
        ChatHistory chatHistory = ChatHistory.create(
                memberId,
                session,
                question,
                result.status(),
                result.content()
        );
        return chatHistoryRepository.save(chatHistory);
    }

    private void saveChatHistorySources(ChatHistory chatHistory, List<RetrievedSource> retrievedSources) {
        if (retrievedSources.isEmpty()) {
            return;
        }

        List<ChatHistorySource> entities = IntStream.range(0, retrievedSources.size())
                .mapToObj(index -> {
                    RetrievedSource source = retrievedSources.get(index);
                    return ChatHistorySource.create(chatHistory, source.response(), source.sourceChunkRef(), index);
                })
                .toList();

        chatHistorySourceRepository.saveAll(entities);
    }

    private RetrievedSource toRetrievedSource(TextSegment segment) {
        String fileName = segment.metadata() != null ? segment.metadata().getString("fileName") : null;
        Integer pageNumber = segment.metadata() != null ? segment.metadata().getInteger("pageNumber") : null;
        String snippet = truncateSnippet(segment.text());
        Long sourceId = extractSourceId(segment);
        String sourceChunkRef = extractSourceChunkRef(segment);

        ChatSourceResponse response = new ChatSourceResponse(
                sourceId,
                fileName == null || fileName.isBlank() ? UNKNOWN_FILE_NAME : fileName,
                pageNumber == null || pageNumber <= 0 ? DEFAULT_PAGE_NUMBER : pageNumber,
                snippet
        );
        return new RetrievedSource(response, sourceChunkRef);
    }

    private Long extractSourceId(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }

        try {
            return segment.metadata().getLong("sourceId");
        } catch (Exception e) {
            log.warn("sourceId 파싱 실패", e);
            return null;
        }
    }

    private String extractSourceChunkRef(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }

        try {
            String sourceChunkRef = segment.metadata().getString("sourceChunkRef");
            return (sourceChunkRef == null || sourceChunkRef.isBlank()) ? null : sourceChunkRef;
        } catch (Exception e) {
            log.warn("sourceChunkRef 파싱 실패", e);
            return null;
        }
    }

    private List<ChatSourceResponse> toResponseSources(List<RetrievedSource> retrievedSources) {
        return retrievedSources.stream()
                .map(RetrievedSource::response)
                .toList();
    }

    private String truncateSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= ChatHistorySource.MAX_SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, ChatHistorySource.MAX_SNIPPET_LENGTH);
    }

    private String buildSourceBlock(List<ChatSourceResponse> sources) {
        return IntStream.range(0, sources.size())
                .mapToObj(index -> formatSource(index + 1, sources.get(index)))
                .collect(Collectors.joining("\n"));
    }

    private String buildConversationContextBlock(List<ConversationContext> contexts) {
        if (contexts.isEmpty()) {
            return "";
        }

        return IntStream.range(0, contexts.size())
                .mapToObj(index -> formatContext(index + 1, contexts.get(index)))
                .collect(Collectors.joining("\n\n"));
    }

    private String formatContext(int order, ConversationContext context) {
        String answer = context.answer() == null ? "-" : context.answer();
        return """
                %d. 질문: %s
                답변: %s
                """.formatted(order, context.question(), answer);
    }

    private String formatSource(int order, ChatSourceResponse source) {
        return "%d. file=%s, page=%s, snippet=%s".formatted(
                order,
                source.fileName(),
                source.pageNumber(),
                source.snippet()
        );
    }

    private ChatResult completed(String answer, List<RetrievedSource> retrievedSources) {
        return new ChatResult(ChatResponseStatus.COMPLETED, answer, retrievedSources);
    }

    private ChatResult irrelevant() {
        return new ChatResult(ChatResponseStatus.IRRELEVANT, IRRELEVANT_MESSAGE, List.of());
    }

    private ChatResult failed(String message) {
        return new ChatResult(ChatResponseStatus.FAILED, message, List.of());
    }

    private record ChatResult(ChatResponseStatus status, String content, List<RetrievedSource> retrievedSources) {
        private List<ChatSourceResponse> responseSources() {
            return retrievedSources.stream()
                    .map(RetrievedSource::response)
                    .toList();
        }
    }

    private record RetrievedSource(ChatSourceResponse response, String sourceChunkRef) {
    }

    private record ConversationContext(String question, String answer) {
    }
}
