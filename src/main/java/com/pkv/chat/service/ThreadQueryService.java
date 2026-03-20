package com.pkv.chat.service;

import com.pkv.chat.domain.ChatThread;
import com.pkv.chat.domain.ThreadTurn;
import com.pkv.chat.domain.TurnCitation;
import com.pkv.chat.dto.CitationResponse;
import com.pkv.chat.dto.ThreadListResponse;
import com.pkv.chat.dto.ThreadTurnDetailResponse;
import com.pkv.chat.dto.ThreadTurnListResponse;
import com.pkv.chat.repository.ChatThreadRepository;
import com.pkv.chat.repository.ThreadTurnRepository;
import com.pkv.chat.repository.TurnCitationRepository;
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
public class ThreadQueryService {
    private static final int DEFAULT_PAGE_NUMBER = 1;

    private final ChatThreadRepository chatThreadRepository;
    private final ThreadTurnRepository threadTurnRepository;
    private final TurnCitationRepository turnCitationRepository;

    public ThreadListResponse getThreadList(Long memberId) {
        List<ThreadListResponse.ThreadSummary> threads = chatThreadRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(thread -> new ThreadListResponse.ThreadSummary(
                        thread.getThreadKey(),
                        thread.getTitle(),
                        thread.getTurnCount(),
                        thread.getCreatedAt()
                ))
                .toList();
        return new ThreadListResponse(threads);
    }

    public ThreadTurnListResponse getThreadTurns(Long memberId, String threadId) {
        ensureThreadExists(memberId, threadId);

        List<ThreadTurnListResponse.TurnSummary> turns = threadTurnRepository
                .findByMemberIdAndThread_ThreadKeyOrderByCreatedAtDesc(memberId, threadId)
                .stream()
                .map(turn -> new ThreadTurnListResponse.TurnSummary(
                        turn.getId(),
                        turn.getPrompt(),
                        turn.getStatus().name(),
                        turn.getCreatedAt()
                ))
                .toList();
        return new ThreadTurnListResponse(turns);
    }

    public ThreadTurnDetailResponse getTurnDetail(Long memberId, String threadId, Long turnId) {
        ThreadTurn turn = threadTurnRepository.findByIdAndMemberIdAndThread_ThreadKey(turnId, memberId, threadId)
                .orElseThrow(() -> new PkvException(ErrorCode.TURN_NOT_FOUND));

        List<CitationResponse> citations = turnCitationRepository
                .findByThreadTurn_IdOrderByDisplayOrderAsc(turnId)
                .stream()
                .map(this::toCitationResponse)
                .toList();

        return new ThreadTurnDetailResponse(
                turn.getPrompt(),
                turn.getAnswer() == null ? "" : turn.getAnswer(),
                citations,
                turn.getStatus().name(),
                turn.getCreatedAt()
        );
    }

    @Transactional
    public void deleteThread(Long memberId, String threadId) {
        ChatThread thread = chatThreadRepository.findByMemberIdAndThreadKey(memberId, threadId)
                .orElseThrow(() -> new PkvException(ErrorCode.THREAD_NOT_FOUND));

        List<ThreadTurn> turns = threadTurnRepository.findByMemberIdAndThread_ThreadKeyOrderByCreatedAtDesc(memberId, threadId);
        for (ThreadTurn turn : turns) {
            turnCitationRepository.deleteByThreadTurn_Id(turn.getId());
            threadTurnRepository.delete(turn);
        }

        chatThreadRepository.delete(thread);
    }

    @Transactional
    public void deleteTurn(Long memberId, String threadId, Long turnId) {
        ThreadTurn turn = threadTurnRepository.findByIdAndMemberIdAndThread_ThreadKey(turnId, memberId, threadId)
                .orElseThrow(() -> new PkvException(ErrorCode.TURN_NOT_FOUND));

        turnCitationRepository.deleteByThreadTurn_Id(turnId);
        threadTurnRepository.delete(turn);
        turn.getThread().decrementTurnCount();
    }

    private void ensureThreadExists(Long memberId, String threadId) {
        if (chatThreadRepository.findByMemberIdAndThreadKey(memberId, threadId).isEmpty()) {
            throw new PkvException(ErrorCode.THREAD_NOT_FOUND);
        }
    }

    private CitationResponse toCitationResponse(TurnCitation citation) {
        Integer pageNumber = citation.getDocumentPageNumber();
        return new CitationResponse(
                citation.getDocumentId(),
                citation.getDocumentFileName(),
                pageNumber == null || pageNumber <= 0 ? DEFAULT_PAGE_NUMBER : pageNumber,
                citation.getSnippet()
        );
    }
}
