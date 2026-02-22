/**
 * 질문 입력폼. 엔터로 전송, 빈 값 방지
 */
import React, { useState } from 'react';
import styles from './ChatInput.module.css';
import Button from '../ui/Button';

const ChatInput = ({ onSend, disabled }) => {
    const [text, setText] = useState('');

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!text.trim() || disabled) return;
        onSend(text);
        setText('');
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit(e);
        }
    };

    return (
        <div className={styles.container}>
            <form
                onSubmit={handleSubmit}
                className={styles.form}
            >
                <textarea
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="문서에 대해 무엇이든 물어보세요..."
                    disabled={disabled}
                    className={styles.textarea}
                    rows={1}
                    style={{ minHeight: '60px' }}
                />
                <div className={styles.buttonWrapper}>
                    <Button
                        type="submit"
                        size="sm"
                        variant="primary"
                        disabled={!text.trim() || disabled}
                        className={styles.submitButton}
                    >
                        <svg width="14" height="14" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 12h14M12 5l7 7-7 7" />
                        </svg>
                    </Button>
                </div>
            </form>
            <div className={styles.disclaimer}>
                <span className={styles.disclaimerText}>
                    AI 생성 콘텐츠에는 오류가 포함될 수 있습니다
                </span>
            </div>
        </div>
    );
};

export default ChatInput;
