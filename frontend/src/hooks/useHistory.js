import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    getSessionList,
    getSessionDetail,
    getHistoryDetail,
    deleteHistory,
    deleteSession,
} from '../api/history';

const SESSIONS_QUERY_KEY = ['sessions'];

export const useSessionList = () => {
    return useQuery({
        queryKey: SESSIONS_QUERY_KEY,
        queryFn: getSessionList,
    });
};

export const useSessionDetail = (sessionId) => {
    return useQuery({
        queryKey: ['session', sessionId],
        queryFn: () => getSessionDetail(sessionId),
        enabled: !!sessionId,
    });
};

export const useHistoryDetail = (historyId) => {
    return useQuery({
        queryKey: ['history', historyId],
        queryFn: () => getHistoryDetail(historyId),
        enabled: !!historyId,
    });
};

export const useDeleteHistory = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (historyId) => deleteHistory(historyId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: SESSIONS_QUERY_KEY });
        },
    });
};

export const useDeleteSession = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (sessionId) => deleteSession(sessionId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: SESSIONS_QUERY_KEY });
        },
    });
};
