package com.pkv.chat.controller;

import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.ChatResponse;
import com.pkv.chat.service.MockChatService;
import com.pkv.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Q&A 채팅 API")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final MockChatService chatService;

    @Operation(summary = "메시지 전송")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.sendMessage(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
