package com.pkv.document.controller;

import com.pkv.common.dto.ApiResponse;
import com.pkv.document.dto.DocumentResponse;
import com.pkv.document.dto.PresignRequest;
import com.pkv.document.dto.PresignResponse;
import com.pkv.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Document", description = "문서 API")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Profile("api")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "문서 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.getDocuments(memberId)));
    }

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
        PresignResponse response = documentService.requestPresignedUrl(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "업로드 확인")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "업로드 확인 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping("/{documentId}/confirm")
    public ResponseEntity<ApiResponse<DocumentResponse>> confirm(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long documentId) {
        DocumentResponse response = documentService.confirmUpload(memberId, documentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "문서 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "삭제 불가 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(memberId, documentId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
