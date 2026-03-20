package com.pkv.chat.service;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.ChatSendRequest;
import com.pkv.chat.dto.ChatSendResponse;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
    private PromptTemplateService promptTemplateService;

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
        ChatSendRequest request = new ChatSendRequest("session-1", QUESTION);
        ChatSession session = existingSession(10L, "session-1");

        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "session-1"))
                .willReturn(Optional.of(session));
        given(chatHistoryRepository.findBySession_IdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED))
                .willReturn(false);
        given(chatHistoryRepository.save(any(ChatHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

        ChatSendResponse response = chatService.sendMessage(MEMBER_ID, request);

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.content()).isEqualTo(ChatService.NO_SEARCHABLE_SOURCE_MESSAGE);
        assertThat(response.sources()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 sessionId면 CHAT_SESSION_NOT_FOUND 예외가 발생한다")
    void sendMessage_throwsWhenSessionNotFound() {
        ChatSendRequest request = new ChatSendRequest("missing-session", QUESTION);
        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "missing-session"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, request))
                .isInstanceOf(PkvException.class)
                .hasMessage("유효하지 않은 세션입니다.");
    }

    @Test
    @DisplayName("세션 질문 수가 한도면 CHAT_SESSION_LIMIT_EXCEEDED 예외가 발생한다")
    void sendMessage_throwsWhenSessionQuestionLimitReached() {
        ChatSendRequest request = new ChatSendRequest("session-1", QUESTION);
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

    @Test
    @DisplayName("정상 경로에서 프롬프트 서비스를 통해 모델 호출 후 답변을 반환한다")
    void sendMessage_callsPromptTemplateServiceAndChatModelOnSuccess() {
        ChatSendRequest request = new ChatSendRequest("session-1", QUESTION);
        ChatSession session = existingSession(10L, "session-1");

        Metadata metadata = new Metadata()
                .put("fileName", "design.md")
                .put("pageNumber", 3)
                .put("sourceId", 99L)
                .put("sourceChunkRef", "99:0");
        TextSegment segment = TextSegment.from("팩토리 패턴은 객체 생성을 캡슐화한다.", metadata);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(List.of(
                new EmbeddingMatch<>(0.95, "embedding-1", Embedding.from(new float[]{0.2f}), segment)
        ));

        given(chatSessionRepository.findByMemberIdAndSessionKeyForUpdate(MEMBER_ID, "session-1"))
                .willReturn(Optional.of(session));
        given(chatHistoryRepository.findBySession_IdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED))
                .willReturn(true);
        given(embeddingModel.embed(QUESTION))
                .willReturn(Response.from(Embedding.from(new float[]{0.1f})));
        given(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .willReturn(searchResult);
        given(promptTemplateService.systemPrompt())
                .willReturn("SYSTEM_PROMPT");
        given(promptTemplateService.renderUserPrompt(anyString(), anyString(), anyString()))
                .willReturn("USER_PROMPT");
        given(chatModel.chat(anyList()))
                .willReturn(ChatResponse.builder().aiMessage(AiMessage.from("정상 응답")).build());
        given(chatHistoryRepository.save(any(ChatHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

        ChatSendResponse response = chatService.sendMessage(MEMBER_ID, request);

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.content()).isEqualTo("정상 응답");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().fileName()).isEqualTo("design.md");
        assertThat(response.sources().getFirst().pageNumber()).isEqualTo(3);

        ArgumentCaptor<String> sourceBlockCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptTemplateService).systemPrompt();
        verify(promptTemplateService).renderUserPrompt(eq(QUESTION), sourceBlockCaptor.capture(), eq(""));
        verify(chatModel).chat(anyList());
        assertThat(sourceBlockCaptor.getValue()).contains("file=design.md");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatHistorySource>> historySourcesCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatHistorySourceRepository).saveAll(historySourcesCaptor.capture());
        assertThat(historySourcesCaptor.getValue()).singleElement().satisfies(source ->
                assertThat(source.getSourceChunkRef()).isEqualTo("99:0"));
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
