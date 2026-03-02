package com.pkv.chat.service;

import com.pkv.chat.ThreadPolicy;
import com.pkv.chat.domain.ChatResponseStatus;
import com.pkv.chat.domain.ChatThread;
import com.pkv.chat.domain.ThreadTurn;
import com.pkv.chat.domain.TurnCitation;
import com.pkv.chat.dto.CitationResponse;
import com.pkv.chat.dto.ThreadTurnCreateRequest;
import com.pkv.chat.dto.ThreadTurnCreateResponse;
import com.pkv.chat.repository.ChatThreadRepository;
import com.pkv.chat.repository.ThreadTurnRepository;
import com.pkv.chat.repository.TurnCitationRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.repository.DocumentRepository;
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
public class ThreadTurnService {

    static final String NO_SEARCHABLE_DOCUMENT_MESSAGE = "임베딩한 문서가 없습니다";
    static final String IRRELEVANT_MESSAGE = "질문과 관련된 내용을 찾을 수 없습니다";
    static final String FAILED_MESSAGE = "답변 생성에 실패했습니다";

    private static final String UNKNOWN_FILE_NAME = "알 수 없는 파일";
    private static final int DEFAULT_PAGE_NUMBER = 1;

    private static final String SYSTEM_PROMPT = """
            너는 사용자가 업로드한 문서를 기반으로 답변하는 어시스턴트다.
            제공된 근거를 벗어나 추측하지 말고, 질문에 대한 답을 한국어로 간결하게 작성해라.
            """;

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final ChatThreadRepository chatThreadRepository;
    private final ThreadTurnRepository threadTurnRepository;
    private final TurnCitationRepository turnCitationRepository;

    @Transactional
    public ThreadTurnCreateResponse createTurn(Long memberId, ThreadTurnCreateRequest request) {
        ChatThread thread = resolveThread(memberId, request);
        if (thread.isTurnLimitReached(ThreadPolicy.MAX_THREAD_TURN_COUNT)) {
            throw new PkvException(ErrorCode.THREAD_LIMIT_EXCEEDED);
        }

        List<ConversationContext> contexts = loadConversationContexts(thread);
        ChatResult result = createTurnCore(memberId, request.prompt(), contexts);

        ThreadTurn turn = saveThreadTurn(memberId, thread, request.prompt(), result);
        saveTurnCitations(turn, result.citations());
        thread.incrementTurnCount();

        return new ThreadTurnCreateResponse(
                thread.getThreadKey(),
                turn.getId(),
                result.answer(),
                result.status().name(),
                result.citations()
        );
    }

    private ChatResult createTurnCore(Long memberId, String prompt, List<ConversationContext> contexts) {
        if (!documentRepository.existsByMemberIdAndStatus(memberId, DocumentStatus.COMPLETED)) {
            return failed(NO_SEARCHABLE_DOCUMENT_MESSAGE);
        }

        try {
            Embedding queryEmbedding = embeddingModel.embed(prompt).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(ThreadPolicy.MAX_RESULTS)
                    .minScore(ThreadPolicy.MIN_SCORE)
                    .filter(metadataKey("memberId").isEqualTo(memberId))
                    .build();

            List<CitationResponse> citations = embeddingStore.search(searchRequest).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .filter(Objects::nonNull)
                    .map(this::toCitationResponse)
                    .toList();

            if (citations.isEmpty()) {
                return irrelevant();
            }

            String userPrompt = buildUserPrompt(prompt, citations, contexts);
            var modelResponse = chatModel.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(userPrompt)
            ));
            String answer = modelResponse.aiMessage() != null ? modelResponse.aiMessage().text() : null;

            if (answer == null || answer.isBlank()) {
                log.warn("LLM 응답이 비어있습니다. memberId={}", memberId);
                return failed(FAILED_MESSAGE);
            }

            return completed(answer, citations);
        } catch (Exception e) {
            log.error("질문 처리 실패. memberId={}", memberId, e);
            return failed(FAILED_MESSAGE);
        }
    }

    private ChatThread resolveThread(Long memberId, ThreadTurnCreateRequest request) {
        if (!StringUtils.hasText(request.threadId())) {
            return createThread(memberId, request.prompt());
        }

        return chatThreadRepository.findByMemberIdAndThreadKey(memberId, request.threadId())
                .orElseThrow(() -> new PkvException(ErrorCode.THREAD_NOT_FOUND));
    }

    private ChatThread createThread(Long memberId, String firstPrompt) {
        ChatThread thread = ChatThread.create(
                memberId,
                UUID.randomUUID().toString(),
                firstPrompt,
                ThreadPolicy.MAX_THREAD_TITLE_LENGTH
        );
        return chatThreadRepository.save(thread);
    }

    private List<ConversationContext> loadConversationContexts(ChatThread thread) {
        PageRequest contextLimit = PageRequest.of(0, ThreadPolicy.MAX_CONTEXT_TURNS);
        return threadTurnRepository.findByThread_IdOrderByCreatedAtDesc(thread.getId(), contextLimit).stream()
                .sorted(Comparator.comparing(ThreadTurn::getCreatedAt))
                .map(turn -> new ConversationContext(turn.getPrompt(), turn.getAnswer()))
                .toList();
    }

    private ThreadTurn saveThreadTurn(Long memberId, ChatThread thread, String prompt, ChatResult result) {
        ThreadTurn turn = ThreadTurn.create(
                memberId,
                thread,
                prompt,
                result.status(),
                result.answer()
        );
        return threadTurnRepository.save(turn);
    }

    private void saveTurnCitations(ThreadTurn turn, List<CitationResponse> citations) {
        if (citations.isEmpty()) {
            return;
        }

        List<TurnCitation> entities = IntStream.range(0, citations.size())
                .mapToObj(index -> TurnCitation.from(turn, citations.get(index), index))
                .toList();

        turnCitationRepository.saveAll(entities);
    }

    private CitationResponse toCitationResponse(TextSegment segment) {
        String fileName = segment.metadata() != null ? segment.metadata().getString("fileName") : null;
        Integer pageNumber = segment.metadata() != null ? segment.metadata().getInteger("pageNumber") : null;
        String snippet = truncateSnippet(segment.text());
        Long documentId = extractDocumentId(segment);

        return new CitationResponse(
                documentId,
                fileName == null || fileName.isBlank() ? UNKNOWN_FILE_NAME : fileName,
                pageNumber == null || pageNumber <= 0 ? DEFAULT_PAGE_NUMBER : pageNumber,
                snippet
        );
    }

    private Long extractDocumentId(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }

        try {
            return segment.metadata().getLong("documentId");
        } catch (Exception e) {
            log.warn("documentId 파싱 실패", e);
            return null;
        }
    }

    private String truncateSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= TurnCitation.MAX_SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, TurnCitation.MAX_SNIPPET_LENGTH);
    }

    private String buildUserPrompt(String prompt, List<CitationResponse> citations, List<ConversationContext> contexts) {
        String citationBlock = IntStream.range(0, citations.size())
                .mapToObj(index -> formatCitation(index + 1, citations.get(index)))
                .collect(Collectors.joining("\n"));

        if (contexts.isEmpty()) {
            return """
                [질문]
                %s

                [검색된 근거]
                %s

                위 근거만 사용해 답변해줘.
                """.formatted(prompt, citationBlock);
        }

        String contextBlock = IntStream.range(0, contexts.size())
                .mapToObj(index -> formatContext(index + 1, contexts.get(index)))
                .collect(Collectors.joining("\n\n"));

        return """
                [이전 대화]
                %s

                [현재 질문]
                %s

                [검색된 근거]
                %s

                위 근거만 사용해 답변해줘.
                """.formatted(contextBlock, prompt, citationBlock);
    }

    private String formatContext(int order, ConversationContext context) {
        String answer = context.answer() == null ? "-" : context.answer();
        return """
                %d. 질문: %s
                답변: %s
                """.formatted(order, context.prompt(), answer);
    }

    private String formatCitation(int order, CitationResponse citation) {
        return "%d. file=%s, page=%s, snippet=%s".formatted(
                order,
                citation.fileName(),
                citation.pageNumber(),
                citation.snippet()
        );
    }

    private ChatResult completed(String answer, List<CitationResponse> citations) {
        return new ChatResult(ChatResponseStatus.COMPLETED, answer, citations);
    }

    private ChatResult irrelevant() {
        return new ChatResult(ChatResponseStatus.IRRELEVANT, IRRELEVANT_MESSAGE, List.of());
    }

    private ChatResult failed(String message) {
        return new ChatResult(ChatResponseStatus.FAILED, message, List.of());
    }

    private record ChatResult(ChatResponseStatus status, String answer, List<CitationResponse> citations) {
    }

    private record ConversationContext(String prompt, String answer) {
    }
}
