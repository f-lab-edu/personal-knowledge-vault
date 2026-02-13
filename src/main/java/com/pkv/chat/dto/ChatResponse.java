package com.pkv.chat.dto;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String content,
        List<SourceReference> sources
) {
}
