import { cn } from '@/lib/utils';
import ChatInput from '@/components/chat/ChatInput';
import MessageList from '@/components/chat/MessageList';

const ChatArea = ({
    sessionId,
    messages,
    loading,
    sessionEnded,
    isRestoring,
    onSend,
    onStartNewSession,
}) => {
    return (
        <div className="flex flex-col h-full w-full relative">
            {(messages.length > 0 || sessionId) && (
                <div className="flex justify-end items-center shrink-0 px-4 py-3 border-b">
                    <button
                        className="px-4 py-2 text-sm font-medium text-muted-foreground bg-muted border rounded-md cursor-pointer transition-all hover:text-foreground hover:bg-accent"
                        onClick={onStartNewSession}
                    >
                        새 대화
                    </button>
                </div>
            )}

            <div className="flex-1 overflow-y-auto px-4 py-8">
                <div className="max-w-[800px] mx-auto">
                    {isRestoring ? (
                        <div className="text-sm text-[var(--color-tertiary)] px-2 py-2">대화를 불러오는 중...</div>
                    ) : messages.length === 0 ? (
                        <div className={cn('text-center mt-[20vh]', 'animate-fade-in')}>
                            <h1 className="text-[2rem] font-bold text-foreground mb-4 tracking-tight">
                                좋은 아침입니다.
                            </h1>
                            <p className="text-lg text-muted-foreground">
                                오늘 어떤 문서를 찾아드릴까요?
                            </p>
                        </div>
                    ) : (
                        <MessageList messages={messages} />
                    )}

                    {loading && (
                        <div className="flex items-center gap-2 mt-4 ml-8 text-sm text-[var(--color-tertiary)] animate-pulse">
                            <span>생각 중...</span>
                        </div>
                    )}
                </div>
            </div>

            <div className="shrink-0 px-4 pt-10 pb-6 bg-gradient-to-t from-background from-70% to-transparent z-10">
                <ChatInput onSend={onSend} disabled={loading || sessionEnded || isRestoring} />
            </div>
        </div>
    );
};

export default ChatArea;
