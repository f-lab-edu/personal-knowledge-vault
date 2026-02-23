import { useQuery } from '@tanstack/react-query';
import {
    getSessionList,
    getSessionDetail,
    getHistoryDetail,
} from '../api/history';

const HISTORY_BASE_QUERY_KEY = ['history'];
export const HISTORY_QUERY_KEYS = {
    all: HISTORY_BASE_QUERY_KEY,
    sessions: [...HISTORY_BASE_QUERY_KEY, 'sessions'],
    session: (sessionId) => [...HISTORY_BASE_QUERY_KEY, 'session', sessionId],
    detail: (chatHistoryId) => [...HISTORY_BASE_QUERY_KEY, 'detail', chatHistoryId],
};

export const HISTORY_QUERY_KEY = HISTORY_QUERY_KEYS.all;
export const HISTORY_SESSIONS_QUERY_KEY = HISTORY_QUERY_KEYS.sessions;

export const useSessionList = () => {
    return useQuery({
        queryKey: HISTORY_QUERY_KEYS.sessions,
        queryFn: getSessionList,
    });
};

export const useSessionDetail = (sessionId) => {
    return useQuery({
        queryKey: HISTORY_QUERY_KEYS.session(sessionId),
        queryFn: () => getSessionDetail(sessionId),
        enabled: !!sessionId,
    });
};

export const useHistoryDetail = (chatHistoryId) => {
    return useQuery({
        queryKey: HISTORY_QUERY_KEYS.detail(chatHistoryId),
        queryFn: () => getHistoryDetail(chatHistoryId),
        enabled: !!chatHistoryId,
    });
};
