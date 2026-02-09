import { useState, useCallback, useRef, useEffect } from 'react';
import { useSendMessage } from './useChat';

const MAX_TURNS = 5;
const SESSION_TIMEOUT_MS = 10 * 60 * 1000;

export const useChatSession = () => {
    const [sessionId, setSessionId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [sessionEnded, setSessionEnded] = useState(false);
    const nextId = useRef(1);
    const timeoutRef = useRef(null);

    const { mutate: sendMessage, isPending: loading } = useSendMessage();

    const resetSession = useCallback(() => {
        setSessionId(null);
        setMessages([]);
        setSessionEnded(false);
        nextId.current = 1;
        clearTimeout(timeoutRef.current);
    }, []);

    const startTimeout = useCallback(() => {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = setTimeout(() => {
            setMessages(prev => [...prev, {
                id: nextId.current++,
                role: 'system',
                content: '10분간 입력이 없어 대화가 종료되었습니다.',
            }]);
            setSessionEnded(true);
        }, SESSION_TIMEOUT_MS);
    }, []);

    useEffect(() => {
        return () => clearTimeout(timeoutRef.current);
    }, []);

    const handleSend = useCallback((text) => {
        let currentSessionId = sessionId;
        if (!currentSessionId) {
            currentSessionId = crypto.randomUUID();
            setSessionId(currentSessionId);
        }

        const userMsg = { id: nextId.current++, role: 'user', content: text };
        setMessages(prev => [...prev, userMsg]);

        const conversationHistory = [...messages, userMsg]
            .slice(-10)
            .map(({ role, content }) => ({ role, content }));

        sendMessage(
            { sessionId: currentSessionId, content: text, conversationHistory },
            {
                onSuccess: (data) => {
                    const assistantMsg = {
                        id: nextId.current++,
                        role: 'assistant',
                        content: data.content,
                        sources: data.sources,
                    };
                    setMessages(prev => {
                        const updated = [...prev, assistantMsg];

                        const turnCount = updated.filter(m => m.role === 'user').length;
                        if (turnCount >= MAX_TURNS) {
                            return [...updated, {
                                id: nextId.current++,
                                role: 'system',
                                content: '최대 대화 횟수에 도달하여 대화가 종료되었습니다.',
                            }];
                        }
                        return updated;
                    });

                    const currentTurnCount = messages.filter(m => m.role === 'user').length + 1;
                    if (currentTurnCount >= MAX_TURNS) {
                        setSessionEnded(true);
                        clearTimeout(timeoutRef.current);
                    } else {
                        startTimeout();
                    }
                },
                onError: () => {
                    const errorMsg = {
                        id: nextId.current++,
                        role: 'assistant',
                        content: '죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해 주세요.',
                    };
                    setMessages(prev => [...prev, errorMsg]);
                    startTimeout();
                },
            },
        );
    }, [sessionId, messages, sendMessage, startTimeout]);

    return { messages, loading, sessionEnded, resetSession, handleSend };
};
