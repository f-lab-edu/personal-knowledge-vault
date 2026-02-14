package com.pkv.chat.service;

import com.pkv.chat.ChatPolicy;
import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.HistoryDetailResponse;
import com.pkv.chat.dto.HistoryItemSummaryResponse;
import com.pkv.chat.dto.HistorySourceReference;
import com.pkv.chat.dto.SessionSummaryResponse;
import com.pkv.chat.repository.ChatHistoryRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.chat.repository.ChatSessionRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatHistoryService {
    private static final int DEFAULT_PAGE_NUMBER = 1;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistorySourceRepository chatHistorySourceRepository;

    public List<SessionSummaryResponse> getSessionList(Long memberId) {
        return chatSessionRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(session -> new SessionSummaryResponse(
                        session.getSessionKey(),
                        session.getTitle(),
                        session.getQuestionCount(),
                        session.getCreatedAt()
                ))
                .toList();
    }

    public List<HistoryItemSummaryResponse> getSessionDetail(Long memberId, String sessionId) {
        return chatHistoryRepository.findByMemberIdAndSession_SessionKeyOrderByCreatedAtDesc(
                        memberId,
                        sessionId,
                        PageRequest.of(0, ChatPolicy.MAX_SESSION_DETAIL_ITEMS)
                )
                .stream()
                .map(chatHistory -> new HistoryItemSummaryResponse(
                        chatHistory.getId(),
                        chatHistory.getQuestion(),
                        chatHistory.getStatus().name(),
                        chatHistory.getCreatedAt()
                ))
                .toList();
    }

    public HistoryDetailResponse getHistoryDetail(Long memberId, Long chatHistoryId) {
        ChatHistory chatHistory = chatHistoryRepository.findByIdAndMemberId(chatHistoryId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.HISTORY_NOT_FOUND));

        List<HistorySourceReference> sources = chatHistorySourceRepository
                .findByChatHistory_IdOrderByDisplayOrderAsc(chatHistoryId)
                .stream()
                .map(this::toSourceReference)
                .toList();

        return new HistoryDetailResponse(
                chatHistory.getQuestion(),
                chatHistory.getAnswer() == null ? "" : chatHistory.getAnswer(),
                sources,
                chatHistory.getStatus().name(),
                chatHistory.getCreatedAt()
        );
    }

    @Transactional
    public void deleteHistory(Long memberId, Long chatHistoryId) {
        if (chatHistoryRepository.findByIdAndMemberId(chatHistoryId, memberId).isEmpty()) {
            throw new PkvException(ErrorCode.HISTORY_NOT_FOUND);
        }

        chatHistorySourceRepository.deleteByChatHistory_Id(chatHistoryId);
        chatHistoryRepository.deleteById(chatHistoryId);
    }

    @Transactional
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

    private HistorySourceReference toSourceReference(ChatHistorySource source) {
        Integer pageNumber = source.getSourcePageNumber();
        return new HistorySourceReference(
                source.getSourceId(),
                source.getSourceFileName(),
                pageNumber == null || pageNumber <= 0 ? DEFAULT_PAGE_NUMBER : pageNumber,
                source.getSnippet()
        );
    }
}
