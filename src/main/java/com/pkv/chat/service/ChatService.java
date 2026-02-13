package com.pkv.chat.service;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.domain.ChatHistoryStatus;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.ChatResponse;
import com.pkv.chat.dto.SourceReference;
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

    static final String NO_SEARCHABLE_SOURCE_MESSAGE = "검색 가능한 파일이 없습니다";
    static final String IRRELEVANT_MESSAGE = "관련 내용을 찾을 수 없습니다";
    static final String FAILED_MESSAGE = "답변 생성에 실패했습니다";

    private static final String UNKNOWN_FILE_NAME = "알 수 없는 파일";

    private static final String SYSTEM_PROMPT = """
            너는 사용자가 업로드한 문서를 기반으로 답변하는 어시스턴트다.
            제공된 출처를 벗어나 추측하지 말고, 질문에 대한 답을 한국어로 간결하게 작성해라.
            """;

    private final SourceRepository sourceRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistorySourceRepository chatHistorySourceRepository;

    @Transactional
    public ChatResponse sendMessage(Long memberId, ChatRequest request) {
        Objects.requireNonNull(memberId, "memberId is required");
        Objects.requireNonNull(request, "request is required");

        ChatSession session = resolveSession(memberId, request);
        validateSessionQuestionLimit(session);

        List<ConversationContext> conversationContexts = loadConversationContexts(session.getId());
        ChatResult result = sendMessageCore(memberId, request.content(), conversationContexts);

        ChatHistory chatHistory = saveChatHistory(memberId, session, request.content(), result);
        saveChatHistorySources(chatHistory, result.response().sources());
        session.incrementQuestionCount();

        return new ChatResponse(session.getSessionKey(), result.response().content(), result.response().sources());
    }

    ChatResult sendMessageCore(Long memberId, ChatRequest request) {
        Objects.requireNonNull(request, "request is required");
        return sendMessageCore(memberId, request.content(), List.of());
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

            List<SourceReference> sources = embeddingStore.search(searchRequest).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .filter(Objects::nonNull)
                    .limit(ChatPolicy.MAX_RESULTS)
                    .map(this::toSourceReference)
                    .toList();

            if (sources.isEmpty()) {
                return irrelevant();
            }

            String prompt = buildUserPrompt(question, sources, conversationContexts);
            var modelResponse = chatModel.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(prompt)
            ));
            String answer = modelResponse.aiMessage() != null ? modelResponse.aiMessage().text() : null;

            if (answer == null || answer.isBlank()) {
                log.warn("LLM 응답이 비어있습니다. memberId={}", memberId);
                return failed(FAILED_MESSAGE);
            }

            return completed(answer, sources);
        } catch (Exception e) {
            log.error("채팅 처리 실패. memberId={}", memberId, e);
            return failed(FAILED_MESSAGE);
        }
    }

    private ChatSession resolveSession(Long memberId, ChatRequest request) {
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
                ChatPolicy.MAX_TITLE_LENGTH
        );
        return chatSessionRepository.save(session);
    }

    private void validateSessionQuestionLimit(ChatSession session) {
        try {
            session.assertCanAsk(ChatPolicy.MAX_SESSION_QUESTION_COUNT);
        } catch (IllegalStateException e) {
            throw new PkvException(ErrorCode.CHAT_SESSION_LIMIT_EXCEEDED);
        }
    }

    private List<ConversationContext> loadConversationContexts(Long sessionId) {
        return chatHistoryRepository.findTop5BySession_IdOrderByCreatedAtDesc(sessionId).stream()
                .sorted(Comparator.comparing(ChatHistory::getCreatedAt))
                .limit(ChatPolicy.MAX_CONTEXT_HISTORY)
                .map(chatHistory -> new ConversationContext(chatHistory.getQuestion(), chatHistory.getAnswer()))
                .toList();
    }

    private ChatHistory saveChatHistory(Long memberId, ChatSession session, String question, ChatResult result) {
        ChatHistory chatHistory = ChatHistory.create(
                memberId,
                session,
                question,
                result.status(),
                result.response().content()
        );
        return chatHistoryRepository.save(chatHistory);
    }

    private void saveChatHistorySources(ChatHistory chatHistory, List<SourceReference> sources) {
        if (sources.isEmpty()) {
            return;
        }

        List<ChatHistorySource> entities = IntStream.range(0, sources.size())
                .mapToObj(index -> ChatHistorySource.from(chatHistory, sources.get(index), index))
                .toList();

        chatHistorySourceRepository.saveAll(entities);
    }

    private SourceReference toSourceReference(TextSegment segment) {
        String fileName = segment.metadata() != null ? segment.metadata().getString("fileName") : null;
        Integer pageNumber = segment.metadata() != null ? segment.metadata().getInteger("pageNumber") : null;
        String snippet = truncateSnippet(segment.text());
        Long sourceId = extractSourceId(segment);

        return new SourceReference(
                sourceId,
                fileName == null || fileName.isBlank() ? UNKNOWN_FILE_NAME : fileName,
                pageNumber,
                snippet
        );
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

    private String truncateSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= ChatHistorySource.MAX_SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, ChatHistorySource.MAX_SNIPPET_LENGTH);
    }

    private String buildUserPrompt(String question, List<SourceReference> sources, List<ConversationContext> contexts) {
        String sourceBlock = IntStream.range(0, sources.size())
                .mapToObj(index -> formatSource(index + 1, sources.get(index)))
                .collect(Collectors.joining("\n"));

        if (contexts.isEmpty()) {
            return """
                [질문]
                %s

                [검색된 출처]
                %s

                위 출처만 근거로 답변해줘.
                """.formatted(question, sourceBlock);
        }

        String contextBlock = IntStream.range(0, contexts.size())
                .mapToObj(index -> formatContext(index + 1, contexts.get(index)))
                .collect(Collectors.joining("\n\n"));

        return """
                [이전 대화]
                %s

                [현재 질문]
                %s

                [검색된 출처]
                %s

                위 출처만 근거로 답변해줘.
                """.formatted(contextBlock, question, sourceBlock);
    }

    private String formatContext(int order, ConversationContext context) {
        String answer = context.answer() == null ? "-" : context.answer();
        return """
                %d. 질문: %s
                답변: %s
                """.formatted(order, context.question(), answer);
    }

    private String formatSource(int order, SourceReference source) {
        String pageText = source.pageNumber() == null ? "-" : source.pageNumber().toString();
        return "%d. file=%s, page=%s, snippet=%s".formatted(
                order,
                source.fileName(),
                pageText,
                source.snippet()
        );
    }

    private ChatResult completed(String answer, List<SourceReference> sources) {
        return new ChatResult(ChatHistoryStatus.COMPLETED, new ChatResponse(null, answer, sources));
    }

    private ChatResult irrelevant() {
        return new ChatResult(ChatHistoryStatus.IRRELEVANT, new ChatResponse(null, IRRELEVANT_MESSAGE, List.of()));
    }

    private ChatResult failed(String message) {
        return new ChatResult(ChatHistoryStatus.FAILED, new ChatResponse(null, message, List.of()));
    }

    private record ConversationContext(String question, String answer) {
    }
}
