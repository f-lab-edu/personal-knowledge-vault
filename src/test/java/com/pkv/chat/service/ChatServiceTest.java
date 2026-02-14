package com.pkv.chat.service;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.ChatResponse;
import com.pkv.chat.repository.ChatHistoryRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.chat.repository.ChatSessionRepository;
import com.pkv.common.exception.PkvException;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.repository.SourceRepository;
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
class ChatServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String QUESTION = "팩토리 패턴이 뭐야?";

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @Mock
    private ChatHistorySourceRepository chatHistorySourceRepository;

    @InjectMocks
    private ChatService chatService;

    @Test
    @DisplayName("검색 가능한 파일이 없으면 FAILED 상태와 고정 메시지를 반환한다")
    void sendMessage_returnsFailedWhenNoSearchableSource() {
        ChatRequest request = new ChatRequest("session-1", QUESTION);
        ChatSession session = existingSession(10L, "session-1");

        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "session-1"))
                .willReturn(Optional.of(session));
        given(chatHistoryRepository.findBySession_IdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED))
                .willReturn(false);
        given(chatHistoryRepository.save(any(ChatHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

        ChatResponse response = chatService.sendMessage(MEMBER_ID, request);

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.content()).isEqualTo(ChatService.NO_SEARCHABLE_SOURCE_MESSAGE);
        assertThat(response.sources()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 sessionId면 CHAT_SESSION_NOT_FOUND 예외가 발생한다")
    void sendMessage_throwsWhenSessionNotFound() {
        ChatRequest request = new ChatRequest("missing-session", QUESTION);
        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "missing-session"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("유효하지 않은 세션입니다.");
    }

    @Test
    @DisplayName("세션 질문 수가 한도면 CHAT_SESSION_LIMIT_EXCEEDED 예외가 발생한다")
    void sendMessage_throwsWhenSessionQuestionLimitReached() {
        ChatRequest request = new ChatRequest("session-1", QUESTION);
        ChatSession session = ChatSession.builder()
                .memberId(MEMBER_ID)
                .sessionKey("session-1")
                .title("title")
                .build();
        ReflectionTestUtils.setField(session, "questionCount", ChatPolicy.MAX_SESSION_QUESTION_COUNT);

        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "session-1"))
                .willReturn(Optional.of(session));

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("현재 세션 질문 한도(%d회)에 도달했습니다. 새 대화를 시작해주세요"
                        .formatted(ChatPolicy.MAX_SESSION_QUESTION_COUNT));
    }

    private ChatSession existingSession(Long id, String sessionKey) {
        ChatSession session = ChatSession.builder()
                .memberId(MEMBER_ID)
                .sessionKey(sessionKey)
                .title("title")
                .build();
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }
}
