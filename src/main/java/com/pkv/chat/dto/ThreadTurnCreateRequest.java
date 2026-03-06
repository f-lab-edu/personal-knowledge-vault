package com.pkv.chat.dto;

import com.pkv.chat.ThreadPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreadTurnCreateRequest(
        String threadId,
        @NotBlank @Size(min = ThreadPolicy.MIN_PROMPT_LENGTH, max = ThreadPolicy.MAX_PROMPT_LENGTH) String prompt
) {
}
