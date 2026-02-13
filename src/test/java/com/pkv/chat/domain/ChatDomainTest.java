package com.pkv.chat.domain;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.dto.SourceReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatDomainTest {

    @Test
    @DisplayName("ChatSession은 첫 질문으로 제목을 생성하고 최대 길이를 넘으면 잘라낸다")
    void createSessionTitleFromQuestion() {
        ChatSession session = ChatSession.create(
                1L,
                "session-1",
                "  a".repeat(40),
                ChatPolicy.MAX_TITLE_LENGTH
        );

        assertThat(session.getTitle()).hasSize(ChatPolicy.MAX_TITLE_LENGTH);
        assertThat(session.getTitle()).endsWith("...");
    }

    @Test
    @DisplayName("ChatSession 질문 한도 도달 시 추가 질문을 막는다")
    void assertCanAskThrowsWhenReachedLimit() {
        ChatSession session = ChatSession.builder()
                .memberId(1L)
                .sessionKey("session-1")
                .title("title")
                .build();
        for (int i = 0; i < ChatPolicy.MAX_SESSION_QUESTION_COUNT; i++) {
            session.incrementQuestionCount();
        }

        assertThatThrownBy(() -> session.assertCanAsk(ChatPolicy.MAX_SESSION_QUESTION_COUNT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ChatHistory 상태 전이는 상태와 답변을 함께 갱신한다")
    void markCompletedUpdatesStatusAndAnswer() {
        ChatSession session = ChatSession.builder()
                .memberId(1L)
                .sessionKey("session-1")
                .title("title")
                .build();
        ChatHistory chatHistory = ChatHistory.create(1L, session, " 질문 ", ChatHistoryStatus.FAILED, "실패");

        chatHistory.markCompleted("완료");

        assertThat(chatHistory.getQuestion()).isEqualTo("질문");
        assertThat(chatHistory.getStatus()).isEqualTo(ChatHistoryStatus.COMPLETED);
        assertThat(chatHistory.getAnswer()).isEqualTo("완료");
    }

    @Test
    @DisplayName("ChatHistorySource는 출처 스니펫을 최대 길이로 보정한다")
    void createHistorySourceFromReference() {
        ChatSession session = ChatSession.builder()
                .memberId(1L)
                .sessionKey("session-1")
                .title("title")
                .build();
        ChatHistory chatHistory = ChatHistory.create(1L, session, "질문", ChatHistoryStatus.COMPLETED, "답변");
        String longSnippet = "a".repeat(ChatHistorySource.MAX_SNIPPET_LENGTH + 50);
        SourceReference sourceReference = new SourceReference(10L, "doc.pdf", 3, longSnippet);

        ChatHistorySource historySource = ChatHistorySource.from(chatHistory, sourceReference, 0);

        assertThat(historySource.getChatHistory()).isEqualTo(chatHistory);
        assertThat(historySource.getSnippet()).hasSize(ChatHistorySource.MAX_SNIPPET_LENGTH);
        assertThat(historySource.isSourceDeleted()).isFalse();
    }
}
