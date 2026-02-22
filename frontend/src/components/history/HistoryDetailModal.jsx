import { X } from 'lucide-react';
import {
    Dialog,
    DialogContent,
    DialogTitle,
} from '@/components/ui/dialog';
import { useHistoryDetail } from '@/hooks/useHistory';
import SourceCard from '@/components/chat/SourceCard';
import { STATUS_LABEL } from '@/utils/constants';
import { formatDate } from '@/utils/format';

const HistoryDetailModal = ({ historyId, onClose }) => {
    const { data: detail, isLoading } = useHistoryDetail(historyId);

    return (
        <Dialog open={!!historyId} onOpenChange={(o) => { if (!o) onClose(); }}>
            <DialogContent className="max-w-[560px]" showCloseButton={false}>
                {isLoading ? (
                    <div className="text-sm text-[var(--color-tertiary)] p-6 text-center">불러오는 중...</div>
                ) : detail ? (
                    <>
                        <div className="flex justify-between items-start mb-4">
                            <DialogTitle className="text-lg font-bold text-foreground leading-snug flex-1 mr-3">
                                {detail.question}
                            </DialogTitle>
                            <button
                                className="flex items-center justify-center w-7 h-7 rounded-sm text-[var(--color-tertiary)] cursor-pointer shrink-0 transition-all hover:bg-black/5 hover:text-foreground"
                                onClick={onClose}
                            >
                                <X className="size-3.5" />
                            </button>
                        </div>

                        <div className="flex gap-3 text-[0.625rem] text-[var(--color-tertiary)] mb-4">
                            <span>{STATUS_LABEL[detail.status] || detail.status}</span>
                            <span>{formatDate(detail.createdAt)}</span>
                        </div>

                        <div className="max-h-[60vh] overflow-y-auto">
                            <div className="mb-5">
                                <div className="text-xs font-bold text-[var(--color-tertiary)] uppercase tracking-widest mb-2">답변</div>
                                <div className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{detail.answer}</div>
                            </div>

                            {detail.sources?.length > 0 && (
                                <div className="mt-4">
                                    <div className="text-xs font-bold text-[var(--color-tertiary)] uppercase tracking-widest mb-2">참고 문서</div>
                                    <div className="flex flex-col gap-2">
                                        {detail.sources.map((source, idx) => (
                                            <div key={idx} className="w-full">
                                                <SourceCard source={source} />
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </>
                ) : null}
            </DialogContent>
        </Dialog>
    );
};

export default HistoryDetailModal;
