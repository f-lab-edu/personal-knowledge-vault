package com.pkv.chat.controller;

import com.pkv.chat.dto.ThreadTurnCreateRequest;
import com.pkv.chat.dto.ThreadTurnCreateResponse;
import com.pkv.chat.service.ThreadTurnService;
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

@Tag(name = "Thread", description = "스레드/턴 API")
@RestController
@RequestMapping("/api/threads")
@RequiredArgsConstructor
@Profile("api")
public class ThreadTurnController {

    private final ThreadTurnService threadTurnService;

    @Operation(summary = "턴 생성")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/turns")
    public ResponseEntity<ApiResponse<ThreadTurnCreateResponse>> createTurn(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ThreadTurnCreateRequest request) {
        ThreadTurnCreateResponse response = threadTurnService.createTurn(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
