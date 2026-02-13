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
import com.pkv.common.exception.PkvException;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.repository.SourceRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    @DisplayName("검색 hit와 LLM 성공 시 COMPLETED 상태와 출처를 반환한다")
    void sendMessageCore_completed() {
        // given
        ChatRequest request = new ChatRequest("session-1", QUESTION);
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        String longSnippet = "a".repeat(240);
        TextSegment segment = createSegment(longSnippet, "Design_Patterns.pdf", 12, 101L);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.93, "vector-1", queryEmbedding, segment);

        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED)).willReturn(true);
        given(embeddingModel.embed(QUESTION)).willReturn(Response.from(queryEmbedding));
        given(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .willReturn(new EmbeddingSearchResult<>(List.of(match)));
        given(chatModel.chat(anyList()))
                .willReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("팩토리 패턴은 객체 생성 책임을 분리하는 패턴입니다."))
                        .build());

        // when
        ChatResult result = chatService.sendMessageCore(MEMBER_ID, request);

        // then
        assertThat(result.status()).isEqualTo(ChatHistoryStatus.COMPLETED);
        assertThat(result.response().sessionId()).isNull();
        assertThat(result.response().content()).isEqualTo("팩토리 패턴은 객체 생성 책임을 분리하는 패턴입니다.");
        assertThat(result.response().sources()).hasSize(1);

        SourceReference source = result.response().sources().getFirst();
        assertThat(source.sourceId()).isEqualTo(101L);
        assertThat(source.fileName()).isEqualTo("Design_Patterns.pdf");
        assertThat(source.pageNumber()).isEqualTo(12);
        assertThat(source.snippet()).hasSize(ChatHistorySource.MAX_SNIPPET_LENGTH);
        assertThat(source.snippet()).isEqualTo(longSnippet.substring(0, ChatHistorySource.MAX_SNIPPET_LENGTH));
    }

    @Test
    @DisplayName("검색 가능한 파일이 없으면 FAILED 상태와 고정 메시지를 반환한다")
    void sendMessageCore_failedWhenNoSearchableSource() {
        // given
        ChatRequest request = new ChatRequest("session-1", QUESTION);
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED)).willReturn(false);

        // when
        ChatResult result = chatService.sendMessageCore(MEMBER_ID, request);

        // then
        assertThat(result.status()).isEqualTo(ChatHistoryStatus.FAILED);
        assertThat(result.response().content()).isEqualTo("검색 가능한 파일이 없습니다");
        assertThat(result.response().sources()).isEmpty();
    }

    @Test
    @DisplayName("sessionId가 없으면 신규 세션을 생성하고 응답에 sessionId를 포함한다")
    void sendMessage_createsSessionWhenSessionIdMissing() {
        // given
        ChatRequest request = new ChatRequest(null, QUESTION);
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        TextSegment segment = createSegment("chunk text", "Design_Patterns.pdf", 12, 101L);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.93, "vector-1", queryEmbedding, segment);

        given(chatSessionRepository.save(any(ChatSession.class))).willAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 10L);
            return session;
        });
        given(chatHistoryRepository.findTop5BySession_IdOrderByCreatedAtDesc(10L)).willReturn(List.of());
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED)).willReturn(true);
        given(embeddingModel.embed(QUESTION)).willReturn(Response.from(queryEmbedding));
        given(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .willReturn(new EmbeddingSearchResult<>(List.of(match)));
        given(chatModel.chat(anyList()))
                .willReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("팩토리 패턴은 객체 생성 책임을 분리하는 패턴입니다."))
                        .build());
        given(chatHistoryRepository.save(any(ChatHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatResponse response = chatService.sendMessage(MEMBER_ID, request);

        // then
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.content()).isEqualTo("팩토리 패턴은 객체 생성 책임을 분리하는 패턴입니다.");
        assertThat(response.sources()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 sessionId면 CHAT_SESSION_NOT_FOUND 예외가 발생한다")
    void sendMessage_throwsWhenSessionNotFound() {
        // given
        ChatRequest request = new ChatRequest("missing-session", QUESTION);
        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "missing-session"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("유효하지 않은 세션입니다.");
    }

    @Test
    @DisplayName("세션 질문 수가 한도면 CHAT_SESSION_LIMIT_EXCEEDED 예외가 발생한다")
    void sendMessage_throwsWhenSessionQuestionLimitReached() {
        // given
        ChatRequest request = new ChatRequest("session-1", QUESTION);
        ChatSession session = ChatSession.builder()
                .memberId(MEMBER_ID)
                .sessionKey("session-1")
                .title("title")
                .build();
        ReflectionTestUtils.setField(session, "questionCount", ChatPolicy.MAX_SESSION_QUESTION_COUNT);

        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "session-1"))
                .willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("현재 세션 질문 한도(%d회)에 도달했습니다. 새 대화를 시작해주세요"
                        .formatted(ChatPolicy.MAX_SESSION_QUESTION_COUNT));
    }

    private TextSegment createSegment(String text, String fileName, Integer pageNumber, Long sourceId) {
        Metadata metadata = new Metadata().put("fileName", fileName);
        if (pageNumber != null) {
            metadata.put("pageNumber", pageNumber);
        }
        if (sourceId != null) {
            metadata.put("sourceId", sourceId);
        }
        return TextSegment.from(text, metadata);
    }
}
