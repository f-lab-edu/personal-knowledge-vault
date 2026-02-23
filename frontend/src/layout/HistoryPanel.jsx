import HistoryList from '@/components/history/HistoryList';

const HistoryPanel = ({ activeSessionId, onSelectSession }) => {
    return (
        <div className="flex flex-col h-full p-5">
            <div className="mb-8">
                <h2 className="font-bold text-xl text-foreground mb-1 tracking-tight">기록</h2>
                <p className="text-xs text-[var(--color-tertiary)] font-medium uppercase tracking-widest">최근 대화</p>
            </div>

            <div className="flex-1 overflow-hidden">
                <HistoryList
                    activeSessionId={activeSessionId}
                    onSelectSession={onSelectSession}
                />
            </div>
        </div>
    );
};

export default HistoryPanel;
