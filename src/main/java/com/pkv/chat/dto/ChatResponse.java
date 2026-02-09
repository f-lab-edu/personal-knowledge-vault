package com.pkv.chat.dto;

import java.util.List;

public record ChatResponse(
        String content,
        List<SourceReference> sources
) {
}
