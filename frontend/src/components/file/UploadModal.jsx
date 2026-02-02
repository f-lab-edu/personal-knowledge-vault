/**
 * 문서 업로드 모달. 진행률 표시
 */
import React, { useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import * as Progress from '@radix-ui/react-progress';
import styles from './UploadModal.module.css';
import Button from '../ui/Button';

const UploadModal = ({ open, onOpenChange }) => {
    const [uploading, setUploading] = useState(false);
    const [progress, setProgress] = useState(0);

    const handleUpload = () => {
        setUploading(true);
        let p = 0;
        const interval = setInterval(() => {
            p += 10;
            setProgress(p);
            if (p >= 100) {
                clearInterval(interval);
                setTimeout(() => {
                    setUploading(false);
                    setProgress(0);
                    onOpenChange(false);
                }, 500);
            }
        }, 200);
    };

    return (
        <Dialog.Root open={open} onOpenChange={onOpenChange}>
            <Dialog.Portal>
                <Dialog.Overlay className={styles.overlay} />
                <Dialog.Content className={styles.content}>
                    <Dialog.Title className={styles.title}>
                        문서 업로드
                    </Dialog.Title>
                    <Dialog.Description className={styles.description}>
                        PDF, TXT, 또는 MD 파일을 업로드하세요. 파일당 최대 30MB.
                    </Dialog.Description>

                    {/* Dropzone Area */}
                    <div className={styles.dropzone}>
                        <div className={styles.dropzoneContent}>
                            <svg className={styles.dropzoneIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                            </svg>
                            <span className={styles.dropzoneText}>클릭하여 선택하거나 파일을 여기로 드래그하세요</span>
                        </div>
                        <p className={styles.dropzoneHint}>지원 형식: .pdf, .txt, .md</p>
                    </div>

                    {/* Progress Bar */}
                    {uploading && (
                        <div className={styles.progressWrapper}>
                            <div className={styles.progressLabel}>
                                <span>업로드 중...</span>
                                <span>{progress}%</span>
                            </div>
                            <Progress.Root className={styles.progressRoot} value={progress}>
                                <Progress.Indicator
                                    className={styles.progressIndicator}
                                    style={{ transform: `translateX(-${100 - progress}%)` }}
                                />
                            </Progress.Root>
                        </div>
                    )}

                    <div className={styles.actions}>
                        <Dialog.Close asChild>
                            <Button variant="ghost" disabled={uploading}>취소</Button>
                        </Dialog.Close>
                        <Button onClick={handleUpload} disabled={uploading}>
                            {uploading ? '처리 중...' : '업로드'}
                        </Button>
                    </div>
                </Dialog.Content>
            </Dialog.Portal>
        </Dialog.Root>
    );
};

export default UploadModal;
