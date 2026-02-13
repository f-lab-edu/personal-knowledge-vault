package com.pkv.chat.repository;

import com.pkv.chat.domain.ChatHistorySource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatHistorySourceRepository extends JpaRepository<ChatHistorySource, Long> {

    List<ChatHistorySource> findByHistory_IdOrderByDisplayOrderAsc(Long historyId);

    void deleteByHistory_Id(Long historyId);

    void deleteByHistory_IdIn(Collection<Long> historyIds);
}
