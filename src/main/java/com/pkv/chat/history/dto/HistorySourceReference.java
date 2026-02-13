package com.pkv.chat.history.dto;

public record HistorySourceReference(
        Long sourceId,
        String fileName,
        Integer pageNumber,
        String snippet
) {
}
