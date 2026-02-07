package com.pkv.source.domain;

public enum SourceStatus {
    /** 파일 메타데이터 생성됨. 업로드 대기. */
    INITIATED,
    /** 파일 업로드 완료. 임베딩 대기 중. */
    UPLOADED,
    /** 임베딩 처리 중. */
    PROCESSING,
    /** 임베딩 완료. 질문 검색 대상. */
    COMPLETED,
    /** 임베딩 실패. */
    FAILED
}
