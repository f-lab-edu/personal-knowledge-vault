import { useMutation, useQueryClient } from '@tanstack/react-query';
import { sendMessage } from '../api/chat';
import { HISTORY_QUERY_KEYS } from './useHistory';

export const useSendMessage = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ sessionId, content }) => sendMessage(sessionId, content),
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: HISTORY_QUERY_KEYS.sessions });
            if (data?.sessionId) {
                queryClient.invalidateQueries({ queryKey: HISTORY_QUERY_KEYS.session(data.sessionId) });
            }
        },
    });
};
