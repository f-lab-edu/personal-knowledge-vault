package com.pkv.chat.dto;

public record CitationResponse(
        Long documentId,
        String fileName,
        int pageNumber,
        String snippet
) {
}
