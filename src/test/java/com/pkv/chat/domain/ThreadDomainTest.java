package com.pkv.chat.domain;

import com.pkv.chat.ThreadPolicy;
import com.pkv.chat.dto.CitationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadDomainTest {

    @Test
    @DisplayName("ChatThread는 첫 프롬프트로 제목을 만들고 길이를 제한한다")
    void createThreadTitleFromPrompt() {
        ChatThread thread = ChatThread.create(
                1L,
                "thread-1",
                "a".repeat(80),
                ThreadPolicy.MAX_THREAD_TITLE_LENGTH
        );

        assertThat(thread.getTitle()).hasSize(ThreadPolicy.MAX_THREAD_TITLE_LENGTH);
        assertThat(thread.getTitle()).endsWith("...");
    }

    @Test
    @DisplayName("ChatThread 턴 카운트 증감과 한도 검증")
    void threadTurnCountBehavior() {
        ChatThread thread = ChatThread.builder()
                .memberId(1L)
                .threadKey("thread-1")
                .title("title")
                .build();

        for (int i = 0; i < ThreadPolicy.MAX_THREAD_TURN_COUNT; i++) {
            thread.incrementTurnCount();
        }
        assertThat(thread.isTurnLimitReached(ThreadPolicy.MAX_THREAD_TURN_COUNT)).isTrue();

        thread.decrementTurnCount();
        assertThat(thread.getTurnCount()).isEqualTo(ThreadPolicy.MAX_THREAD_TURN_COUNT - 1);
    }

    @Test
    @DisplayName("TurnCitation은 스니펫 최대 길이를 보정한다")
    void citationSnippetIsTrimmed() {
        ChatThread thread = ChatThread.builder()
                .memberId(1L)
                .threadKey("thread-1")
                .title("title")
                .build();

        ThreadTurn turn = ThreadTurn.create(1L, thread, " 질문 ", ChatResponseStatus.COMPLETED, "답변");
        String longSnippet = "a".repeat(TurnCitation.MAX_SNIPPET_LENGTH + 50);
        CitationResponse citation = new CitationResponse(10L, "doc.pdf", 3, longSnippet);

        TurnCitation turnCitation = TurnCitation.from(turn, citation, 0);

        assertThat(turnCitation.getThreadTurn()).isEqualTo(turn);
        assertThat(turn.getPrompt()).isEqualTo("질문");
        assertThat(turnCitation.getSnippet()).hasSize(TurnCitation.MAX_SNIPPET_LENGTH);
    }
}
