package com.pkv.chat.controller;

import com.pkv.chat.dto.ChatHistoryDetailResponse;
import com.pkv.chat.dto.ChatHistoryListResponse;
import com.pkv.chat.dto.ChatSessionListResponse;
import com.pkv.chat.service.ChatHistoryService;
import com.pkv.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History", description = "Q&A 히스토리 API")
@RestController
@RequestMapping("/api/chat-histories")
@RequiredArgsConstructor
@Profile("api")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    /**
     * 현재 회원의 최근 채팅 세션 리스트를 반환한다.
     * 최신순, 페이징 미적용
     */
    @Operation(summary = "세션 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionListResponse>> getSessionList(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getSessionList(memberId)));
    }

    /**
     * 지정한 세션의 질문 리스트를 반환한다.
     * 최신순, 페이징 미적용
     */
    @Operation(summary = "세션 내 질문 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ChatHistoryListResponse>> getSessionDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getSessionDetail(memberId, sessionId)));
    }

    /**
     * 지정한 질문 내역 1건의 질문/답변/출처를 반환한다.
     */
    @Operation(summary = "질문 히스토리 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/{chatHistoryId}")
    public ResponseEntity<ApiResponse<ChatHistoryDetailResponse>> getHistoryDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatHistoryId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getHistoryDetail(memberId, chatHistoryId)));
    }
}
