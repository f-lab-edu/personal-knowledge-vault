package com.pkv.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private ChatHistory history;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_file_name", nullable = false, length = 100)
    private String sourceFileName;

    @Column(name = "source_page_number")
    private Integer sourcePageNumber;

    @Column(name = "snippet", nullable = false, length = 200)
    private String snippet;

    @Column(name = "source_deleted", nullable = false)
    private boolean sourceDeleted;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder
    public ChatHistorySource(
            ChatHistory history,
            Long sourceId,
            String sourceFileName,
            Integer sourcePageNumber,
            String snippet,
            int displayOrder
    ) {
        this.history = Objects.requireNonNull(history, "history is required");
        this.sourceId = sourceId;
        this.sourceFileName = Objects.requireNonNull(sourceFileName, "sourceFileName is required");
        this.sourcePageNumber = sourcePageNumber;
        this.snippet = Objects.requireNonNull(snippet, "snippet is required");
        this.sourceDeleted = false;
        this.displayOrder = displayOrder;
    }

    public void markDeleted() {
        this.sourceDeleted = true;
    }
}
