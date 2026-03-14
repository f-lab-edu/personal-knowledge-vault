import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    getSources,
    deleteSource,
    requestPresignedUrl,
    uploadToS3,
    confirmUpload,
    getContentType,
} from '../api/source';
import { SOURCE_STATUS } from '../utils/constants';

const SOURCES_QUERY_KEY = ['sources'];

const POLLING_STATUSES = Object.entries(SOURCE_STATUS)
    .filter(([, v]) => v.polling)
    .map(([k]) => k);

export const useSources = () => {
    return useQuery({
        queryKey: SOURCES_QUERY_KEY,
        queryFn: getSources,
        refetchInterval: (query) => {
            const hasPending = query.state.data?.some((s) =>
                POLLING_STATUSES.includes(s.status),
            );
            return hasPending ? 3_000 : false;
        },
    });
};

export const useUploadSource = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ file, onProgress }) => {
            const extension = file.name.split('.').pop().toLowerCase();
            const contentType = getContentType(extension);

            const { sourceId, presignedUrl } = await requestPresignedUrl(
                file.name,
                file.size,
            );

            await uploadToS3(presignedUrl, file, contentType, onProgress);

            return await confirmUpload(sourceId);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: SOURCES_QUERY_KEY });
        },
    });
};

export const useDeleteSource = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (sourceId) => deleteSource(sourceId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: SOURCES_QUERY_KEY });
        },
    });
};
