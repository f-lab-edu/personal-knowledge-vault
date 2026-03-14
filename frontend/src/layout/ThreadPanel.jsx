import ThreadList from '@/components/thread/ThreadList';

const ThreadPanel = ({ activeThreadId, onSelectThread }) => {
    return (
        <div className="flex flex-col h-full p-5">
            <div className="mb-8">
                <h2 className="font-bold text-xl text-foreground mb-1 tracking-tight">기록</h2>
                <p className="text-xs text-[var(--color-tertiary)] font-medium uppercase tracking-widest">최근 대화</p>
            </div>

            <div className="flex-1 overflow-hidden">
                <ThreadList
                    activeThreadId={activeThreadId}
                    onSelectThread={onSelectThread}
                />
            </div>
        </div>
    );
};

export default ThreadPanel;
