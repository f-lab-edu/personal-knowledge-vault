import { buildApiUrl, request, unwrapData } from './http';

let refreshUnavailable = false;
let refreshPromise = null;

const withRefresh = async (operation) => {
    try {
        return await operation();
    } catch (error) {
        if (error.status !== 401) {
            throw error;
        }

        await refreshAccessToken();
        return await operation();
    }
};

export const startOAuthLogin = () => {
    window.location.assign(buildApiUrl('/oauth2/authorization/google'));
};

const refreshAccessToken = async () => {
    if (refreshUnavailable) {
        const error = new Error('Refresh token unavailable');
        error.status = 401;
        throw error;
    }

    if (refreshPromise) {
        return refreshPromise;
    }

    refreshPromise = request('/api/auth/refresh', { method: 'POST' })
        .then((payload) => {
            refreshUnavailable = false;
            return payload;
        })
        .catch((error) => {
            if (error?.status === 401 || error?.status === 403) {
                refreshUnavailable = true;
            }
            throw error;
        })
        .finally(() => {
            refreshPromise = null;
        });

    return refreshPromise;
};

export const getCurrentMember = async () => {
    return withRefresh(async () => {
        const payload = await request('/api/auth/me');
        return unwrapData(payload);
    });
};

export const logout = async () => {
    await withRefresh(() => request('/api/auth/logout', { method: 'POST' }));
};

export const withdraw = async () => {
    await withRefresh(() => request('/api/members/me', { method: 'DELETE' }));
};
