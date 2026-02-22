/**
 * 답변 출처 정보 (파일명, 페이지, 발췌문)
 */
import React from 'react';
import styles from './SourceCard.module.css';

const SourceCard = ({ source, onClick }) => {
    return (
        <div onClick={onClick} className={styles.card}>
            <div className={styles.header}>
                <svg className={styles.icon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span className={styles.fileName} title={source.fileName}>
                    {source.fileName}
                </span>
            </div>

            <div className={styles.snippet}>
                "{source.snippet}"
            </div>

            <div className={styles.footer}>
                <span className={styles.pageBadge}>
                    P. {source.pageNumber}
                </span>
            </div>
        </div>
    );
};

export default SourceCard;
