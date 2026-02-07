package com.pkv.source.controller;

import com.pkv.common.dto.ApiResponse;
import com.pkv.source.dto.PresignRequest;
import com.pkv.source.dto.PresignResponse;
import com.pkv.source.dto.SourceResponse;
import com.pkv.source.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Source", description = "소스 관련 API")
@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;

    @Operation(summary = "소스 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SourceResponse>>> getSources(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.getSources(memberId)));
    }

    @Operation(summary = "Presigned URL 발급")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PresignRequest request) {
        PresignResponse response = sourceService.requestPresignedUrl(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "업로드 확인")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SourceResponse>> confirm(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        SourceResponse response = sourceService.confirmUpload(memberId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
