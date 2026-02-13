package com.pkv.chat.service;

import com.pkv.chat.domain.ChatResultStatus;
import com.pkv.chat.dto.ChatResponse;

record ChatResult(
        ChatResultStatus status,
        ChatResponse response
) {
}
