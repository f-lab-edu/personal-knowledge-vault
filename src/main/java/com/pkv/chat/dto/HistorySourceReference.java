package com.pkv.chat.dto;

public record HistorySourceReference(
        Long sourceId,
        String fileName,
        int pageNumber,
        String snippet
) {
}
