import { useThreadTurns } from '@/hooks/useThread';
import { TURN_STATUS_LABEL } from '@/utils/constants';

const ThreadTurns = ({ threadId, onSelectTurn }) => {
    const { data: turns = [], isLoading } = useThreadTurns(threadId);

    if (isLoading) {
        return <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">불러오는 중...</div>;
    }

    if (turns.length === 0) {
        return null;
    }

    return (
        <div className="flex flex-col gap-1">
            {turns.map((turn) => (
                <div
                    key={turn.turnId}
                    className="group flex items-center text-left px-3 py-2 rounded-sm transition-all w-full cursor-pointer hover:bg-white hover:shadow-sm"
                    onClick={() => onSelectTurn(turn.turnId)}
                >
                    <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-foreground truncate mb-1 transition-colors group-hover:text-primary">
                            {turn.prompt}
                        </div>
                        <div className="text-[0.625rem] text-[var(--color-tertiary)]">
                            {TURN_STATUS_LABEL[turn.status] || turn.status}
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default ThreadTurns;
