package com.pkv.chat;

public final class ChatPolicy {

    public static final int MIN_QUESTION_LENGTH = 2;
    public static final int MAX_QUESTION_LENGTH = 1000;
    public static final int MAX_RESULTS = 5;
    public static final int MAX_SESSION_QUESTION_COUNT = 5;
    public static final int MAX_CONTEXT_HISTORY = 4;
    public static final int MAX_SESSION_DETAIL_ITEMS = 20;
    public static final int MAX_SESSION_TITLE_LENGTH = 30;
    public static final double MIN_SCORE = 0.4;

    private ChatPolicy() {
    }
}
