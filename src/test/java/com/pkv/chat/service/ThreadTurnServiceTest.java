package com.pkv.chat.service;

import com.pkv.chat.ThreadPolicy;
import com.pkv.chat.domain.ChatThread;
import com.pkv.chat.domain.ThreadTurn;
import com.pkv.chat.dto.HydeResult;
import com.pkv.chat.dto.ThreadTurnCreateRequest;
import com.pkv.chat.dto.ThreadTurnCreateResponse;
import com.pkv.chat.repository.ChatThreadRepository;
import com.pkv.chat.repository.ThreadTurnRepository;
import com.pkv.chat.repository.TurnCitationRepository;
import com.pkv.common.exception.PkvException;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ThreadTurnServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String PROMPT = "팩토리 패턴이 뭐야?";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatThreadRepository chatThreadRepository;

    @Mock
    private ThreadTurnRepository threadTurnRepository;

    @Mock
    private TurnCitationRepository turnCitationRepository;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private HydeQueryTransformer hydeQueryTransformer;

    @InjectMocks
    private ThreadTurnService threadTurnService;

    @Test
    @DisplayName("검색 가능한 문서가 없으면 FAILED 상태/고정 메시지를 반환한다")
    void createTurnReturnsFailedWhenNoSearchableDocument() {
        ThreadTurnCreateRequest request = new ThreadTurnCreateRequest("thread-1", PROMPT);
        ChatThread thread = existingThread(10L, "thread-1");

        given(chatThreadRepository.findByMemberIdAndThreadKey(MEMBER_ID, "thread-1"))
                .willReturn(Optional.of(thread));
        given(threadTurnRepository.findByThread_IdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(documentRepository.existsByMemberIdAndStatus(MEMBER_ID, DocumentStatus.COMPLETED))
                .willReturn(false);
        given(threadTurnRepository.save(any(ThreadTurn.class))).willAnswer(invocation -> {
            ThreadTurn saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        ThreadTurnCreateResponse response = threadTurnService.createTurn(MEMBER_ID, request);

        assertThat(response.threadId()).isEqualTo("thread-1");
        assertThat(response.turnId()).isEqualTo(99L);
        assertThat(response.answer()).isEqualTo(ThreadTurnService.NO_SEARCHABLE_DOCUMENT_MESSAGE);
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 threadId면 THREAD_NOT_FOUND 예외가 발생한다")
    void createTurnThrowsWhenThreadNotFound() {
        ThreadTurnCreateRequest request = new ThreadTurnCreateRequest("missing-thread", PROMPT);
        given(chatThreadRepository.findByMemberIdAndThreadKey(MEMBER_ID, "missing-thread"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> threadTurnService.createTurn(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("유효하지 않은 스레드입니다.");
    }

    @Test
    @DisplayName("스레드 턴 수가 한도면 THREAD_LIMIT_EXCEEDED 예외가 발생한다")
    void createTurnThrowsWhenThreadTurnLimitReached() {
        ThreadTurnCreateRequest request = new ThreadTurnCreateRequest("thread-1", PROMPT);
        ChatThread thread = ChatThread.builder()
                .memberId(MEMBER_ID)
                .threadKey("thread-1")
                .title("title")
                .build();
        ReflectionTestUtils.setField(thread, "turnCount", ThreadPolicy.MAX_THREAD_TURN_COUNT);

        given(chatThreadRepository.findByMemberIdAndThreadKey(MEMBER_ID, "thread-1"))
                .willReturn(Optional.of(thread));

        assertThatThrownBy(() -> threadTurnService.createTurn(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("현재 스레드 턴 한도(5회)에 도달했습니다. 새 대화를 시작해주세요");
    }

    @Test
    @DisplayName("HyDE 한/영 검색 결과에서 동일 sourceChunkRef를 가진 citation은 중복 제거된다")
    void createTurnDeduplicatesCitationsBySourceChunkRef() {
        ThreadTurnCreateRequest request = new ThreadTurnCreateRequest("thread-1", PROMPT);
        ChatThread thread = existingThread(10L, "thread-1");

        given(chatThreadRepository.findByMemberIdAndThreadKey(MEMBER_ID, "thread-1"))
                .willReturn(Optional.of(thread));
        given(threadTurnRepository.findByThread_IdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(documentRepository.existsByMemberIdAndStatus(MEMBER_ID, DocumentStatus.COMPLETED))
                .willReturn(true);

        // HyDE가 한/영 두 개의 가상 문서를 반환
        given(hydeQueryTransformer.transform(PROMPT))
                .willReturn(new HydeResult("팩토리 패턴은 객체 생성을 위임하는 패턴", "Factory pattern delegates object creation"));

        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        given(embeddingModel.embed(any(String.class)))
                .willReturn(new Response<>(fakeEmbedding));

        // 두 검색 모두 같은 sourceChunkRef("chunk-1")를 가진 결과를 반환 → 중복
        TextSegment duplicateSegment = TextSegment.from(
                "팩토리 패턴 설명 텍스트",
                dev.langchain4j.data.document.Metadata.from(Map.of(
                        "fileName", "design-patterns.pdf",
                        "pageNumber", 5,
                        "memberId", MEMBER_ID,
                        "documentId", 100L,
                        "sourceChunkRef", "chunk-1"
                ))
        );
        TextSegment uniqueSegment = TextSegment.from(
                "다른 청크 텍스트",
                dev.langchain4j.data.document.Metadata.from(Map.of(
                        "fileName", "design-patterns.pdf",
                        "pageNumber", 8,
                        "memberId", MEMBER_ID,
                        "documentId", 100L,
                        "sourceChunkRef", "chunk-2"
                ))
        );

        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(List.of(
                new EmbeddingMatch<>(0.95, "id-1", fakeEmbedding, duplicateSegment),
                new EmbeddingMatch<>(0.90, "id-2", fakeEmbedding, uniqueSegment)
        ));
        given(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .willReturn(searchResult);

        // LLM 응답
        given(promptTemplateService.systemPrompt()).willReturn("system prompt");
        given(promptTemplateService.renderUserPrompt(any(), any(), any())).willReturn("rendered prompt");
        given(chatModel.chat(anyList()))
                .willReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("팩토리 패턴은 객체 생성을 캡슐화합니다."))
                        .build());

        given(threadTurnRepository.save(any(ThreadTurn.class))).willAnswer(invocation -> {
            ThreadTurn saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        ThreadTurnCreateResponse response = threadTurnService.createTurn(MEMBER_ID, request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        // 한/영 검색 각각 2개(chunk-1, chunk-2) → 총 4개 중 chunk-1 중복 제거 → 3개
        // 하지만 chunk-2도 중복이므로 → 최종 2개
        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations())
                .extracting("snippet")
                .containsExactly("팩토리 패턴 설명 텍스트", "다른 청크 텍스트");
    }

    private ChatThread existingThread(Long id, String threadKey) {
        ChatThread thread = ChatThread.builder()
                .memberId(MEMBER_ID)
                .threadKey(threadKey)
                .title("title")
                .build();
        ReflectionTestUtils.setField(thread, "id", id);
        return thread;
    }
}
