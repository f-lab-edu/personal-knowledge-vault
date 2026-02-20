package com.pkv.chat;

public final class ChatPolicy {

    /**
     * 사용자가 입력하는 질문(content)의 최소 길이.
     */
    public static final int MIN_QUESTION_LENGTH = 2;

    /**
     * 사용자가 입력하는 질문(content)의 최대 길이.
     */
    public static final int MAX_QUESTION_LENGTH = 1000;

    /**
     * 질문 관련 벡터 스토어에서 가져올 결과 최대 개수.
     */
    public static final int MAX_RESULTS = 5;

    /**
     * 하나의 채팅 세션에서 허용되는 최대 질문 횟수.
     */
    public static final int MAX_SESSION_QUESTION_COUNT = 5;

    /**
     * 동일 세션에서 컨텍스트로 포함할 이전 대화 이력의 최대 개수(최신 순).
     */
    public static final int MAX_CONTEXT_HISTORY = 4;

    /**
     * 세션 제목의 최대 길이.
     */
    public static final int MAX_SESSION_TITLE_LENGTH = 30;

    /**
     * 벡터 검색 최소 유사도 점수 컷오프.
     */
    public static final double MIN_SCORE = 0.7;

    private ChatPolicy() {
    }
}
