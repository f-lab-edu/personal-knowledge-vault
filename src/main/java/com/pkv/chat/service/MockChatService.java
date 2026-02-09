package com.pkv.chat.service;

import com.pkv.chat.dto.ChatRequest;
import com.pkv.chat.dto.ChatResponse;
import com.pkv.chat.dto.SourceReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockChatService {

    public ChatResponse sendMessage(Long memberId, ChatRequest request) {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<SourceReference> sources = List.of(
                new SourceReference("Design_Patterns.pdf", 12,
                        "팩토리 메서드 패턴은 객체를 생성하기 위한 인터페이스를 정의하지만, 서브클래스가 어떤 클래스를 인스턴스화할지 결정하게 합니다..."),
                new SourceReference("Architecture_v2.md", 1,
                        "우리는 다중 제공자를 지원하기 위해 결제 게이트웨이 서비스에 팩토리 패턴을 활용합니다...")
        );

        String answer = "업로드하신 문서를 기반으로 답변드리겠습니다. "
                + "해당 질문에 대해 문서에서 관련 내용을 찾았습니다. "
                + "문서에 따르면 이 개념은 소프트웨어 설계에서 중요한 역할을 합니다. "
                + "구체적인 구현 방법과 활용 사례가 문서에 잘 정리되어 있습니다. "
                + "추가적인 질문이 있으시면 말씀해 주세요.";

        return new ChatResponse(answer, sources);
    }
}
