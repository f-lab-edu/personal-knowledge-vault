package com.pkv.document.repository;

import com.pkv.document.domain.Document;
import com.pkv.document.domain.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByMemberIdAndStatusNotOrderByCreatedAtDesc(Long memberId, DocumentStatus status);

    long countByMemberIdAndStatusNot(Long memberId, DocumentStatus status);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d WHERE d.memberId = :memberId AND d.status != :status")
    long sumFileSizeByMemberIdAndStatusNot(@Param("memberId") Long memberId, @Param("status") DocumentStatus status);

    boolean existsByMemberIdAndOriginalFileNameAndStatusNot(Long memberId, String originalFileName, DocumentStatus status);

    boolean existsByMemberIdAndStatus(Long memberId, DocumentStatus status);

    void deleteByMemberIdAndOriginalFileNameAndStatus(Long memberId, String originalFileName, DocumentStatus status);

    Optional<Document> findByIdAndMemberId(Long id, Long memberId);
}
