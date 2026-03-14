import { useState } from 'react';
import ThreadTurns from './ThreadTurns';

const ThreadGroup = ({ thread, isActive, onSelectThread, onSelectTurn }) => {
    const [expanded, setExpanded] = useState(isActive);
    const isExpanded = isActive || expanded;

    const handleSelectThread = () => {
        setExpanded((prev) => !prev);
        onSelectThread?.(thread.threadId);
    };

    return (
        <div className="mb-6">
            <div
                className={[
                    'group flex items-center w-full text-xs font-bold text-[var(--color-tertiary)] tracking-wide mb-2 px-2 py-2 rounded-sm text-left cursor-pointer transition-colors hover:bg-black/[0.03]',
                    isActive ? 'bg-black/[0.04] text-foreground' : '',
                ].join(' ')}
                role="button"
                tabIndex={0}
                onClick={handleSelectThread}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleSelectThread();
                    }
                }}
            >
                <span className="flex justify-between items-center flex-1 min-w-0 gap-3">
                    <span className="truncate">{isExpanded ? '▾' : '▸'} {thread.title}</span>
                    <span className="text-[0.625rem] font-normal text-[var(--color-tertiary)] whitespace-nowrap">{thread.turnCount}개 질문</span>
                </span>
            </div>
            {isExpanded && (
                <ThreadTurns
                    threadId={thread.threadId}
                    onSelectTurn={onSelectTurn}
                />
            )}
        </div>
    );
};

export default ThreadGroup;
