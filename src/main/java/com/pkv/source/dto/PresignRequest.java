package com.pkv.source.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignRequest(
        @NotBlank @Schema(description = "파일명 (확장자 포함)", example = "운영체제.pdf")
        String fileName,
        @Positive @Schema(description = "파일 크기 (bytes)", example = "1048576")
        long fileSize
) {}
