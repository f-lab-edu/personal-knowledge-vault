import { useState, useRef } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import * as Progress from '@radix-ui/react-progress';
import { clsx } from 'clsx';
import styles from './UploadModal.module.css';
import Button from '../ui/Button';
import { useUploadSource } from '../../hooks/useSource';
import { formatFileSize } from '../../utils/format';
import { validateFile, ACCEPT_EXTENSIONS } from '../../utils/validation';
import { getErrorMessage } from '../../utils/error';
import { toast } from '../../stores/toastStore';

const UploadModal = ({ open, onOpenChange }) => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [progress, setProgress] = useState(0);
    const [isDragging, setIsDragging] = useState(false);
    const fileInputRef = useRef(null);
    const uploadMutation = useUploadSource();

    const uploading = uploadMutation.isPending;

    const resetState = () => {
        setSelectedFile(null);
        setProgress(0);
        setIsDragging(false);
    };

    const handleOpenChange = (nextOpen) => {
        if (!nextOpen) {
            resetState();
        }
        onOpenChange(nextOpen);
    };

    const handleFileSelect = (file) => {
        if (!file) return;

        const error = validateFile(file);
        if (error) {
            toast.error(error);
            return;
        }

        setSelectedFile(file);
    };

    const handleInputChange = (e) => {
        handleFileSelect(e.target.files?.[0]);
        e.target.value = '';
    };

    const handleDropzoneClick = () => {
        if (!uploading) {
            fileInputRef.current?.click();
        }
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        setIsDragging(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        setIsDragging(false);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setIsDragging(false);
        handleFileSelect(e.dataTransfer.files?.[0]);
    };

    const handleUpload = async () => {
        if (!selectedFile || uploading) return;

        try {
            const normalizedName = selectedFile.name.normalize('NFC');
            const fileToUpload = normalizedName !== selectedFile.name
                ? new File([selectedFile], normalizedName, { type: selectedFile.type })
                : selectedFile;

            await uploadMutation.mutateAsync({
                file: fileToUpload,
                onProgress: setProgress,
            });
            resetState();
            onOpenChange(false);
        } catch (error) {
            toast.error(getErrorMessage(error, '업로드에 실패했습니다.'));
        }
    };

    return (
        <Dialog.Root open={open} onOpenChange={handleOpenChange}>
            <Dialog.Portal>
                <Dialog.Overlay className={styles.overlay} />
                <Dialog.Content className={styles.content}>
                    <Dialog.Title className={styles.title}>
                        문서 업로드
                    </Dialog.Title>
                    <Dialog.Description className={styles.description}>
                        PDF, TXT, 또는 MD 파일을 업로드하세요. 파일당 최대 30MB.
                    </Dialog.Description>

                    <input
                        ref={fileInputRef}
                        type="file"
                        accept={ACCEPT_EXTENSIONS}
                        onChange={handleInputChange}
                        style={{ display: 'none' }}
                    />

                    <div
                        className={clsx(styles.dropzone, isDragging && styles.dropzoneDragging)}
                        onClick={handleDropzoneClick}
                        onDragOver={handleDragOver}
                        onDragLeave={handleDragLeave}
                        onDrop={handleDrop}
                    >
                        {selectedFile ? (
                            <div className={styles.dropzoneContent}>
                                <svg className={styles.dropzoneIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                </svg>
                                <span className={styles.dropzoneText}>{selectedFile.name}</span>
                                <p className={styles.dropzoneHint}>{formatFileSize(selectedFile.size)}</p>
                            </div>
                        ) : (
                            <div className={styles.dropzoneContent}>
                                <svg className={styles.dropzoneIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                                </svg>
                                <span className={styles.dropzoneText}>클릭하여 선택하거나 파일을 여기로 드래그하세요</span>
                                <p className={styles.dropzoneHint}>지원 형식: .pdf, .txt, .md</p>
                            </div>
                        )}
                    </div>

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
                        <Button
                            onClick={handleUpload}
                            disabled={!selectedFile || uploading}
                        >
                            {uploading ? '처리 중...' : '업로드'}
                        </Button>
                    </div>
                </Dialog.Content>
            </Dialog.Portal>
        </Dialog.Root>
    );
};

export default UploadModal;
