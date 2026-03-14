import { useQuery } from '@tanstack/react-query';
import {
    getThreads,
    getThreadTurns,
    getTurnDetail,
} from '../api/thread';

const THREAD_BASE_QUERY_KEY = ['threads'];

export const THREAD_QUERY_KEYS = {
    all: THREAD_BASE_QUERY_KEY,
    list: [...THREAD_BASE_QUERY_KEY, 'list'],
    turns: (threadId) => [...THREAD_BASE_QUERY_KEY, 'turns', threadId],
    detail: (threadId, turnId) => [...THREAD_BASE_QUERY_KEY, 'detail', threadId, turnId],
};

export const useThreadList = () => {
    return useQuery({
        queryKey: THREAD_QUERY_KEYS.list,
        queryFn: getThreads,
    });
};

export const useThreadTurns = (threadId) => {
    return useQuery({
        queryKey: THREAD_QUERY_KEYS.turns(threadId),
        queryFn: () => getThreadTurns(threadId),
        enabled: !!threadId,
    });
};

export const useTurnDetail = (threadId, turnId) => {
    return useQuery({
        queryKey: THREAD_QUERY_KEYS.detail(threadId, turnId),
        queryFn: () => getTurnDetail(threadId, turnId),
        enabled: !!threadId && !!turnId,
    });
};
