package com.pkv.chat.repository;

import com.pkv.chat.domain.TurnCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TurnCitationRepository extends JpaRepository<TurnCitation, Long> {

    List<TurnCitation> findByThreadTurn_IdOrderByDisplayOrderAsc(Long turnId);

    void deleteByThreadTurn_Id(Long turnId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TurnCitation tc SET tc.documentId = null WHERE tc.documentId = :documentId")
    int clearDocumentIdByDocumentId(@Param("documentId") Long documentId);
}
