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

    /**
     * 회원이 업로드한 소스 파일 목록을 조회한다.
     * INITIATED(업로드 대기) 상태의 소스는 제외하고, 생성일 역순으로 반환한다.
     */
    @Operation(summary = "소스 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SourceResponse>>> getSources(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.getSources(memberId)));
    }

    /**
     * 프론트엔드에서 파일 업로드 시작 시 호출한다.
     * 파일명·크기·확장자를 검증하고, S3 Presigned PUT URL을 발급한다.
     * 동일 파일명의 INITIATED 상태 소스가 있으면 정리 후 새로 생성한다.
     */
    @Operation(summary = "Presigned URL 발급")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PresignRequest request) {
        PresignResponse response = sourceService.requestPresignedUrl(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 프론트엔드에서 S3 업로드 완료 후 호출한다.
     * S3에 파일이 실제 존재하는지 확인하고, 소스 상태를 UPLOADED로 변경한다.
     * INITIATED 상태가 아니거나 S3에 파일이 없으면 실패한다.
     */
    @Operation(summary = "업로드 확인")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SourceResponse>> confirm(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        SourceResponse response = sourceService.confirmUpload(memberId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 프론트엔드에서 소스 삭제 시 호출한다.
     * COMPLETED 또는 FAILED 상태의 소스만 삭제할 수 있다.
     * S3 파일을 먼저 삭제한 뒤 DB 레코드를 삭제한다.
     */
    @Operation(summary = "소스 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        sourceService.deleteSource(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
