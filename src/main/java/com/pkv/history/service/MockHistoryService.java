package com.pkv.history.service;

import com.pkv.chat.dto.SourceReference;
import com.pkv.history.dto.HistoryDetailResponse;
import com.pkv.history.dto.HistoryItemSummaryResponse;
import com.pkv.history.dto.SessionSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MockHistoryService {

    private static final Instant NOW = Instant.now();

    public List<SessionSummaryResponse> getSessionList(Long memberId) {
        return List.of(
                new SessionSummaryResponse(
                        "session-001", "팩토리 패턴 분석", 3,
                        NOW.minus(1, ChronoUnit.HOURS)),
                new SessionSummaryResponse(
                        "session-002", "데이터베이스 스키마 검토", 2,
                        NOW.minus(1, ChronoUnit.DAYS)),
                new SessionSummaryResponse(
                        "session-003", "React Query 설정", 4,
                        NOW.minus(3, ChronoUnit.DAYS))
        );
    }

    public List<HistoryItemSummaryResponse> getSessionDetail(Long memberId, String sessionId) {
        return switch (sessionId) {
            case "session-001" -> List.of(
                    new HistoryItemSummaryResponse(1L, "팩토리 패턴이 뭔가요?", "COMPLETED",
                            NOW.minus(1, ChronoUnit.HOURS)),
                    new HistoryItemSummaryResponse(2L, "추상 팩토리와의 차이점은?", "COMPLETED",
                            NOW.minus(55, ChronoUnit.MINUTES)),
                    new HistoryItemSummaryResponse(3L, "실제 적용 사례를 알려주세요", "COMPLETED",
                            NOW.minus(50, ChronoUnit.MINUTES))
            );
            case "session-002" -> List.of(
                    new HistoryItemSummaryResponse(4L, "ERD 구조를 설명해주세요", "COMPLETED",
                            NOW.minus(1, ChronoUnit.DAYS)),
                    new HistoryItemSummaryResponse(5L, "인덱스 전략은 어떻게 되나요?", "COMPLETED",
                            NOW.minus(23, ChronoUnit.HOURS))
            );
            case "session-003" -> List.of(
                    new HistoryItemSummaryResponse(6L, "React Query 기본 설정 방법은?", "COMPLETED",
                            NOW.minus(3, ChronoUnit.DAYS)),
                    new HistoryItemSummaryResponse(7L, "캐싱 전략을 알려주세요", "COMPLETED",
                            NOW.minus(71, ChronoUnit.HOURS)),
                    new HistoryItemSummaryResponse(8L, "Mutation 사용법은?", "COMPLETED",
                            NOW.minus(70, ChronoUnit.HOURS)),
                    new HistoryItemSummaryResponse(9L, "에러 핸들링은 어떻게 하나요?", "FAILED",
                            NOW.minus(69, ChronoUnit.HOURS))
            );
            default -> List.of();
        };
    }

    public HistoryDetailResponse getHistoryDetail(Long memberId, Long historyId) {
        List<SourceReference> sources = List.of(
                new SourceReference("Design_Patterns.pdf", 12,
                        "팩토리 메서드 패턴은 객체를 생성하기 위한 인터페이스를 정의하지만, 서브클래스가 어떤 클래스를 인스턴스화할지 결정하게 합니다..."),
                new SourceReference("Architecture_v2.md", 1,
                        "우리는 다중 제공자를 지원하기 위해 결제 게이트웨이 서비스에 팩토리 패턴을 활용합니다...")
        );

        return new HistoryDetailResponse(
                "팩토리 패턴이 뭔가요?",
                "팩토리 패턴(Factory Pattern)은 객체를 생성하기 위한 인터페이스를 정의하지만, "
                        + "어떤 클래스의 인스턴스를 생성할지에 대한 결정은 서브클래스가 내리도록 하는 생성 패턴입니다. "
                        + "이 패턴은 클라이언트 코드를 구체적인 클래스에 결합시키지 않고 다양한 유형의 객체를 생성할 수 있게 합니다. "
                        + "테스트를 용이하게 하고 향후 새로운 유형을 쉽게 확장할 수 있게 해줍니다. "
                        + "추가적인 질문이 있으시면 말씀해 주세요.",
                sources,
                "COMPLETED",
                NOW.minus(1, ChronoUnit.HOURS)
        );
    }

    public void deleteHistory(Long memberId, Long historyId) {
        // Mock: no-op
    }

    public void deleteSession(Long memberId, String sessionId) {
        // Mock: no-op
    }
}
