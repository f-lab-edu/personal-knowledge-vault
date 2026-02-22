import { useState } from 'react';
import SessionGroup from './SessionGroup';
import { useSessionList } from '@/hooks/useHistory';
import HistoryDetailModal from './HistoryDetailModal';

const HistoryList = () => {
    const { data: sessions, isLoading } = useSessionList();
    const [selectedHistoryId, setSelectedHistoryId] = useState(null);

    if (isLoading) {
        return (
            <div className="flex flex-col h-full overflow-y-auto">
                <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">불러오는 중...</div>
            </div>
        );
    }

    if (!sessions || sessions.length === 0) {
        return (
            <div className="flex flex-col h-full overflow-y-auto">
                <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">히스토리가 없습니다.</div>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-full overflow-y-auto">
            {sessions.map((session) => (
                <SessionGroup
                    key={session.sessionId}
                    session={session}
                    onSelectHistory={setSelectedHistoryId}
                />
            ))}
            <HistoryDetailModal
                historyId={selectedHistoryId}
                onClose={() => setSelectedHistoryId(null)}
            />
        </div>
    );
};

export default HistoryList;
