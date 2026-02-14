package com.pkv.chat.controller;

import com.pkv.chat.dto.HistoryDetailResponse;
import com.pkv.chat.dto.HistoryItemSummaryResponse;
import com.pkv.chat.dto.SessionSummaryResponse;
import com.pkv.chat.service.ChatHistoryService;
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

import java.util.List;

@Tag(name = "History", description = "Q&A 히스토리 API")
@RestController
@RequestMapping("/api/chat-histories")
@RequiredArgsConstructor
@Profile("api")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    /**
     * 프론트엔드가 히스토리 패널의 세션 목록을 불러올 때 호출한다.
     * 현재 회원의 세션만 조회하며, 생성일(createdAt) 기준 최신순으로 반환한다.
     */
    @Operation(summary = "세션 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> getSessionList(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getSessionList(memberId)));
    }

    /**
     * 프론트엔드가 선택한 세션의 질문 목록을 불러올 때 호출한다.
     * 회원 범위와 sessionId를 함께 조건으로 조회하고,
     * 최신순 최대 한도(ChatPolicy.MAX_SESSION_DETAIL_ITEMS)를 반환한다.
     * 일치하는 데이터가 없으면 빈 목록을 반환한다.
     */
    @Operation(summary = "세션 내 질문 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<HistoryItemSummaryResponse>>> getSessionDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getSessionDetail(memberId, sessionId)));
    }

    /**
     * 프론트엔드가 히스토리 단건의 질문/답변/출처 상세를 열 때 호출한다.
     * 본인 데이터만 조회 가능하며, chatHistoryId가 없거나 타인 데이터면 H001을 반환한다.
     * 출처는 저장된 displayOrder 순서로 조립해 답변 시점 스냅샷을 그대로 반환한다.
     */
    @Operation(summary = "히스토리 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/{chatHistoryId}")
    public ResponseEntity<ApiResponse<HistoryDetailResponse>> getHistoryDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatHistoryId) {
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getHistoryDetail(memberId, chatHistoryId)));
    }

    /**
     * 프론트엔드가 히스토리 1건 삭제 시 호출한다.
     * 소유권을 먼저 확인한 뒤 연관 출처를 삭제하고, 마지막에 히스토리 본문을 삭제한다.
     * chatHistoryId가 없거나 타인 데이터면 H001을 반환한다.
     */
    @Operation(summary = "히스토리 개별 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/{chatHistoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteHistory(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatHistoryId) {
        chatHistoryService.deleteHistory(memberId, chatHistoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 프론트엔드가 세션 전체 삭제 시 호출한다.
     * 본인 세션이 존재하면 하위 히스토리/출처까지 함께 정리하고 세션을 삭제한다.
     * 세션이 없거나 본인 세션이 아니면 에러 없이 종료한다.
     */
    @Operation(summary = "세션 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId) {
        chatHistoryService.deleteSession(memberId, sessionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
