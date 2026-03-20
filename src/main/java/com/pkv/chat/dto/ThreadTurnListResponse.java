package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ThreadTurnListResponse(
        List<TurnSummary> turns
) {
    public record TurnSummary(
            long turnId,
            String prompt,
            String status,
            Instant createdAt
    ) {
    }
}
