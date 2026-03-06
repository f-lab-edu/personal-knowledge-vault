package com.pkv.chat.dto;

import java.util.List;

public record ThreadTurnCreateResponse(
        String threadId,
        Long turnId,
        String answer,
        String status,
        List<CitationResponse> citations
) {
}
