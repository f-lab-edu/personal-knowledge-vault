import { request, unwrapData } from './http';

export const getSessionList = async () => {
    const payload = await request('/api/chat-histories/sessions');
    const data = unwrapData(payload);
    if (Array.isArray(data)) {
        return data;
    }
    return data?.sessions ?? [];
};

export const getSessionDetail = async (sessionId) => {
    const payload = await request(`/api/chat-histories/sessions/${sessionId}`);
    const data = unwrapData(payload);
    if (Array.isArray(data)) {
        return data;
    }
    return data?.histories ?? [];
};

export const getHistoryDetail = async (chatHistoryId) => {
    const payload = await request(`/api/chat-histories/${chatHistoryId}`);
    return unwrapData(payload);
};
