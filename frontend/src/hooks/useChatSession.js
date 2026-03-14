import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { useSendMessage } from './useChat';
import { useSessionList } from './useHistory';
import { getHistoryDetail, getSessionDetail } from '@/api/history';
import { getErrorMessage } from '@/utils/error';

const MAX_TURNS = 5;
const INVALID_SESSION_ERROR_CODE = 'Q001';
const SESSION_LIMIT_ERROR_CODE = 'Q002';

const SYSTEM_ERROR_MESSAGE = '죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해 주세요.';

const toUserMessage = (id, question) => ({ id, role: 'user', content: question });
const toAssistantMessage = (id, answer, sources = []) => ({ id, role: 'assistant', content: answer, sources });
const toSystemMessage = (id, content) => ({ id, role: 'system', content });

export const useChatSession = () => {
    const [sessionId, setSessionId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [sessionEnded, setSessionEnded] = useState(false);
    const [isRestoring, setIsRestoring] = useState(true);

    const nextId = useRef(1);
    const initializedRef = useRef(false);
    const restoreSequenceRef = useRef(0);
    const pendingSessionIdRef = useRef(null);

    const {
        data: sessions = [],
        isLoading: isSessionListLoading,
        isFetching: isSessionListFetching,
    } = useSessionList();
    const { mutateAsync: sendMessage, isPending: loading } = useSendMessage();

    const resetLocalState = useCallback(() => {
        setSessionId(null);
        setMessages([]);
        setSessionEnded(false);
        nextId.current = 1;
    }, []);

    const restoreSession = useCallback(async (targetSessionId) => {
        const restoreSequence = ++restoreSequenceRef.current;
        setIsRestoring(true);

        try {
            const summaries = await getSessionDetail(targetSessionId);
            if (restoreSequence !== restoreSequenceRef.current) {
                return;
            }

            const orderedSummaries = [...(summaries || [])].reverse();

            if (orderedSummaries.length === 0) {
                setSessionId(targetSessionId);
                setMessages([]);
                setSessionEnded(false);
                nextId.current = 1;
                return;
            }

            const details = await Promise.all(
                orderedSummaries.map((summary) => getHistoryDetail(summary.chatHistoryId)),
            );
            if (restoreSequence !== restoreSequenceRef.current) {
                return;
            }

            nextId.current = 1;
            const restoredMessages = [];

            orderedSummaries.forEach((summary, index) => {
                const detail = details[index];

                restoredMessages.push(toUserMessage(nextId.current++, summary.question));
                restoredMessages.push(toAssistantMessage(
                    nextId.current++,
                    detail?.answer || '',
                    detail?.sources || [],
                ));
            });

            setSessionId(targetSessionId);
            setMessages(restoredMessages);
            setSessionEnded(orderedSummaries.length >= MAX_TURNS);
        } catch (error) {
            if (restoreSequence !== restoreSequenceRef.current) {
                return;
            }
            toast.error(getErrorMessage(error, '대화 복원에 실패했습니다.'));
            resetLocalState();
        } finally {
            if (restoreSequence === restoreSequenceRef.current) {
                setIsRestoring(false);
            }
        }
    }, [resetLocalState]);

    useEffect(() => {
        if (initializedRef.current || isSessionListLoading) {
            return;
        }

        initializedRef.current = true;

        if (!sessions.length) {
            setIsRestoring(false);
            return;
        }

        restoreSession(sessions[0].sessionId);
    }, [sessions, isSessionListLoading, restoreSession]);

    useEffect(() => {
        if (!sessionId || isSessionListLoading || isSessionListFetching) {
            return;
        }

        const exists = sessions.some((session) => session.sessionId === sessionId);
        if (exists) {
            if (pendingSessionIdRef.current === sessionId) {
                pendingSessionIdRef.current = null;
            }
            return;
        }

        if (pendingSessionIdRef.current === sessionId) {
            return;
        }

        resetLocalState();
    }, [
        sessions,
        isSessionListFetching,
        isSessionListLoading,
        resetLocalState,
        sessionId,
    ]);

    const startNewSession = useCallback(() => {
        restoreSequenceRef.current += 1;
        pendingSessionIdRef.current = null;
        initializedRef.current = true;
        setIsRestoring(false);
        resetLocalState();
    }, [resetLocalState]);

    const selectSession = useCallback((targetSessionId) => {
        if (!targetSessionId) {
            startNewSession();
            return;
        }

        if (targetSessionId === sessionId) {
            return;
        }

        pendingSessionIdRef.current = null;
        restoreSession(targetSessionId);
    }, [restoreSession, sessionId, startNewSession]);

    const handleSend = useCallback(async (text) => {
        const trimmedText = text.trim();
        if (!trimmedText || loading || isRestoring || sessionEnded) {
            return false;
        }

        const userMessage = toUserMessage(nextId.current++, trimmedText);
        setMessages((prev) => [...prev, userMessage]);

        try {
            const response = await sendMessage({ sessionId, content: trimmedText });

            if (response?.sessionId) {
                if (response.sessionId !== sessionId) {
                    pendingSessionIdRef.current = response.sessionId;
                }
                setSessionId(response.sessionId);
            }

            const assistantMessage = toAssistantMessage(
                nextId.current++,
                response?.content || '',
                response?.sources || [],
            );
            setMessages((prev) => [...prev, assistantMessage]);

            return true;
        } catch (error) {
            const message = getErrorMessage(error, SYSTEM_ERROR_MESSAGE);

            if (error?.code === INVALID_SESSION_ERROR_CODE) {
                toast.error(message);
                startNewSession();
                return false;
            }

            if (error?.code === SESSION_LIMIT_ERROR_CODE) {
                toast.error(message);
                setSessionEnded(true);
                setMessages((prev) => [...prev, toSystemMessage(nextId.current++, message)]);
                return false;
            }

            setMessages((prev) => [...prev, toAssistantMessage(nextId.current++, SYSTEM_ERROR_MESSAGE)]);
            return false;
        }
    }, [isRestoring, loading, sendMessage, sessionEnded, sessionId, startNewSession]);

    return {
        sessionId,
        messages,
        loading,
        sessionEnded,
        isRestoring,
        startNewSession,
        selectSession,
        handleSend,
    };
};
