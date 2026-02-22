/**
 * 중앙 채팅 영역. 대화창과 입력폼
 */
import { useState } from 'react';
import { clsx } from 'clsx';
import styles from './ChatArea.module.css';
import ChatInput from '../components/chat/ChatInput';
import MessageList from '../components/chat/MessageList';

const INITIAL_MESSAGES = [
    {
        id: 1,
        role: 'user',
        content: '디자인 문서에 설명된 팩토리 패턴이 무엇인가요?'
    },
    {
        id: 2,
        role: 'assistant',
        content: '팩토리 패턴(Factory Pattern)은 객체를 생성하기 위한 인터페이스를 정의하지만, 어떤 클래스의 인스턴스를 생성할지에 대한 결정은 서브클래스가 내리도록 하는 생성 패턴입니다. 업로드된 디자인 문서의 맥락에서, 이 패턴은 클라이언트 코드를 구체적인 클래스에 결합시키지 않고 다양한 유형의 "PaymentProcessor"(CreditCard, PayPal, Stripe)를 인스턴스화하는 데 사용됩니다. 이러한 분리는 테스트를 용이하게 하고 향후 새로운 결제 방식을 쉽게 확장할 수 있게 해줍니다.',
        sources: [
            { fileName: 'Design_Patterns.pdf', pageNumber: 12, snippet: '팩토리 메서드 패턴은 객체를 생성하기 위한 인터페이스를 정의하지만, 서브클래스가 어떤 클래스를 인스턴스화할지 결정하게 합니다...' },
            { fileName: 'Architecture_v2.md', pageNumber: 1, snippet: '우리는 다중 제공자를 지원하기 위해 결제 게이트웨이 서비스에 팩토리 패턴을 활용합니다...' }
        ]
    }
];

const ChatArea = () => {
    const [messages, setMessages] = useState(INITIAL_MESSAGES);
    const [loading, setLoading] = useState(false);

    const handleSend = (text) => {
        // Optimistic User Message
        const newUserMsg = { id: Date.now(), role: 'user', content: text };
        setMessages(prev => [...prev, newUserMsg]);
        setLoading(true);

        // Mock AI Response after delay
        setTimeout(() => {
            const newAiMsg = {
                id: Date.now() + 1,
                role: 'assistant',
                content: '이것은 "New York Times" 미니멀리스트 디자인 시스템을 기반으로 한 시뮬레이션 응답입니다. 백엔드 연결은 아직 설정되지 않았습니다.',
                sources: [
                    { fileName: 'Mock_Source.pdf', pageNumber: 1, snippet: '이것은 문서에서 발견된 관련 내용을 나타내는 모의 스니펫입니다.' }
                ]
            };
            setMessages(prev => [...prev, newAiMsg]);
            setLoading(false);
        }, 1500);
    };

    return (
        <div className={styles.container}>
            {/* Scrollable Message Area */}
            <div className={styles.scrollArea}>
                <div className={styles.contentWrapper}>
                    {messages.length === 0 ? (
                        <div className={clsx(styles.welcomeWrapper, 'animate-fade-in')}>
                            <h1 className={styles.welcomeTitle}>
                                좋은 아침입니다.
                            </h1>
                            <p className={styles.welcomeSubtitle}>
                                오늘 어떤 문서를 찾아드릴까요?
                            </p>
                        </div>
                    ) : (
                        <MessageList messages={messages} />
                    )}
                    {loading && (
                        <div className={styles.loading}>
                            <span>생각 중...</span>
                        </div>
                    )}
                </div>
            </div>

            {/* Input Area (Fixed at bottom) */}
            <div className={styles.inputWrapper}>
                <ChatInput onSend={handleSend} disabled={loading} />
            </div>
        </div>
    );
};

export default ChatArea;
