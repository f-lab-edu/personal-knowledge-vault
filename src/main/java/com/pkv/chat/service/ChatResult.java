package com.pkv.chat.service;

import com.pkv.chat.domain.ChatHistoryStatus;
import com.pkv.chat.dto.ChatResponse;

record ChatResult(
        ChatHistoryStatus status,
        ChatResponse response
) {
}
