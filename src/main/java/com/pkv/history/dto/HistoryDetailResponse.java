package com.pkv.history.dto;

import com.pkv.chat.dto.SourceReference;

import java.time.Instant;
import java.util.List;

public record HistoryDetailResponse(
        String question,
        String answer,
        List<SourceReference> sources,
        String status,
        Instant createdAt
) {
}
