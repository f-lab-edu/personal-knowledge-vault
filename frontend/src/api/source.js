import axios from 'axios';
import { request, unwrapData } from './http';

const CONTENT_TYPE_MAP = {
    pdf: 'application/pdf',
    txt: 'text/plain',
    md: 'text/markdown',
};

export const getContentType = (extension) =>
    CONTENT_TYPE_MAP[extension.toLowerCase()] || 'application/octet-stream';

export const requestPresignedUrl = async (fileName, fileSize) => {
    const payload = await request('/api/sources/presign', {
        method: 'POST',
        body: { fileName, fileSize },
    });
    return unwrapData(payload);
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

export const confirmUpload = async (sourceId) => {
    const payload = await request(`/api/sources/${sourceId}/confirm`, {
        method: 'POST',
    });
    return unwrapData(payload);
};

export const getSources = async () => {
    const payload = await request('/api/sources');
    return unwrapData(payload);
};

export const deleteSource = async (sourceId) => {
    await request(`/api/sources/${sourceId}`, {
        method: 'DELETE',
    });
};
