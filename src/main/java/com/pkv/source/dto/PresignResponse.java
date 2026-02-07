package com.pkv.source.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PresignResponse(
        @Schema(description = "소스 ID", example = "1") Long sourceId,
        @Schema(description = "Presigned PUT URL") String presignedUrl,
        @Schema(description = "URL 만료 시각") Instant expiresAt
) {}
