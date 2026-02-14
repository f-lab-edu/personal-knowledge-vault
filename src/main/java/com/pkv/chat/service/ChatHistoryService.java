package com.pkv.chat.service;

import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.dto.ChatHistoryDetailResponse;
import com.pkv.chat.dto.ChatHistoryListResponse;
import com.pkv.chat.dto.ChatSessionListResponse;
import com.pkv.chat.dto.ChatSourceResponse;
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
@Transactional(readOnly = true)
public class ChatHistoryService {
    private static final int DEFAULT_PAGE_NUMBER = 1;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistorySourceRepository chatHistorySourceRepository;

    public ChatSessionListResponse getSessionList(Long memberId) {
        List<ChatSessionListResponse.Item> sessions = chatSessionRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(session -> new ChatSessionListResponse.Item(
                        session.getSessionKey(),
                        session.getTitle(),
                        session.getQuestionCount(),
                        session.getCreatedAt()
                ))
                .toList();
        return new ChatSessionListResponse(sessions);
    }

    public ChatHistoryListResponse getSessionDetail(Long memberId, String sessionId) {
        List<ChatHistoryListResponse.Item> histories = chatHistoryRepository.findByMemberIdAndSession_SessionKeyOrderByCreatedAtDesc(
                        memberId,
                        sessionId
                )
                .stream()
                .map(chatHistory -> new ChatHistoryListResponse.Item(
                        chatHistory.getId(),
                        chatHistory.getQuestion(),
                        chatHistory.getStatus().name(),
                        chatHistory.getCreatedAt()
                ))
                .toList();
        return new ChatHistoryListResponse(histories);
    }

    public ChatHistoryDetailResponse getHistoryDetail(Long memberId, Long chatHistoryId) {
        ChatHistory chatHistory = chatHistoryRepository.findByIdAndMemberId(chatHistoryId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.HISTORY_NOT_FOUND));

        List<ChatSourceResponse> sources = chatHistorySourceRepository
                .findByChatHistory_IdOrderByDisplayOrderAsc(chatHistoryId)
                .stream()
                .map(this::toSourceResponse)
                .toList();

        return new ChatHistoryDetailResponse(
                chatHistory.getQuestion(),
                chatHistory.getAnswer() == null ? "" : chatHistory.getAnswer(),
                sources,
                chatHistory.getStatus().name(),
                chatHistory.getCreatedAt()
        );
    }

    private ChatSourceResponse toSourceResponse(ChatHistorySource source) {
        Integer pageNumber = source.getSourcePageNumber();
        return new ChatSourceResponse(
                source.getSourceId(),
                source.getSourceFileName(),
                pageNumber == null || pageNumber <= 0 ? DEFAULT_PAGE_NUMBER : pageNumber,
                source.getSnippet()
        );
    }
}
