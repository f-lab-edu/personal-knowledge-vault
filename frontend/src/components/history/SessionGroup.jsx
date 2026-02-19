import { useState } from 'react';
import SessionQuestions from './SessionQuestions';

const SessionGroup = ({ session, isActive, onSelectSession, onSelectHistory }) => {
    const [expanded, setExpanded] = useState(isActive);
    const isExpanded = isActive || expanded;

    const handleSelectSession = () => {
        setExpanded((prev) => !prev);
        onSelectSession?.(session.sessionId);
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
                onClick={handleSelectSession}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleSelectSession();
                    }
                }}
            >
                <span className="flex justify-between items-center flex-1 min-w-0">
                    <span>{isExpanded ? '▾' : '▸'} {session.title}</span>
                    <span className="text-[0.625rem] font-normal text-[var(--color-tertiary)]">{session.questionCount}개 질문</span>
                </span>
            </div>
            {isExpanded && (
                <SessionQuestions
                    sessionId={session.sessionId}
                    onSelectHistory={onSelectHistory}
                />
            )}
        </div>
    );
};

export default SessionGroup;
