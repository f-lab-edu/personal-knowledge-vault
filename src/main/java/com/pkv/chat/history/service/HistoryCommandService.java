package com.pkv.chat.history.service;

import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.repository.ChatHistoryRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.chat.repository.ChatSessionRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional
public class HistoryCommandService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistorySourceRepository chatHistorySourceRepository;

    public void deleteHistory(Long memberId, Long chatHistoryId) {
        if (chatHistoryRepository.findByIdAndMemberId(chatHistoryId, memberId).isEmpty()) {
            throw new PkvException(ErrorCode.HISTORY_NOT_FOUND);
        }

        chatHistorySourceRepository.deleteByChatHistory_Id(chatHistoryId);
        chatHistoryRepository.deleteById(chatHistoryId);
    }

    public void deleteSession(Long memberId, String sessionId) {
        ChatSession session = chatSessionRepository.findByMemberIdAndSessionKey(memberId, sessionId)
                .orElse(null);

        if (session == null) {
            return;
        }

        List<Long> chatHistoryIds = chatHistoryRepository.findBySession_IdOrderByCreatedAtAsc(session.getId()).stream()
                .map(ChatHistory::getId)
                .toList();

        if (!chatHistoryIds.isEmpty()) {
            chatHistorySourceRepository.deleteByChatHistory_IdIn(chatHistoryIds);
        }
        chatHistoryRepository.deleteBySession_Id(session.getId());
        chatSessionRepository.delete(session);
    }
}
