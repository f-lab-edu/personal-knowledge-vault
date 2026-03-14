import axios from 'axios';
import { requestWithFallback, unwrapData } from './http';

const CONTENT_TYPE_MAP = {
    pdf: 'application/pdf',
    txt: 'text/plain',
    md: 'text/markdown',
};

const firstDefined = (...values) =>
    values.find((value) => value !== undefined && value !== null);

const extractCollection = (data, key) => {
    if (Array.isArray(data)) {
        return data;
    }

    return Array.isArray(data?.[key]) ? data[key] : [];
};

const normalizeDocument = (document) => ({
    id: firstDefined(document?.documentId, document?.id, document?.sourceId) ?? null,
    fileName: document?.fileName ?? '알 수 없는 문서',
    fileSize: document?.fileSize ?? 0,
    extension: document?.extension ?? '',
    status: document?.status ?? '',
    createdAt: document?.createdAt ?? null,
});

export const getContentType = (extension) =>
    CONTENT_TYPE_MAP[extension.toLowerCase()] || 'application/octet-stream';

export const requestPresignedUrl = async (fileName, fileSize) => {
    const payload = await requestWithFallback([
        {
            path: '/api/documents/presign',
            options: {
                method: 'POST',
                body: { fileName, fileSize },
            },
        },
        {
            path: '/api/sources/presign',
            options: {
                method: 'POST',
                body: { fileName, fileSize },
            },
        },
    ]);
    const data = unwrapData(payload);

    return {
        documentId: firstDefined(data?.documentId, data?.id, data?.sourceId) ?? null,
        presignedUrl: data?.presignedUrl ?? '',
        expiresAt: data?.expiresAt ?? null,
    };
};

export const uploadToS3 = async (presignedUrl, file, contentType, onProgress) => {
    await axios.put(presignedUrl, file, {
        headers: { 'Content-Type': contentType },
        onUploadProgress: (e) => {
            if (e.lengthComputable && onProgress) {
                onProgress(Math.round((e.loaded / e.total) * 100));
            }
        },
    });
};

export const confirmUpload = async (documentId) => {
    const payload = await requestWithFallback([
        {
            path: `/api/documents/${documentId}/confirm`,
            options: { method: 'POST' },
        },
        {
            path: `/api/sources/${documentId}/confirm`,
            options: { method: 'POST' },
        },
    ]);
    return normalizeDocument(unwrapData(payload));
};

export const getDocuments = async () => {
    const payload = await requestWithFallback([
        { path: '/api/documents' },
        { path: '/api/sources' },
    ]);
    const data = unwrapData(payload);

    return extractCollection(data, 'documents').map(normalizeDocument);
};

export const deleteDocument = async (documentId) => {
    await requestWithFallback([
        {
            path: `/api/documents/${documentId}`,
            options: { method: 'DELETE' },
        },
        {
            path: `/api/sources/${documentId}`,
            options: { method: 'DELETE' },
        },
    ]);
};
