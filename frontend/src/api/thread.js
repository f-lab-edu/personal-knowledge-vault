import { requestWithFallback, unwrapData } from './http';

const firstDefined = (...values) =>
    values.find((value) => value !== undefined && value !== null);

const extractCollection = (data, ...keys) => {
    if (Array.isArray(data)) {
        return data;
    }

    for (const key of keys) {
        if (Array.isArray(data?.[key])) {
            return data[key];
        }
    }

    return [];
};

const normalizeCitations = (citations = []) =>
    (citations || []).map((citation) => ({
        documentId: firstDefined(citation?.documentId, citation?.sourceId) ?? null,
        fileName: citation?.fileName ?? '알 수 없는 파일',
        pageNumber: citation?.pageNumber ?? null,
        snippet: citation?.snippet ?? '',
    }));

export const createTurn = async (threadId, prompt) => {
    const payload = await requestWithFallback([
        {
            path: '/api/threads/turns',
            options: {
                method: 'POST',
                body: { threadId, prompt },
            },
        },
        threadId && {
            path: `/api/threads/${threadId}/turns`,
            options: {
                method: 'POST',
                body: { prompt },
            },
        },
        {
            path: '/api/turns',
            options: {
                method: 'POST',
                body: { threadId, prompt },
            },
        },
        {
            path: '/api/chat/messages',
            options: {
                method: 'POST',
                body: { sessionId: threadId, content: prompt },
            },
        },
    ]);
    const data = unwrapData(payload);

    return {
        threadId: firstDefined(data?.threadId, data?.sessionId) ?? null,
        answer: firstDefined(data?.answer, data?.content) ?? '',
        citations: normalizeCitations(firstDefined(data?.citations, data?.sources)),
    };
};

export const getThreads = async () => {
    const payload = await requestWithFallback([
        { path: '/api/threads' },
        { path: '/api/chat-histories/sessions' },
    ]);
    const data = unwrapData(payload);
    const threads = extractCollection(data, 'threads', 'sessions');

    return threads.map((thread) => ({
        threadId: firstDefined(thread?.threadId, thread?.sessionId) ?? null,
        title: thread?.title ?? '',
        turnCount: firstDefined(thread?.turnCount, thread?.questionCount) ?? 0,
        createdAt: thread?.createdAt ?? null,
    }));
};

export const getThreadTurns = async (threadId) => {
    const encodedThreadId = encodeURIComponent(threadId);
    const payload = await requestWithFallback([
        { path: `/api/threads/${encodedThreadId}/turns` },
        { path: `/api/turns?threadId=${encodedThreadId}` },
        { path: `/api/chat-histories/sessions/${encodedThreadId}` },
    ]);
    const data = unwrapData(payload);
    const turns = extractCollection(data, 'turns', 'histories');

    return turns.map((turn) => ({
        turnId: firstDefined(turn?.turnId, turn?.chatHistoryId) ?? null,
        prompt: firstDefined(turn?.prompt, turn?.question) ?? '',
        status: turn?.status ?? '',
        createdAt: turn?.createdAt ?? null,
    }));
};

export const getTurnDetail = async (threadId, turnId) => {
    const encodedThreadId = encodeURIComponent(threadId);
    const payload = await requestWithFallback([
        { path: `/api/threads/${encodedThreadId}/turns/${turnId}` },
        { path: `/api/turns/${turnId}` },
        { path: `/api/chat-histories/${turnId}` },
    ]);
    const data = unwrapData(payload);

    return {
        threadId: firstDefined(data?.threadId, data?.sessionId, threadId) ?? null,
        turnId: firstDefined(data?.turnId, data?.chatHistoryId, turnId) ?? null,
        prompt: firstDefined(data?.prompt, data?.question) ?? '',
        answer: firstDefined(data?.answer, data?.content) ?? '',
        citations: normalizeCitations(firstDefined(data?.citations, data?.sources)),
        status: data?.status ?? '',
        createdAt: data?.createdAt ?? null,
    };
};
