package com.pkv.chat.dto;

import com.pkv.chat.ChatPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
        String sessionId,
        @NotBlank @Size(min = ChatPolicy.MIN_QUESTION_LENGTH, max = ChatPolicy.MAX_QUESTION_LENGTH) String content
) {
}
