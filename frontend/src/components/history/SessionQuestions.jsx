import { Trash2 } from 'lucide-react';
import { useSessionDetail, useDeleteHistory } from '@/hooks/useHistory';
import { confirm } from '@/stores/confirmStore';
import { toast } from 'sonner';
import { getErrorMessage } from '@/utils/error';
import { STATUS_LABEL } from '@/utils/constants';

const SessionQuestions = ({ sessionId, onSelectHistory }) => {
    const { data: questions, isLoading } = useSessionDetail(sessionId);
    const deleteMutation = useDeleteHistory();

    const handleDelete = async (e, item) => {
        e.stopPropagation();
        const confirmed = await confirm({
            title: '질문 삭제',
            description: `"${item.question}" 항목을 삭제하시겠습니까?`,
            variant: 'danger',
        });
        if (!confirmed) return;

        deleteMutation.mutate(item.historyId, {
            onError: (error) => {
                toast.error(getErrorMessage(error, '삭제에 실패했습니다.'));
            },
        });
    };

    if (isLoading) {
        return <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">불러오는 중...</div>;
    }

    if (!questions || questions.length === 0) {
        return null;
    }

    return (
        <div className="flex flex-col gap-1">
            {questions.map((item) => (
                <div
                    key={item.historyId}
                    className="group flex items-center text-left px-3 py-2 rounded-sm transition-all w-full cursor-pointer hover:bg-white hover:shadow-sm"
                    onClick={() => onSelectHistory(item.historyId)}
                >
                    <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-foreground truncate mb-1 transition-colors group-hover:text-primary">
                            {item.question}
                        </div>
                        <div className="text-[0.625rem] text-[var(--color-tertiary)]">
                            {STATUS_LABEL[item.status] || item.status}
                        </div>
                    </div>
                    <button
                        className="flex items-center justify-center w-6 h-6 shrink-0 rounded-sm text-[var(--color-tertiary)] cursor-pointer opacity-0 transition-all group-hover:opacity-100 hover:text-[var(--color-error)] hover:bg-[rgba(211,47,47,0.08)]"
                        onClick={(e) => handleDelete(e, item)}
                        title="삭제"
                    >
                        <Trash2 className="size-3.5" />
                    </button>
                </div>
            ))}
        </div>
    );
};

export default SessionQuestions;
