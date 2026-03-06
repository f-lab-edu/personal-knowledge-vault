package com.pkv.chat.repository;

import com.pkv.chat.domain.ThreadTurn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThreadTurnRepository extends JpaRepository<ThreadTurn, Long> {

    List<ThreadTurn> findByThread_IdOrderByCreatedAtDesc(Long threadId, Pageable pageable);

    List<ThreadTurn> findByMemberIdAndThread_ThreadKeyOrderByCreatedAtDesc(Long memberId, String threadKey);

    Optional<ThreadTurn> findByIdAndMemberIdAndThread_ThreadKey(Long turnId, Long memberId, String threadKey);
}
