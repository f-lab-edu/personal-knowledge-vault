package com.pkv.chat.dto;

public record SourceReference(
        Long sourceId,
        String fileName,
        Integer pageNumber,
        String snippet
) {
}
