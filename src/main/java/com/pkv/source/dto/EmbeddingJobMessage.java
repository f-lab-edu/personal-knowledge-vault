package com.pkv.source.dto;

public record EmbeddingJobMessage(
        Long sourceId,
        Long memberId,
        String storagePath,
        String originalFileName,
        String fileExtension
) {
}
