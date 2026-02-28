/**
 * 우측 히스토리 패널. 최근 대화 목록
 */
import React from 'react';
import styles from './HistoryPanel.module.css';
import HistoryList from '../components/history/HistoryList';

const HistoryPanel = () => {
    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h2 className={styles.title}>기록</h2>
                <p className={styles.subtitle}>최근 대화</p>
            </div>

            <div className={styles.content}>
                <HistoryList />
            </div>
        </div>
    );
};

export default HistoryPanel;
