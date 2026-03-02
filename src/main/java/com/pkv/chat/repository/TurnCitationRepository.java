package com.pkv.chat.repository;

import com.pkv.chat.domain.TurnCitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurnCitationRepository extends JpaRepository<TurnCitation, Long> {

    List<TurnCitation> findByThreadTurn_IdOrderByDisplayOrderAsc(Long turnId);

    void deleteByThreadTurn_Id(Long turnId);
}
