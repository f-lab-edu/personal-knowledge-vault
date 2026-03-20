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
@Table(name = "chat_threads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "thread_key", nullable = false, length = 64)
    private String threadKey;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatThread create(Long memberId, String threadKey, String firstPrompt, int maxThreadTitleLength) {
        return new ChatThread(memberId, threadKey, createTitleFrom(firstPrompt, maxThreadTitleLength));
    }

    @Builder
    public ChatThread(Long memberId, String threadKey, String title) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.threadKey = Objects.requireNonNull(threadKey, "threadKey is required");
        this.title = validateTitle(title);
        this.turnCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementTurnCount() {
        this.turnCount += 1;
        this.updatedAt = Instant.now();
    }

    public void decrementTurnCount() {
        if (this.turnCount > 0) {
            this.turnCount -= 1;
            this.updatedAt = Instant.now();
        }
    }

    public boolean isTurnLimitReached(int maxTurnCount) {
        return this.turnCount >= maxTurnCount;
    }

    public static String createTitleFrom(String prompt, int maxThreadTitleLength) {
        String trimmed = Objects.requireNonNull(prompt, "prompt is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("prompt is required");
        }

        if (trimmed.length() <= maxThreadTitleLength) {
            return trimmed;
        }

        return trimmed.substring(0, maxThreadTitleLength - 3) + "...";
    }

    private String validateTitle(String title) {
        String value = Objects.requireNonNull(title, "title is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("title is required");
        }
        return value;
    }
}
