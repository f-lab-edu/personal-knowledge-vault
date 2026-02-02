/**
 * 사용자 질문과 AI 답변 렌더링
 */
import React, { useEffect, useRef } from 'react';
import styles from './MessageList.module.css';
import AnswerBubble from './AnswerBubble';

const UserMessage = ({ content }) => (
    <div className={styles.userMessageWrapper}>
        <div className={styles.userMessageBubble}>
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
        <div className={styles.list}>
            {messages.map((msg) => (
                <div key={msg.id}>
                    {msg.role === 'user' ? (
                        <UserMessage content={msg.content} />
                    ) : (
                        <AnswerBubble content={msg.content} sources={msg.sources} />
                    )}
                </div>
            ))}
            <div ref={bottomRef} />
        </div>
    );
};

export default MessageList;
