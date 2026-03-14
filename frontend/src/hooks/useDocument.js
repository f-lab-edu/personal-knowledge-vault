import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
    getDocuments,
    deleteDocument,
    requestPresignedUrl,
    uploadToS3,
    confirmUpload,
    getContentType,
} from '../api/document';
import { DOCUMENT_STATUS } from '../utils/constants';

const DOCUMENTS_QUERY_KEY = ['documents'];

const POLLING_STATUSES = Object.entries(DOCUMENT_STATUS)
    .filter(([, value]) => value.polling)
    .map(([status]) => status);

export const useDocuments = () => {
    return useQuery({
        queryKey: DOCUMENTS_QUERY_KEY,
        queryFn: getDocuments,
        refetchInterval: (query) => {
            const hasPending = query.state.data?.some((document) =>
                POLLING_STATUSES.includes(document.status),
            );
            return hasPending ? 3_000 : false;
        },
    });
};

export const useUploadDocument = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ file, onProgress }) => {
            const extension = file.name.split('.').pop().toLowerCase();
            const contentType = getContentType(extension);

            const { documentId, presignedUrl } = await requestPresignedUrl(
                file.name,
                file.size,
            );

            await uploadToS3(presignedUrl, file, contentType, onProgress);

            return confirmUpload(documentId);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: DOCUMENTS_QUERY_KEY });
        },
    });
};

export const useDeleteDocument = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (documentId) => deleteDocument(documentId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: DOCUMENTS_QUERY_KEY });
        },
    });
};
