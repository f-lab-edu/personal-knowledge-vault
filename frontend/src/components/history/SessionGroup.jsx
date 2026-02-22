import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import SessionQuestions from './SessionQuestions';
import { useDeleteSession } from '@/hooks/useHistory';
import { confirm } from '@/stores/confirmStore';
import { toast } from 'sonner';
import { getErrorMessage } from '@/utils/error';

const SessionGroup = ({ session, onSelectHistory }) => {
    const [expanded, setExpanded] = useState(false);
    const deleteMutation = useDeleteSession();

    const handleDeleteSession = async (e) => {
        e.stopPropagation();
        const confirmed = await confirm({
            title: '세션 삭제',
            description: `"${session.title}" 세션을 삭제하시겠습니까? 모든 질문이 함께 삭제됩니다.`,
            variant: 'danger',
        });
        if (!confirmed) return;

        deleteMutation.mutate(session.sessionId, {
            onError: (error) => {
                toast.error(getErrorMessage(error, '삭제에 실패했습니다.'));
            },
        });
    };

    return (
        <div className="mb-6">
            <div
                className="group flex items-center w-full text-xs font-bold text-[var(--color-tertiary)] tracking-wide mb-2 px-2 py-2 rounded-sm text-left cursor-pointer transition-colors hover:bg-black/[0.03]"
                role="button"
                tabIndex={0}
                onClick={() => setExpanded(prev => !prev)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setExpanded(prev => !prev); }}
            >
                <span className="flex justify-between items-center flex-1 min-w-0">
                    <span>{expanded ? '▾' : '▸'} {session.title}</span>
                    <span className="text-[0.625rem] font-normal text-[var(--color-tertiary)]">{session.questionCount}개 질문</span>
                </span>
                <button
                    className="flex items-center justify-center w-6 h-6 shrink-0 rounded-sm text-[var(--color-tertiary)] cursor-pointer opacity-0 transition-all group-hover:opacity-100 hover:text-[var(--color-error)] hover:bg-[rgba(211,47,47,0.08)]"
                    onClick={handleDeleteSession}
                    title="세션 삭제"
                >
                    <Trash2 className="size-3.5" />
                </button>
            </div>
            {expanded && <SessionQuestions sessionId={session.sessionId} onSelectHistory={onSelectHistory} />}
        </div>
    );
};

export default SessionGroup;
