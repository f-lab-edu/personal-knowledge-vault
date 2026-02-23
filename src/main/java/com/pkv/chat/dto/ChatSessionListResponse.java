package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatSessionListResponse(
        List<Item> sessions
) {
    public record Item(
            String sessionId,
            String title,
            int questionCount,
            Instant createdAt
    ) {
    }
}
