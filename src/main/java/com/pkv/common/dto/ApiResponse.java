package com.pkv.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pkv.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 API 응답")
public class ApiResponse<T> {

    @Schema(description = "성공 여부", example = "true")
    private final boolean success;
    @Schema(description = "응답 데이터")
    private final T data;
    @Schema(description = "에러 정보")
    private final ErrorResponse error;
    @Schema(description = "응답 시각", example = "2025-01-01T00:00:00Z")
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = Instant.now();
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null));
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, Object details) {
        return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), details));
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), customMessage, null));
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String customMessage, Object details) {
        return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), customMessage, details));
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "에러 응답 상세")
    public static class ErrorResponse {
        @Schema(description = "에러 코드", example = "AUTH_001")
        private final String code;
        @Schema(description = "에러 메시지", example = "인증이 필요합니다")
        private final String message;
        @Schema(description = "추가 상세 정보")
        private final Object details;

        public ErrorResponse(String code, String message, Object details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
    }
}
