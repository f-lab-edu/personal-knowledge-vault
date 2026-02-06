/**
 * 좌측 사이드바. 파일 목록, 업로드, 로그아웃
 */
import React, { useState } from 'react';
import styles from './Sidebar.module.css';
import Button from '../components/ui/Button';
import FileList from '../components/file/FileList';
import UploadModal from '../components/file/UploadModal';

const Sidebar = () => {
    const [isUploadOpen, setIsUploadOpen] = useState(false);

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h2 className={styles.title}>내 보관함</h2>
                <p className={styles.usage}>3/30 파일 사용 중 (15MB/300MB)</p>
            </div>

            <div className={styles.actionArea}>
                <Button
                    variant="primary"
                    isFullWidth
                    onClick={() => setIsUploadOpen(true)}
                >
                    <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ marginRight: '8px' }}>
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    문서 추가
                </Button>
            </div>

            <div className={styles.listArea}>
                <FileList />
            </div>

            <div className={styles.footer}>
                <Button variant="ghost" isFullWidth style={{ justifyContent: 'flex-start' }}>
                    <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ marginRight: '8px' }}>
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    로그아웃
                </Button>
            </div>

            <UploadModal open={isUploadOpen} onOpenChange={setIsUploadOpen} />
        </div>
    );
};

export default Sidebar;
