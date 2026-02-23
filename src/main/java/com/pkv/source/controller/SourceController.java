package com.pkv.source.controller;

import com.pkv.common.dto.ApiResponse;
import com.pkv.source.dto.PresignRequest;
import com.pkv.source.dto.PresignResponse;
import com.pkv.source.dto.SourceResponse;
import com.pkv.source.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Source", description = "소스 관련 API")
@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
@Profile("api")
public class SourceController {

    private final SourceService sourceService;

    /**
     * 회원이 업로드한 소스 파일 목록을 조회한다.
     * INITIATED(업로드 대기) 상태의 소스는 제외하고, 생성일 역순으로 반환한다.
     */
    @Operation(summary = "소스 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<SourceResponse>>> getSources(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.getSources(memberId)));
    }

    /**
     * 파일 업로드를 위한 S3 Presigned PUT URL을 발급한다.
     * 파일명·확장자·크기·개수·총 용량·중복 정책을 검증한 뒤 URL을 생성한다.
     * 동일 파일명의 INITIATED 상태 소스가 있으면 정리 후 새로 생성한다.
     */
    @Operation(summary = "Presigned URL 발급")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일명 중복")
    })
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PresignRequest request) {
        PresignResponse response = sourceService.requestPresignedUrl(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 클라이언트의 S3 업로드 완료를 확인하고, 소스 상태를 INITIATED → UPLOADED로 전이한다.
     * S3 HeadObject로 파일 존재를 검증하며, INITIATED가 아니거나 파일이 없으면 실패한다.
     */
    @Operation(summary = "업로드 확인")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "업로드 확인 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "소스를 찾을 수 없음")
    })
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SourceResponse>> confirm(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        SourceResponse response = sourceService.confirmUpload(memberId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 소스를 삭제한다. COMPLETED 또는 FAILED 상태만 삭제 가능하다.
     * S3 오브젝트를 먼저 삭제한 뒤 DB 레코드를 삭제한다.
     */
    @Operation(summary = "소스 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "삭제 불가 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "소스를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        sourceService.deleteSource(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
