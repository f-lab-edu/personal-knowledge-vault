package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ThreadListResponse(
        List<ThreadSummary> threads
) {
    public record ThreadSummary(
            String threadId,
            String title,
            int turnCount,
            Instant createdAt
    ) {
    }
}
