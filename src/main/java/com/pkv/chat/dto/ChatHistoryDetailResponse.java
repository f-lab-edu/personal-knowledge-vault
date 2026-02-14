package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatHistoryDetailResponse(
        String question,
        String answer,
        List<ChatSourceResponse> sources,
        String status,
        Instant createdAt
) {
}
