package com.pkv.history.service;

import com.pkv.chat.domain.ChatHistory;
import com.pkv.chat.domain.ChatHistorySource;
import com.pkv.chat.domain.ChatSession;
import com.pkv.chat.dto.SourceReference;
import com.pkv.chat.repository.ChatHistoryRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.chat.repository.ChatSessionRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.history.dto.HistoryDetailResponse;
import com.pkv.history.dto.HistoryItemSummaryResponse;
import com.pkv.history.dto.SessionSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

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
        return chatHistoryRepository.findTop20ByMemberIdAndSession_SessionKeyOrderByCreatedAtDesc(memberId, sessionId)
                .stream()
                .map(history -> new HistoryItemSummaryResponse(
                        history.getId(),
                        history.getQuestion(),
                        history.getStatus().name(),
                        history.getCreatedAt()
                ))
                .toList();
    }

    public HistoryDetailResponse getHistoryDetail(Long memberId, Long historyId) {
        ChatHistory history = chatHistoryRepository.findByIdAndMemberId(historyId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.HISTORY_NOT_FOUND));

        List<SourceReference> sources = chatHistorySourceRepository.findByHistory_IdOrderByDisplayOrderAsc(historyId)
                .stream()
                .map(this::toSourceReference)
                .toList();

        return new HistoryDetailResponse(
                history.getQuestion(),
                history.getAnswer() == null ? "" : history.getAnswer(),
                sources,
                history.getStatus().name(),
                history.getCreatedAt()
        );
    }

    @Transactional
    public void deleteHistory(Long memberId, Long historyId) {
        if (chatHistoryRepository.findByIdAndMemberId(historyId, memberId).isEmpty()) {
            throw new PkvException(ErrorCode.HISTORY_NOT_FOUND);
        }

        chatHistorySourceRepository.deleteByHistory_Id(historyId);
        chatHistoryRepository.deleteById(historyId);
    }

    @Transactional
    public void deleteSession(Long memberId, String sessionId) {
        ChatSession session = chatSessionRepository.findByMemberIdAndSessionKey(memberId, sessionId)
                .orElse(null);

        if (session == null) {
            return;
        }

        List<Long> historyIds = chatHistoryRepository.findBySession_IdOrderByCreatedAtAsc(session.getId()).stream()
                .map(ChatHistory::getId)
                .toList();

        if (!historyIds.isEmpty()) {
            chatHistorySourceRepository.deleteByHistory_IdIn(historyIds);
        }
        chatHistoryRepository.deleteBySession_Id(session.getId());
        chatSessionRepository.delete(session);
    }

    private SourceReference toSourceReference(ChatHistorySource source) {
        return new SourceReference(
                source.getSourceId(),
                source.getSourceFileName(),
                source.getSourcePageNumber(),
                source.getSnippet()
        );
    }
}
