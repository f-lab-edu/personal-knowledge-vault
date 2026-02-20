package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByMemberIdAndSessionKey(Long memberId, String sessionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM ChatSession cs WHERE cs.memberId = :memberId AND cs.sessionKey = :sessionKey")
    Optional<ChatSession> findByMemberIdAndSessionKeyForUpdate(@Param("memberId") Long memberId,
                                                                @Param("sessionKey") String sessionKey);

    List<ChatSession> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
