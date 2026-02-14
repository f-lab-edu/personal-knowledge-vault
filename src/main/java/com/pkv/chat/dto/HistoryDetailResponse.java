package com.pkv.chat.dto;

import java.time.Instant;
import java.util.List;

public record HistoryDetailResponse(
        String question,
        String answer,
        List<HistorySourceReference> sources,
        String status,
        Instant createdAt
) {
}
