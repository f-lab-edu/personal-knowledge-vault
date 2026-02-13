package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findTop5BySession_IdOrderByCreatedAtDesc(Long sessionId);

    List<ChatHistory> findTop20ByMemberIdAndSession_SessionKeyOrderByCreatedAtDesc(Long memberId, String sessionKey);

    List<ChatHistory> findBySession_IdOrderByCreatedAtAsc(Long sessionId);

    Optional<ChatHistory> findByIdAndMemberId(Long historyId, Long memberId);

    void deleteBySession_Id(Long sessionId);
}
