package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findBySession_IdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    List<ChatHistory> findByMemberIdAndSession_SessionKeyOrderByCreatedAtDesc(
            Long memberId,
            String sessionKey,
            Pageable pageable
    );

    List<ChatHistory> findBySession_IdOrderByCreatedAtAsc(Long sessionId);

    Optional<ChatHistory> findByIdAndMemberId(Long chatHistoryId, Long memberId);

    void deleteBySession_Id(Long sessionId);
}
