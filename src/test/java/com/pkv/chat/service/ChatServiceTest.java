package com.pkv.chat.service;

import com.pkv.chat.domain.ChatResultStatus;
import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.SourceReference;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private ChatService chatService;

    @Test
    @DisplayName("검색 hit와 LLM 성공 시 COMPLETED 상태와 출처를 반환한다")
    void sendMessageCore_completed() {
        // given
        ChatRequest request = new ChatRequest("session-1", QUESTION, List.of());
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        String longSnippet = "a".repeat(240);
        TextSegment segment = createSegment(longSnippet, "Design_Patterns.pdf", 12);
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
        assertThat(result.status()).isEqualTo(ChatResultStatus.COMPLETED);
        assertThat(result.response().content()).isEqualTo("팩토리 패턴은 객체 생성 책임을 분리하는 패턴입니다.");
        assertThat(result.response().sources()).hasSize(1);

        SourceReference source = result.response().sources().getFirst();
        assertThat(source.fileName()).isEqualTo("Design_Patterns.pdf");
        assertThat(source.pageNumber()).isEqualTo(12);
        assertThat(source.snippet()).hasSize(200);
        assertThat(source.snippet()).isEqualTo(longSnippet.substring(0, 200));
    }

    @Test
    @DisplayName("검색 가능한 파일이 없으면 FAILED 상태와 고정 메시지를 반환한다")
    void sendMessageCore_failedWhenNoSearchableSource() {
        // given
        ChatRequest request = new ChatRequest("session-1", QUESTION, List.of());
        given(sourceRepository.existsByMemberIdAndStatus(MEMBER_ID, SourceStatus.COMPLETED)).willReturn(false);

        // when
        ChatResult result = chatService.sendMessageCore(MEMBER_ID, request);

        // then
        assertThat(result.status()).isEqualTo(ChatResultStatus.FAILED);
        assertThat(result.response().content()).isEqualTo("검색 가능한 파일이 없습니다");
        assertThat(result.response().sources()).isEmpty();
    }

    private TextSegment createSegment(String text, String fileName, Integer pageNumber) {
        Metadata metadata = new Metadata().put("fileName", fileName);
        if (pageNumber != null) {
            metadata.put("pageNumber", pageNumber);
        }
        return TextSegment.from(text, metadata);
    }
}
