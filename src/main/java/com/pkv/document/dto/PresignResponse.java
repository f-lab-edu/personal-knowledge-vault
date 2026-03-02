package com.pkv.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PresignResponse(
        @Schema(description = "문서 ID", example = "1") Long documentId,
        @Schema(description = "Presigned PUT URL") String presignedUrl,
        @Schema(description = "URL 만료 시각") Instant expiresAt
) {}
