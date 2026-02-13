package com.pkv.chat.history.service;

import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.history.HistoryPolicy;
import com.pkv.chat.history.dto.HistoryDetailResponse;
import com.pkv.chat.history.dto.HistoryItemSummaryResponse;
import com.pkv.chat.history.dto.HistorySourceReference;
import com.pkv.chat.history.dto.SessionSummaryResponse;
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
public class HistoryQueryService {

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
                        PageRequest.of(0, HistoryPolicy.MAX_SESSION_DETAIL_ITEMS)
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

    private HistorySourceReference toSourceReference(ChatHistorySource source) {
        return new HistorySourceReference(
                source.getSourceId(),
                source.getSourceFileName(),
                source.getSourcePageNumber(),
                source.getSnippet()
        );
    }
}
