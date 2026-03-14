import { useState } from 'react';
import { ChevronLeft } from 'lucide-react';
import Sidebar from './Sidebar';
import ChatArea from './ChatArea';
import ThreadPanel from './ThreadPanel';
import { useThreadChat } from '@/hooks/useThreadChat';

const AppLayout = () => {
    const [isThreadPanelOpen, setIsThreadPanelOpen] = useState(true);
    const {
        threadId,
        messages,
        loading,
        threadEnded,
        isRestoring,
        startNewThread,
        selectThread,
        handleSend,
    } = useThreadChat();

    return (
        <div className="flex h-screen w-screen overflow-hidden bg-background">
            <aside className="w-[var(--lnb-width)] h-full border-r flex flex-col bg-muted">
                <Sidebar />
            </aside>
            <main className="flex-1 h-full flex flex-col relative">
                <ChatArea
                    threadId={threadId}
                    messages={messages}
                    loading={loading}
                    threadEnded={threadEnded}
                    isRestoring={isRestoring}
                    onSend={handleSend}
                    onStartNewThread={startNewThread}
                />
            </main>
            <aside className={isThreadPanelOpen
                ? 'w-[var(--rnb-width)] h-full border-l bg-muted'
                : 'hidden'}
            >
                <ThreadPanel
                    activeThreadId={threadId}
                    onSelectThread={selectThread}
                />
            </aside>
            {!isThreadPanelOpen && (
                <button
                    className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-md bg-muted border text-muted-foreground hover:text-foreground transition-colors"
                    onClick={() => setIsThreadPanelOpen(true)}
                    aria-label="기록 패널 열기"
                >
                    <ChevronLeft className="size-4 rotate-180" />
                </button>
            )}
        </div>
    );
};

export default AppLayout;
