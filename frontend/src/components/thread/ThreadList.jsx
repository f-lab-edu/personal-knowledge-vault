import { useState } from 'react';
import ThreadGroup from './ThreadGroup';
import { useThreadList } from '@/hooks/useThread';
import TurnDetailModal from './TurnDetailModal';

const ThreadList = ({ activeThreadId, onSelectThread }) => {
    const { data: threads = [], isLoading } = useThreadList();
    const [selectedTurn, setSelectedTurn] = useState(null);

    if (isLoading) {
        return (
            <div className="flex flex-col h-full overflow-y-auto">
                <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">불러오는 중...</div>
            </div>
        );
    }

    if (threads.length === 0) {
        return (
            <div className="flex flex-col h-full overflow-y-auto">
                <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">기록이 없습니다.</div>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-full overflow-y-auto">
            {threads.map((thread) => (
                <ThreadGroup
                    key={thread.threadId}
                    thread={thread}
                    isActive={activeThreadId === thread.threadId}
                    onSelectThread={onSelectThread}
                    onSelectTurn={(turnId) => setSelectedTurn({ threadId: thread.threadId, turnId })}
                />
            ))}
            <TurnDetailModal
                threadId={selectedTurn?.threadId}
                turnId={selectedTurn?.turnId}
                onClose={() => setSelectedTurn(null)}
            />
        </div>
    );
};

export default ThreadList;
