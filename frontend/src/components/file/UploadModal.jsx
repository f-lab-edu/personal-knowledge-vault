import { useState, useRef } from 'react';
import { cn } from '@/lib/utils';
import { FileText, UploadCloud } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
    DialogClose,
} from '@/components/ui/dialog';
import { useUploadSource } from '@/hooks/useSource';
import { formatFileSize } from '@/utils/format';
import { validateFile, ACCEPT_EXTENSIONS } from '@/utils/validation';
import { getErrorMessage } from '@/utils/error';
import { toast } from 'sonner';

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

    const handleUpload = () => {
        if (!selectedFile || uploading) return;

        const normalizedName = selectedFile.name.normalize('NFC');
        const fileToUpload = normalizedName !== selectedFile.name
            ? new File([selectedFile], normalizedName, { type: selectedFile.type })
            : selectedFile;

        uploadMutation.mutate(
            { file: fileToUpload, onProgress: setProgress },
            {
                onSuccess: () => {
                    resetState();
                    onOpenChange(false);
                },
                onError: (error) => {
                    toast.error(getErrorMessage(error, '업로드에 실패했습니다.'));
                },
            },
        );
    };

    return (
        <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogContent className="max-w-[450px]" showCloseButton={false}>
                <DialogHeader>
                    <DialogTitle>문서 업로드</DialogTitle>
                    <DialogDescription>
                        PDF, TXT, 또는 MD 파일을 업로드하세요. 파일당 최대 30MB.
                    </DialogDescription>
                </DialogHeader>

                <input
                    ref={fileInputRef}
                    type="file"
                    accept={ACCEPT_EXTENSIONS}
                    onChange={handleInputChange}
                    className="hidden"
                />

                <div
                    className={cn(
                        "border-2 border-dashed border-[var(--color-border-light)] rounded-md p-8 text-center mb-2 cursor-pointer transition-all hover:bg-muted hover:border-border",
                        isDragging && "bg-muted border-primary"
                    )}
                    onClick={handleDropzoneClick}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                >
                    {selectedFile ? (
                        <div className="text-[var(--color-tertiary)] mb-2">
                            <FileText className="size-10 mx-auto mb-2 opacity-50" />
                            <span className="text-sm font-medium block">{selectedFile.name}</span>
                            <p className="text-xs text-[var(--color-tertiary)]">{formatFileSize(selectedFile.size)}</p>
                        </div>
                    ) : (
                        <div className="text-[var(--color-tertiary)] mb-2">
                            <UploadCloud className="size-10 mx-auto mb-2 opacity-50" />
                            <span className="text-sm font-medium block">클릭하여 선택하거나 파일을 여기로 드래그하세요</span>
                            <p className="text-xs text-[var(--color-tertiary)]">지원 형식: .pdf, .txt, .md</p>
                        </div>
                    )}
                </div>

                {uploading && (
                    <div className="mb-2">
                        <div className="flex justify-between text-xs mb-1 text-muted-foreground">
                            <span>업로드 중...</span>
                            <span>{progress}%</span>
                        </div>
                        <Progress value={progress} />
                    </div>
                )}

                <DialogFooter>
                    <DialogClose asChild>
                        <Button variant="ghost" disabled={uploading}>취소</Button>
                    </DialogClose>
                    <Button
                        onClick={handleUpload}
                        disabled={!selectedFile || uploading}
                    >
                        {uploading ? '처리 중...' : '업로드'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default UploadModal;
