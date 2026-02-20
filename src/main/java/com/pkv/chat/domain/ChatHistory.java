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
@Table(name = "chat_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ChatSession session;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatResponseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatHistory create(
            Long memberId,
            ChatSession session,
            String question,
            ChatResponseStatus status,
            String answer
    ) {
        return new ChatHistory(memberId, session, question, answer, status);
    }

    @Builder
    public ChatHistory(Long memberId, ChatSession session, String question, String answer, ChatResponseStatus status) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.session = Objects.requireNonNull(session, "session is required");
        this.question = validateQuestion(question);
        this.answer = answer;
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private String validateQuestion(String question) {
        String value = Objects.requireNonNull(question, "question is required").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }
        return value;
    }
}
