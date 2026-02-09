package com.pkv.history.dto;

import java.time.Instant;

public record HistoryItemSummaryResponse(
        long historyId,
        String question,
        String status,
        Instant createdAt
) {
}
