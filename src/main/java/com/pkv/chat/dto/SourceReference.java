package com.pkv.chat.dto;

public record SourceReference(
        Long sourceId,
        String fileName,
        int pageNumber,
        String snippet
) {
}
