import { useMutation } from '@tanstack/react-query';
import { sendMessage } from '../api/chat';

export const useSendMessage = () => {
    return useMutation({
        mutationFn: ({ sessionId, content, conversationHistory }) =>
            sendMessage(sessionId, content, conversationHistory),
    });
};
