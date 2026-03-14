import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { useCreateTurn } from './useTurn';
import { useThreadList } from './useThread';
import { getThreadTurns, getTurnDetail } from '@/api/thread';
import { getErrorMessage } from '@/utils/error';

const MAX_TURNS = 5;
const INVALID_THREAD_ERROR_CODE = 'Q001';
const THREAD_LIMIT_ERROR_CODE = 'Q002';
const SYSTEM_ERROR_MESSAGE = '죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해 주세요.';
const THREAD_LIMIT_MESSAGE = '현재 대화 질문 한도에 도달했습니다. 새 대화를 시작해 주세요.';

const toUserMessage = (id, prompt) => ({ id, role: 'user', content: prompt });
const toAssistantMessage = (id, answer, citations = []) => ({
    id,
    role: 'assistant',
    content: answer,
    citations,
});
const toSystemMessage = (id, content) => ({ id, role: 'system', content });

export const useThreadChat = () => {
    const [threadId, setThreadId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [threadEnded, setThreadEnded] = useState(false);
    const [isRestoring, setIsRestoring] = useState(true);

    const nextId = useRef(1);
    const initializedRef = useRef(false);
    const restoreSequenceRef = useRef(0);
    const pendingThreadIdRef = useRef(null);

    const {
        data: threads = [],
        isLoading: isThreadListLoading,
        isFetching: isThreadListFetching,
    } = useThreadList();
    const { mutateAsync: createTurn, isPending: loading } = useCreateTurn();

    const resetLocalState = useCallback(() => {
        setThreadId(null);
        setMessages([]);
        setThreadEnded(false);
        nextId.current = 1;
    }, []);

    const restoreThread = useCallback(async (targetThreadId) => {
        const restoreSequence = ++restoreSequenceRef.current;
        setIsRestoring(true);

        try {
            const turns = await getThreadTurns(targetThreadId);
            if (restoreSequence !== restoreSequenceRef.current) {
                return;
            }

            const orderedTurns = [...(turns || [])].reverse();

            if (orderedTurns.length === 0) {
                setThreadId(targetThreadId);
                setMessages([]);
                setThreadEnded(false);
                nextId.current = 1;
                return;
            }

            const details = await Promise.all(
                orderedTurns.map((turn) => getTurnDetail(targetThreadId, turn.turnId)),
            );
            if (restoreSequence !== restoreSequenceRef.current) {
                return;
            }

            nextId.current = 1;
            const restoredMessages = [];

            orderedTurns.forEach((turn, index) => {
                const detail = details[index];

                restoredMessages.push(toUserMessage(nextId.current++, turn.prompt));
                restoredMessages.push(toAssistantMessage(
                    nextId.current++,
                    detail?.answer || '',
                    detail?.citations || [],
                ));
            });

            setThreadId(targetThreadId);
            setMessages(restoredMessages);
            setThreadEnded(orderedTurns.length >= MAX_TURNS);
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
        if (initializedRef.current || isThreadListLoading) {
            return;
        }

        initializedRef.current = true;

        if (!threads.length) {
            setIsRestoring(false);
            return;
        }

        restoreThread(threads[0].threadId);
    }, [threads, isThreadListLoading, restoreThread]);

    useEffect(() => {
        if (!threadId || isThreadListLoading || isThreadListFetching) {
            return;
        }

        const exists = threads.some((thread) => thread.threadId === threadId);
        if (exists) {
            if (pendingThreadIdRef.current === threadId) {
                pendingThreadIdRef.current = null;
            }
            return;
        }

        if (pendingThreadIdRef.current === threadId) {
            return;
        }

        resetLocalState();
    }, [
        threads,
        isThreadListFetching,
        isThreadListLoading,
        resetLocalState,
        threadId,
    ]);

    const startNewThread = useCallback(() => {
        restoreSequenceRef.current += 1;
        pendingThreadIdRef.current = null;
        initializedRef.current = true;
        setIsRestoring(false);
        resetLocalState();
    }, [resetLocalState]);

    const selectThread = useCallback((targetThreadId) => {
        if (!targetThreadId) {
            startNewThread();
            return;
        }

        if (targetThreadId === threadId) {
            return;
        }

        pendingThreadIdRef.current = null;
        restoreThread(targetThreadId);
    }, [restoreThread, startNewThread, threadId]);

    const handleSend = useCallback(async (text) => {
        const trimmedText = text.trim();
        if (!trimmedText || loading || isRestoring || threadEnded) {
            return false;
        }

        const nextTurnCount = messages.filter((message) => message.role === 'user').length + 1;
        const userMessage = toUserMessage(nextId.current++, trimmedText);
        setMessages((prev) => [...prev, userMessage]);

        try {
            const response = await createTurn({ threadId, prompt: trimmedText });

            if (response?.threadId) {
                if (response.threadId !== threadId) {
                    pendingThreadIdRef.current = response.threadId;
                }
                setThreadId(response.threadId);
            }

            const assistantMessage = toAssistantMessage(
                nextId.current++,
                response?.answer || '',
                response?.citations || [],
            );

            setMessages((prev) => {
                const updated = [...prev, assistantMessage];

                if (nextTurnCount >= MAX_TURNS) {
                    return [...updated, toSystemMessage(nextId.current++, THREAD_LIMIT_MESSAGE)];
                }

                return updated;
            });

            if (nextTurnCount >= MAX_TURNS) {
                setThreadEnded(true);
            }

            return true;
        } catch (error) {
            const message = getErrorMessage(error, SYSTEM_ERROR_MESSAGE);

            if (error?.code === INVALID_THREAD_ERROR_CODE) {
                toast.error(message);
                startNewThread();
                return false;
            }

            if (error?.code === THREAD_LIMIT_ERROR_CODE) {
                toast.error(message);
                setThreadEnded(true);
                setMessages((prev) => [...prev, toSystemMessage(nextId.current++, message)]);
                return false;
            }

            setMessages((prev) => [...prev, toAssistantMessage(nextId.current++, SYSTEM_ERROR_MESSAGE)]);
            return false;
        }
    }, [createTurn, isRestoring, loading, messages, startNewThread, threadEnded, threadId]);

    return {
        threadId,
        messages,
        loading,
        threadEnded,
        isRestoring,
        startNewThread,
        selectThread,
        handleSend,
    };
};
