package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {

    Optional<ChatThread> findByMemberIdAndThreadKey(Long memberId, String threadKey);

    List<ChatThread> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
