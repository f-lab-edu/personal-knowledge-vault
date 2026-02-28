const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

const normalizePath = (path) => (path.startsWith('/') ? path : `/${path}`);

export const buildApiUrl = (path) => {
    if (!API_BASE_URL) {
        return normalizePath(path);
    }

    return `${API_BASE_URL.replace(/\/$/, '')}${normalizePath(path)}`;
};

const isPlainObject = (value) => {
    if (!value || typeof value !== 'object') {
        return false;
    }

    return (
        !(value instanceof FormData) &&
        !(value instanceof URLSearchParams) &&
        !(value instanceof Blob)
    );
};

export const request = async (path, options = {}) => {
    const headers = new Headers(options.headers || {});
    let body = options.body;

    if (isPlainObject(body)) {
        body = JSON.stringify(body);
        if (!headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }
    }

    const response = await fetch(buildApiUrl(path), {
        credentials: 'include',
        ...options,
        headers,
        body,
    });

    const contentType = response.headers.get('content-type') || '';
    let payload = null;

    if (contentType.includes('application/json')) {
        payload = await response.json().catch(() => null);
    } else {
        payload = await response.text().catch(() => '');
    }

    if (!response.ok) {
        const error = new Error(payload?.error?.message || response.statusText);
        error.status = response.status;
        error.code = payload?.error?.code;
        error.payload = payload;
        throw error;
    }

    return payload;
};

export const unwrapData = (payload) => payload?.data ?? null;
