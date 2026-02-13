package com.pkv.chat.history.dto;

import java.time.Instant;

public record HistoryItemSummaryResponse(
        long chatHistoryId,
        String question,
        String status,
        Instant createdAt
) {
}
