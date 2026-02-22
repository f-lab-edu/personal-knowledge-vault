import { request, unwrapData } from './http';

export const sendMessage = async (sessionId, content, conversationHistory) => {
    const payload = await request('/api/chat/messages', {
        method: 'POST',
        body: { sessionId, content, conversationHistory },
    });
    return unwrapData(payload);
};
