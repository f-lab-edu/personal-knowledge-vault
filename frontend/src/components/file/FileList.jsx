import { cn } from '@/lib/utils';
import { FileText, Trash2 } from 'lucide-react';
import { useSources, useDeleteSource } from '@/hooks/useSource';
import { formatFileSize } from '@/utils/format';
import { getErrorMessage } from '@/utils/error';
import { toast } from 'sonner';
import { confirm } from '@/stores/confirmStore';
import { SOURCE_STATUS } from '@/utils/constants';

const STATUS_STYLES = {
    UPLOADED: 'text-muted-foreground bg-black/5',
    PROCESSING: 'text-[var(--color-info)] bg-[rgba(25,118,210,0.1)]',
    COMPLETED: 'text-[var(--color-success)] bg-[rgba(56,142,60,0.1)]',
    FAILED: 'text-[var(--color-error)] bg-[rgba(211,47,47,0.1)]',
};

const StatusBadge = ({ status }) => {
    const config = SOURCE_STATUS[status];
    if (!config) return null;

    return (
        <span className={cn(
            "text-[0.5625rem] px-2 py-1 rounded-sm font-bold uppercase tracking-widest leading-tight whitespace-nowrap shrink-0",
            STATUS_STYLES[status]
        )}>
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
            <div className="flex flex-col gap-1">
                <div className="px-3 py-6 text-center text-sm text-[var(--color-tertiary)]">불러오는 중...</div>
            </div>
        );
    }

    if (sources.length === 0) {
        return (
            <div className="flex flex-col gap-1">
                <div className="px-3 py-6 text-center text-sm text-[var(--color-tertiary)]">
                    업로드된 문서가 없습니다.
                </div>
            </div>
        );
    }

    const canDelete = (status) => status === 'COMPLETED' || status === 'FAILED';

    return (
        <div className="flex flex-col gap-1">
            <div className="flex items-center justify-between px-3 py-2 mb-1 border-b border-[var(--color-border-light)] text-[0.625rem] font-bold text-[var(--color-tertiary)] uppercase tracking-widest">
                <span>문서</span>
                <span>상태</span>
            </div>

            {sources.map((source) => (
                <div key={source.id} className="group flex items-center justify-between px-3 py-2 rounded-sm cursor-pointer transition-all border border-transparent hover:bg-background hover:border-[var(--color-border-light)] hover:shadow-sm">
                    <div className="flex items-center gap-3 overflow-hidden">
                        <FileText className={cn(
                            "size-3.5 shrink-0 text-[var(--color-tertiary)] transition-colors group-hover:text-muted-foreground",
                            source.extension === 'pdf' && "text-[var(--color-error)] opacity-80 group-hover:text-[var(--color-error)] group-hover:opacity-100"
                        )} />
                        <div className="flex flex-col min-w-0">
                            <span className="text-xs font-medium text-foreground truncate tracking-tight">{source.fileName}</span>
                            <span className="text-[0.625rem] text-[var(--color-tertiary)] mt-1">{formatFileSize(source.fileSize)}</span>
                        </div>
                    </div>

                    <div className="flex items-center shrink-0 ml-2">
                        <StatusBadge status={source.status} />
                        {canDelete(source.status) && (
                            <button
                                className="flex items-center justify-center p-1 ml-2 rounded-sm text-[var(--color-tertiary)] cursor-pointer opacity-0 transition-all group-hover:opacity-100 hover:text-[var(--color-error)] hover:bg-[rgba(211,47,47,0.08)] disabled:opacity-40 disabled:cursor-not-allowed"
                                onClick={() => handleDelete(source)}
                                disabled={deleteMutation.isPending}
                                title="삭제"
                            >
                                <Trash2 className="size-3.5" />
                            </button>
                        )}
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FileList;
