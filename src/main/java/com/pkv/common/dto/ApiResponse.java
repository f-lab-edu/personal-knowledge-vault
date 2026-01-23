package com.pkv.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pkv.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = LocalDateTime.now();
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
    public static class ErrorResponse {
        private final String code;
        private final String message;
        private final Object details;

        public ErrorResponse(String code, String message, Object details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
    }
}
