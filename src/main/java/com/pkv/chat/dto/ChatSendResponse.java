package com.pkv.chat.dto;

import java.util.List;

public record ChatSendResponse(
        String sessionId,
        String content,
        List<ChatSourceResponse> sources
) {
}
