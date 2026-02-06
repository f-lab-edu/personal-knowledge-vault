/**
 * 업로드된 파일 목록. 상태 뱃지 표시
 */
import React from 'react';
import { clsx } from 'clsx';
import styles from './FileList.module.css';

// Mock Data
const MOCK_FILES = [
    { id: 1, name: 'Design_Patterns.pdf', size: '12MB', status: 'completed' },
    { id: 2, name: 'Project_Requirements.md', size: '45KB', status: 'embedding' },
    { id: 3, name: 'Legacy_Code_Analysis.txt', size: '1.2MB', status: 'failed' },
    { id: 4, name: 'Deployment_Guide.pdf', size: '3.4MB', status: 'completed' },
];

const StatusBadge = ({ status }) => {
    const statusClass = {
        completed: styles.statusCompleted,
        embedding: styles.statusEmbedding,
        failed: styles.statusFailed,
        pending: styles.statusPending,
    };

    const labels = {
        completed: '준비됨',
        embedding: '처리 중',
        failed: '실패',
        pending: '대기 중',
    };

    return (
        <span className={clsx(styles.badge, statusClass[status])}>
            {labels[status]}
        </span>
    );
};

const FileList = () => {
    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <span>문서</span>
                <span>상태</span>
            </div>

            {MOCK_FILES.map((file) => (
                <div key={file.id} className={styles.fileItem}>
                    <div className={styles.fileInfo}>
                        <svg
                            className={clsx(styles.icon, file.name.endsWith('.pdf') && styles.iconPdf)}
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                        >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        <div className={styles.details}>
                            <span className={styles.fileName}>{file.name}</span>
                            <span className={styles.fileSize}>{file.size}</span>
                        </div>
                    </div>

                    <div className={styles.statusContainer}>
                        <StatusBadge status={file.status} />
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FileList;
