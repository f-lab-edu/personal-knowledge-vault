import { request, unwrapData } from './http';

export const sendMessage = async (sessionId, content) => {
    const payload = await request('/api/chat/messages', {
        method: 'POST',
        body: { sessionId, content },
    });
    return unwrapData(payload);
};
