package com.pkv.source.repository;

import com.pkv.source.domain.Source;
import com.pkv.source.domain.SourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByMemberIdAndStatusNotOrderByCreatedAtDesc(Long memberId, SourceStatus status);

    long countByMemberIdAndStatusNot(Long memberId, SourceStatus status);

    @Query("SELECT COALESCE(SUM(s.fileSize), 0) FROM Source s WHERE s.memberId = :memberId AND s.status != :status")
    long sumFileSizeByMemberIdAndStatusNot(@Param("memberId") Long memberId, @Param("status") SourceStatus status);

    boolean existsByMemberIdAndOriginalFileNameAndStatusNot(Long memberId, String originalFileName, SourceStatus status);

    boolean existsByMemberIdAndStatus(Long memberId, SourceStatus status);

    void deleteByMemberIdAndOriginalFileNameAndStatus(Long memberId, String originalFileName, SourceStatus status);

    Optional<Source> findByIdAndMemberId(Long id, Long memberId);
}
