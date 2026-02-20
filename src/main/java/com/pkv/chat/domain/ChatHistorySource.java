package com.pkv.chat.domain;

import com.pkv.chat.dto.ChatSourceResponse;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "chat_history_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatHistorySource {

    public static final int MAX_SNIPPET_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ChatHistory chatHistory;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_file_name", nullable = false, length = 100)
    private String sourceFileName;

    @Column(name = "source_page_number")
    private Integer sourcePageNumber;

    // 실제 청크 추적용 참조값(`<sourceId>:<chunkIndex>`). source_id가 NULL이면 원본 소스는 삭제된 상태이며, 이 값은 이력 추적용으로만 유지된다.
    @Column(name = "source_chunk_ref", length = 64)
    private String sourceChunkRef;

    @Column(name = "snippet", nullable = false, length = MAX_SNIPPET_LENGTH)
    private String snippet;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static ChatHistorySource create(ChatHistory chatHistory, ChatSourceResponse sourceResponse, int displayOrder) {
        return create(chatHistory, sourceResponse, null, displayOrder);
    }

    public static ChatHistorySource create(
            ChatHistory chatHistory,
            ChatSourceResponse sourceResponse,
            String sourceChunkRef,
            int displayOrder
    ) {
        Objects.requireNonNull(sourceResponse, "sourceResponse is required");

        return new ChatHistorySource(
                chatHistory,
                sourceResponse.sourceId(),
                sourceResponse.fileName(),
                sourceResponse.pageNumber(),
                sourceChunkRef,
                sourceResponse.snippet(),
                displayOrder
        );
    }

    @Builder
    public ChatHistorySource(
            ChatHistory chatHistory,
            Long sourceId,
            String sourceFileName,
            Integer sourcePageNumber,
            String sourceChunkRef,
            String snippet,
            int displayOrder
    ) {
        this.chatHistory = Objects.requireNonNull(chatHistory, "chatHistory is required");
        this.sourceId = sourceId;
        this.sourceFileName = Objects.requireNonNull(sourceFileName, "sourceFileName is required");
        this.sourcePageNumber = sourcePageNumber;
        this.sourceChunkRef = sourceChunkRef;
        this.snippet = normalizeSnippet(snippet);
        this.displayOrder = displayOrder;
    }

    private String normalizeSnippet(String snippet) {
        String value = snippet == null ? "" : snippet;
        if (value.length() <= MAX_SNIPPET_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SNIPPET_LENGTH);
    }
}
