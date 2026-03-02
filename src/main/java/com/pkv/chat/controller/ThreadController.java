package com.pkv.chat.controller;

import com.pkv.chat.dto.ThreadListResponse;
import com.pkv.chat.dto.ThreadTurnDetailResponse;
import com.pkv.chat.dto.ThreadTurnListResponse;
import com.pkv.chat.service.ThreadQueryService;
import com.pkv.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Thread", description = "스레드 조회/삭제 API")
@RestController
@RequestMapping("/api/threads")
@RequiredArgsConstructor
@Profile("api")
public class ThreadController {

    private final ThreadQueryService threadQueryService;

    @Operation(summary = "스레드 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<ThreadListResponse>> getThreadList(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(threadQueryService.getThreadList(memberId)));
    }

    @Operation(summary = "스레드 내 턴 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/{threadId}/turns")
    public ResponseEntity<ApiResponse<ThreadTurnListResponse>> getThreadTurns(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String threadId) {
        return ResponseEntity.ok(ApiResponse.success(threadQueryService.getThreadTurns(memberId, threadId)));
    }

    @Operation(summary = "턴 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/{threadId}/turns/{turnId}")
    public ResponseEntity<ApiResponse<ThreadTurnDetailResponse>> getTurnDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String threadId,
            @PathVariable Long turnId) {
        return ResponseEntity.ok(ApiResponse.success(threadQueryService.getTurnDetail(memberId, threadId, turnId)));
    }

    @Operation(summary = "턴 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/{threadId}/turns/{turnId}")
    public ResponseEntity<ApiResponse<Void>> deleteTurn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String threadId,
            @PathVariable Long turnId) {
        threadQueryService.deleteTurn(memberId, threadId, turnId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "스레드 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/{threadId}")
    public ResponseEntity<ApiResponse<Void>> deleteThread(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String threadId) {
        threadQueryService.deleteThread(memberId, threadId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
