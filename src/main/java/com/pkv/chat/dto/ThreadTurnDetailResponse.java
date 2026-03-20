package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ThreadTurnDetailResponse(
        String prompt,
        String answer,
        List<CitationResponse> citations,
        String status,
        Instant createdAt
) {
}
