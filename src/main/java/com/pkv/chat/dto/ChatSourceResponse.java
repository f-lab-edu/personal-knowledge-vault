package com.pkv.chat.dto;

public record ChatSourceResponse(
        Long sourceId,
        String fileName,
        int pageNumber,
        String snippet
) {
}
