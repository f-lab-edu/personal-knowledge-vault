package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatThread;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {

    Optional<ChatThread> findByMemberIdAndThreadKey(Long memberId, String threadKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ct FROM ChatThread ct WHERE ct.memberId = :memberId AND ct.threadKey = :threadKey")
    Optional<ChatThread> findByMemberIdAndThreadKeyForUpdate(@Param("memberId") Long memberId,
                                                              @Param("threadKey") String threadKey);

    List<ChatThread> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
