package com.pkv.document.dto;

public record EmbeddingJobMessage(
        Long documentId,
        Long memberId,
        String storagePath,
        String originalFileName,
        String fileExtension
) {
}
