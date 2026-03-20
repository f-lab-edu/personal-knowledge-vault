package com.pkv.chat;

public final class ThreadPolicy {

    public static final int MIN_PROMPT_LENGTH = 2;
    public static final int MAX_PROMPT_LENGTH = 1000;
    public static final int MAX_RESULTS = 5;
    public static final int MAX_THREAD_TURN_COUNT = 5;
    public static final int MAX_CONTEXT_TURNS = 4;
    public static final int MAX_THREAD_TITLE_LENGTH = 30;
    public static final double MIN_SCORE = 0.7;

    private ThreadPolicy() {
    }
}
