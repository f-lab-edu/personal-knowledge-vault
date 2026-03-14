import { useEffect, useRef } from 'react';
import AnswerBubble from './AnswerBubble';

const UserMessage = ({ content }) => (
    <div className="flex justify-end animate-[fadeIn_0.3s_ease]">
        <div className="bg-accent text-foreground px-5 py-4 rounded-xl rounded-tr-sm max-w-[80%] text-sm leading-relaxed shadow-sm">
            {content}
        </div>
    </div>
);

const SystemMessage = ({ content }) => (
    <div className="flex justify-center animate-[fadeIn_0.3s_ease]">
        <div className="text-sm text-[var(--color-tertiary)] text-center px-6 py-3 border-y w-full">
            {content}
        </div>
    </div>
);

const MessageList = ({ messages }) => {
    const bottomRef = useRef(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    return (
        <div className="flex flex-col gap-10 pb-4">
            {messages.map((message) => (
                <div key={message.id}>
                    {message.role === 'user' ? (
                        <UserMessage content={message.content} />
                    ) : message.role === 'system' ? (
                        <SystemMessage content={message.content} />
                    ) : (
                        <AnswerBubble content={message.content} citations={message.citations} />
                    )}
                </div>
            ))}
            <div ref={bottomRef} />
        </div>
    );
};

export default MessageList;
