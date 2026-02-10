package com.pkv.source.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "original_file_name", nullable = false, length = 100)
    private String originalFileName;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Source(Long memberId, String originalFileName, Long fileSize, String fileExtension, SourceStatus status) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.originalFileName = Objects.requireNonNull(originalFileName, "originalFileName is required");
        this.fileSize = Objects.requireNonNull(fileSize, "fileSize is required");
        this.fileExtension = Objects.requireNonNull(fileExtension, "fileExtension is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void assignStoragePath(String storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath is required");
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        if (this.status != SourceStatus.INITIATED) {
            throw new IllegalStateException("Source can only be confirmed from INITIATED status, current: " + this.status);
        }
        this.status = SourceStatus.UPLOADED;
        this.updatedAt = Instant.now();
    }

    public void startProcessing() {
        if (this.status != SourceStatus.UPLOADED) {
            throw new IllegalStateException("Source can only start processing from UPLOADED status, current: " + this.status);
        }
        this.status = SourceStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (this.status != SourceStatus.PROCESSING) {
            throw new IllegalStateException("Source can only be completed from PROCESSING status, current: " + this.status);
        }
        this.status = SourceStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        if (this.status != SourceStatus.PROCESSING) {
            throw new IllegalStateException("Source can only fail from PROCESSING status, current: " + this.status);
        }
        this.status = SourceStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public boolean isDeletable() {
        return this.status == SourceStatus.COMPLETED || this.status == SourceStatus.FAILED;
    }
}
