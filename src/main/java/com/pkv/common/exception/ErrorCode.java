package com.pkv.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "서버 내부 오류가 발생했습니다."),

    // Auth
    OAUTH2_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "A001", "OAuth2 인증에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A003", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A005", "리프레시 토큰이 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A006", "리프레시 토큰이 존재하지 않습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원을 찾을 수 없습니다."),

    // Source
    SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "소스를 찾을 수 없습니다."),
    SOURCE_NAME_INVALID(HttpStatus.BAD_REQUEST, "S002", "파일명은 한글, 영문, 숫자, _, -만 허용되며 최대 30자입니다."),
    SOURCE_EXTENSION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "S003", "지원하지 않는 파일 형식입니다. (pdf, txt, md)"),
    SOURCE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "S004", "파일 크기는 30MB를 초과할 수 없습니다."),
    SOURCE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "S005", "파일은 최대 30개까지 업로드할 수 있습니다."),
    SOURCE_TOTAL_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "S006", "총 저장 용량 300MB를 초과합니다."),
    SOURCE_NAME_DUPLICATED(HttpStatus.CONFLICT, "S007", "동일한 이름의 파일이 이미 존재합니다."),
    SOURCE_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "S008", "현재 상태에서는 삭제할 수 없습니다."),
    SOURCE_UPLOAD_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "S009", "파일 업로드가 확인되지 않았습니다."),

    // Worker — 임베딩 파이프라인(파싱/청킹/임베딩)
    DOCUMENT_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "W001", "문서 파싱에 실패했습니다."); // S3 다운로드 후 텍스트 추출 실패 시

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
