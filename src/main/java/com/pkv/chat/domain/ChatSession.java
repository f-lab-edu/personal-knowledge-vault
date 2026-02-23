package com.pkv.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatSession create(Long memberId, String sessionKey, String firstQuestion, int maxSessionTitleLength) {
        return new ChatSession(memberId, sessionKey, createTitleFrom(firstQuestion, maxSessionTitleLength));
    }

    @Builder
    public ChatSession(Long memberId, String sessionKey, String title) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.sessionKey = Objects.requireNonNull(sessionKey, "sessionKey is required");
        this.title = validateTitle(title);
        this.questionCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementQuestionCount() {
        this.questionCount += 1;
        this.updatedAt = Instant.now();
    }

    public boolean isQuestionLimitReached(int maxQuestionCount) {
        return this.questionCount >= maxQuestionCount;
    }

    public static String createTitleFrom(String question, int maxSessionTitleLength) {
        String trimmed = Objects.requireNonNull(question, "question is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }

        if (trimmed.length() <= maxSessionTitleLength) {
            return trimmed;
        }

        return trimmed.substring(0, maxSessionTitleLength - 3) + "...";
    }

    private String validateTitle(String title) {
        String value = Objects.requireNonNull(title, "title is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("title is required");
        }
        return value;
    }
}
