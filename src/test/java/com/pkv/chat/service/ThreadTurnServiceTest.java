package com.pkv.chat.service;

import com.pkv.chat.ThreadPolicy;
import com.pkv.chat.domain.ChatThread;
import com.pkv.chat.domain.ThreadTurn;
import com.pkv.chat.dto.ThreadTurnCreateRequest;
import com.pkv.chat.dto.ThreadTurnCreateResponse;
import com.pkv.chat.repository.ChatThreadRepository;
import com.pkv.chat.repository.ThreadTurnRepository;
import com.pkv.chat.repository.TurnCitationRepository;
import com.pkv.common.exception.PkvException;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.repository.DocumentRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
