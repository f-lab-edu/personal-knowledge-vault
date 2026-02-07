package com.pkv.source.dto;

import com.pkv.source.domain.Source;
import com.pkv.source.domain.SourceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SourceResponse(
        @Schema(description = "소스 ID", example = "1") Long id,
        @Schema(description = "파일명", example = "설계서.pdf") String fileName,
        @Schema(description = "파일 크기 (bytes)", example = "1048576") long fileSize,
        @Schema(description = "확장자", example = "pdf") String extension,
        @Schema(description = "상태") SourceStatus status,
        @Schema(description = "생성 시각") Instant createdAt
) {
    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getOriginalFileName(),
                source.getFileSize(),
                source.getFileExtension(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }
}
