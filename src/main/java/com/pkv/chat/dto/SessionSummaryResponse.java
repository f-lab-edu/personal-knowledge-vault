package com.pkv.chat.dto;

import java.time.Instant;

public record SessionSummaryResponse(
        String sessionId,
        String title,
        int questionCount,
        Instant createdAt
) {
}
