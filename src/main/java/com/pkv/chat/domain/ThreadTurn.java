package com.pkv.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "thread_turns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ChatThread thread;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatResponseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ThreadTurn create(
            Long memberId,
            ChatThread thread,
            String prompt,
            ChatResponseStatus status,
            String answer
    ) {
        return new ThreadTurn(memberId, thread, prompt, answer, status);
    }

    @Builder
    public ThreadTurn(Long memberId, ChatThread thread, String prompt, String answer, ChatResponseStatus status) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.thread = Objects.requireNonNull(thread, "thread is required");
        this.prompt = validatePrompt(prompt);
        this.answer = answer;
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private String validatePrompt(String prompt) {
        String value = Objects.requireNonNull(prompt, "prompt is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("prompt is required");
        }
        return value;
    }
}
