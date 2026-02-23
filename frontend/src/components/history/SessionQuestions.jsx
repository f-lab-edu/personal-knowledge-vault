import { useSessionDetail } from '@/hooks/useHistory';
import { STATUS_LABEL } from '@/utils/constants';

const SessionQuestions = ({ sessionId, onSelectHistory }) => {
    const { data: questions, isLoading } = useSessionDetail(sessionId);

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
                    key={item.chatHistoryId}
                    className="group flex items-center text-left px-3 py-2 rounded-sm transition-all w-full cursor-pointer hover:bg-white hover:shadow-sm"
                    onClick={() => onSelectHistory(item.chatHistoryId)}
                >
                    <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-foreground truncate mb-1 transition-colors group-hover:text-primary">
                            {item.question}
                        </div>
                        <div className="text-[0.625rem] text-[var(--color-tertiary)]">
                            {STATUS_LABEL[item.status] || item.status}
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default SessionQuestions;
