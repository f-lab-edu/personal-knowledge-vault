package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatHistoryListResponse(
        List<Item> histories
) {
    public record Item(
            long chatHistoryId,
            String question,
            String status,
            Instant createdAt
    ) {
    }
}
