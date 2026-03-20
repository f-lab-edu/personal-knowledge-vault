package com.pkv.chat.domain;

import com.pkv.chat.dto.CitationResponse;
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
@Table(name = "turn_citations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TurnCitation {

    public static final int MAX_SNIPPET_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ThreadTurn threadTurn;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "document_file_name", nullable = false, length = 100)
    private String documentFileName;

    @Column(name = "document_page_number")
    private Integer documentPageNumber;

    // 실제 청크 추적용 참조값(`<sourceId>:<chunkIndex>`). source_id가 NULL이면 원본 소스는 삭제된 상태이며, 이 값은 이력 추적용으로만 유지된다.
    @Column(name = "source_chunk_ref", length = 64)
    private String sourceChunkRef;

    @Column(name = "snippet", nullable = false, length = MAX_SNIPPET_LENGTH)
    private String snippet;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static TurnCitation from(ThreadTurn threadTurn, CitationResponse citation, int displayOrder) {
        return from(threadTurn, citation, null, displayOrder);
    }

    public static TurnCitation from(
            ThreadTurn threadTurn,
            CitationResponse citation,
            String sourceChunkRef,
            int displayOrder
    ) {
        Objects.requireNonNull(citation, "citation is required");

        return new TurnCitation(
                threadTurn,
                citation.documentId(),
                citation.fileName(),
                citation.pageNumber(),
                sourceChunkRef,
                citation.snippet(),
                displayOrder
        );
    }

    @Builder
    public TurnCitation(
            ThreadTurn threadTurn,
            Long documentId,
            String documentFileName,
            Integer documentPageNumber,
            String sourceChunkRef,
            String snippet,
            int displayOrder
    ) {
        this.threadTurn = Objects.requireNonNull(threadTurn, "threadTurn is required");
        this.documentId = documentId;
        this.documentFileName = Objects.requireNonNull(documentFileName, "documentFileName is required");
        this.documentPageNumber = documentPageNumber;
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
