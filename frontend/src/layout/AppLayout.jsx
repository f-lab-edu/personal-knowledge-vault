/**
 * 메인 3-Column 레이아웃 (사이드바 + 채팅 + 히스토리)
 */
import React, { useState } from 'react';
import styles from './AppLayout.module.css';
import Sidebar from './Sidebar';
import ChatArea from './ChatArea';
import HistoryPanel from './HistoryPanel';

const AppLayout = () => {
    const [isHistoryOpen, setIsHistoryOpen] = useState(true);

    const toggleHistory = () => {
        setIsHistoryOpen(!isHistoryOpen);
    };

    return (
        <div className={styles.container}>
            <aside className={styles.sidebar}>
                <Sidebar />
            </aside>
            <main className={styles.main}>
                <ChatArea />
            </main>
            <aside className={`${styles.history} ${!isHistoryOpen ? styles.historyClosed : ''}`}>
                <HistoryPanel isOpen={isHistoryOpen} onToggle={toggleHistory} />
            </aside>
            {!isHistoryOpen && (
                <button
                    className={styles.historyToggleCollapsed}
                    onClick={toggleHistory}
                    aria-label="기록 패널 열기"
                >
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <path d="M10 12L6 8L10 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                </button>
            )}
        </div>
    );
};

export default AppLayout;
