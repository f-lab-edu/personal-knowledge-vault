package com.pkv.history.controller;

import com.pkv.common.dto.ApiResponse;
import com.pkv.history.dto.HistoryDetailResponse;
import com.pkv.history.dto.HistoryItemSummaryResponse;
import com.pkv.history.dto.SessionSummaryResponse;
import com.pkv.history.service.MockHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "History", description = "Q&A 히스토리 API")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final MockHistoryService historyService;

    @Operation(summary = "세션 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> getSessionList(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getSessionList(memberId)));
    }

    @Operation(summary = "세션 내 질문 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<HistoryItemSummaryResponse>>> getSessionDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getSessionDetail(memberId, sessionId)));
    }

    @Operation(summary = "히스토리 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/{historyId}")
    public ResponseEntity<ApiResponse<HistoryDetailResponse>> getHistoryDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long historyId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getHistoryDetail(memberId, historyId)));
    }

    @Operation(summary = "히스토리 개별 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/{historyId}")
    public ResponseEntity<ApiResponse<Void>> deleteHistory(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long historyId) {
        historyService.deleteHistory(memberId, historyId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "세션 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId) {
        historyService.deleteSession(memberId, sessionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
