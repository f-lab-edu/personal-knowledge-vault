package com.pkv.document.dto;

import com.pkv.document.domain.Document;
import com.pkv.document.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record DocumentResponse(
        @Schema(description = "문서 ID", example = "1") Long id,
        @Schema(description = "파일명", example = "설계서.pdf") String fileName,
        @Schema(description = "파일 크기 (bytes)", example = "1048576") long fileSize,
        @Schema(description = "확장자", example = "pdf") String extension,
        @Schema(description = "상태") DocumentStatus status,
        @Schema(description = "생성 시각") Instant createdAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getFileExtension(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }
}
