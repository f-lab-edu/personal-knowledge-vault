package com.pkv.common.exception;

import lombok.Getter;

@Getter
public class PkvException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object details;

    public PkvException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public PkvException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    public PkvException(ErrorCode errorCode, Object details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public PkvException(ErrorCode errorCode, String customMessage, Object details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }

    public PkvException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public PkvException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
        this.details = null;
    }
}
