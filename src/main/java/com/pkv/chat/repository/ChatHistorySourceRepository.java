package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatHistorySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatHistorySourceRepository extends JpaRepository<ChatHistorySource, Long> {

    List<ChatHistorySource> findByChatHistory_IdOrderByDisplayOrderAsc(Long chatHistoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatHistorySource chs SET chs.sourceId = null WHERE chs.sourceId = :sourceId")
    int clearSourceIdBySourceId(@Param("sourceId") Long sourceId);
}
