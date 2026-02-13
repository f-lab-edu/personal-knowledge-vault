package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatHistorySource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatHistorySourceRepository extends JpaRepository<ChatHistorySource, Long> {

    List<ChatHistorySource> findByChatHistory_IdOrderByDisplayOrderAsc(Long chatHistoryId);

    void deleteByChatHistory_Id(Long chatHistoryId);

    void deleteByChatHistory_IdIn(Collection<Long> chatHistoryIds);
}
