import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createTurn } from '../api/thread';
import { THREAD_QUERY_KEYS } from './useThread';

export const useCreateTurn = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ threadId, prompt }) => createTurn(threadId, prompt),
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: THREAD_QUERY_KEYS.list });

            if (data?.threadId) {
                queryClient.invalidateQueries({ queryKey: THREAD_QUERY_KEYS.turns(data.threadId) });
            }
        },
    });
};
