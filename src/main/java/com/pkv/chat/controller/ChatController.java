package com.pkv.chat.controller;

import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.ChatResponse;
import com.pkv.chat.service.ChatService;
import com.pkv.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
@Profile("api")
public class ChatController {

    private final ChatService chatService;

    /**
     * 사용자의 질문을 기반으로 답변을 반환한다.
     * sessionId가 비어 있으면 서버가 새 세션을 만들고, 생성된 sessionId를 함께 응답.
     * 기존 sessionId가 있으면 같은 세션의 이전 대화 중 최대 ChatPolicy.MAX_CONTEXT_HISTORY개를 컨텍스트로 함께 질문에 반영.
     */
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
