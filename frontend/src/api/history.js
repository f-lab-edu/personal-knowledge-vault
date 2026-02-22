import { request, unwrapData } from './http';

export const getSessionList = async () => {
    const payload = await request('/api/history/sessions');
    return unwrapData(payload);
};

export const getSessionDetail = async (sessionId) => {
    const payload = await request(`/api/history/sessions/${sessionId}`);
    return unwrapData(payload);
};

export const getHistoryDetail = async (historyId) => {
    const payload = await request(`/api/history/${historyId}`);
    return unwrapData(payload);
};

export const deleteHistory = async (historyId) => {
    await request(`/api/history/${historyId}`, { method: 'DELETE' });
};

export const deleteSession = async (sessionId) => {
    await request(`/api/history/sessions/${sessionId}`, { method: 'DELETE' });
};
