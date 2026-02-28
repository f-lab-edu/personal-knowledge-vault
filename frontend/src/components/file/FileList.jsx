import { clsx } from 'clsx';
import styles from './FileList.module.css';
import { useSources, useDeleteSource } from '../../hooks/useSource';
import { formatFileSize } from '../../utils/format';
import { getErrorMessage } from '../../utils/error';
import { toast } from '../../stores/toastStore';
import { confirm } from '../../stores/confirmStore';

const STATUS_CONFIG = {
    UPLOADED: { className: styles.statusPending, label: '분석 대기' },
    PROCESSING: { className: styles.statusEmbedding, label: '처리 중' },
    COMPLETED: { className: styles.statusCompleted, label: '준비됨' },
    FAILED: { className: styles.statusFailed, label: '실패' },
};

const StatusBadge = ({ status }) => {
    const config = STATUS_CONFIG[status];
    if (!config) return null;

    return (
        <span className={clsx(styles.badge, config.className)}>
            {config.label}
        </span>
    );
};

const FileList = () => {
    const { data: sources = [], isLoading } = useSources();
    const deleteMutation = useDeleteSource();

    const handleDelete = async (source) => {
        const confirmed = await confirm({
            title: '파일 삭제',
            description: `"${source.fileName}" 파일을 삭제하시겠습니까?`,
            variant: 'danger',
        });
        if (!confirmed) return;

        deleteMutation.mutate(source.id, {
            onError: (error) => {
                toast.error(getErrorMessage(error, '삭제에 실패했습니다.'));
            },
        });
    };

    if (isLoading) {
        return (
            <div className={styles.container}>
                <div className={styles.emptyMessage}>불러오는 중...</div>
            </div>
        );
    }

    if (sources.length === 0) {
        return (
            <div className={styles.container}>
                <div className={styles.emptyMessage}>
                    업로드된 문서가 없습니다.
                </div>
            </div>
        );
    }

    const canDelete = (status) => status === 'COMPLETED' || status === 'FAILED';

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <span>문서</span>
                <span>상태</span>
            </div>

            {sources.map((source) => (
                <div key={source.id} className={styles.fileItem}>
                    <div className={styles.fileInfo}>
                        <svg
                            className={clsx(styles.icon, source.extension === 'pdf' && styles.iconPdf)}
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                        >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        <div className={styles.details}>
                            <span className={styles.fileName}>{source.fileName}</span>
                            <span className={styles.fileSize}>{formatFileSize(source.fileSize)}</span>
                        </div>
                    </div>

                    <div className={styles.statusContainer}>
                        <StatusBadge status={source.status} />
                        {canDelete(source.status) && (
                            <button
                                className={styles.deleteButton}
                                onClick={() => handleDelete(source)}
                                disabled={deleteMutation.isPending}
                                title="삭제"
                            >
                                <svg width="14" height="14" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                </svg>
                            </button>
                        )}
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FileList;
