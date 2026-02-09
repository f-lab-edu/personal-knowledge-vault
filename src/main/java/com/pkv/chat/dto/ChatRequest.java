package com.pkv.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        String sessionId,
        @NotBlank @Size(min = 2, max = 1000) String content,
        List<ConversationTurn> conversationHistory
) {
}
