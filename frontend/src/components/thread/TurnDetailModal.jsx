import { X } from 'lucide-react';
import {
    Dialog,
    DialogContent,
    DialogTitle,
} from '@/components/ui/dialog';
import { useTurnDetail } from '@/hooks/useThread';
import CitationCard from '@/components/chat/CitationCard';
import { TURN_STATUS_LABEL } from '@/utils/constants';
import { formatDate } from '@/utils/format';

const TurnDetailModal = ({ threadId, turnId, onClose }) => {
    const { data: detail, isLoading } = useTurnDetail(threadId, turnId);
    const isOpen = !!threadId && !!turnId;

    return (
        <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
            <DialogContent className="max-w-[560px]" showCloseButton={false}>
                {isLoading ? (
                    <div className="text-sm text-[var(--color-tertiary)] p-6 text-center">불러오는 중...</div>
                ) : detail ? (
                    <>
                        <div className="flex justify-between items-start mb-4">
                            <DialogTitle className="text-lg font-bold text-foreground leading-snug flex-1 mr-3">
                                {detail.prompt}
                            </DialogTitle>
                            <button
                                className="flex items-center justify-center w-7 h-7 rounded-sm text-[var(--color-tertiary)] cursor-pointer shrink-0 transition-all hover:bg-black/5 hover:text-foreground"
                                onClick={onClose}
                            >
                                <X className="size-3.5" />
                            </button>
                        </div>

                        <div className="flex gap-3 text-[0.625rem] text-[var(--color-tertiary)] mb-4">
                            <span>{TURN_STATUS_LABEL[detail.status] || detail.status}</span>
                            <span>{formatDate(detail.createdAt)}</span>
                        </div>

                        <div className="max-h-[60vh] overflow-y-auto">
                            <div className="mb-5">
                                <div className="text-xs font-bold text-[var(--color-tertiary)] uppercase tracking-widest mb-2">답변</div>
                                <div className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{detail.answer}</div>
                            </div>

                            {detail.citations?.length > 0 && (
                                <div className="mt-4">
                                    <div className="text-xs font-bold text-[var(--color-tertiary)] uppercase tracking-widest mb-2">인용</div>
                                    <div className="flex flex-col gap-2">
                                        {detail.citations.map((citation, index) => (
                                            <div key={index} className="w-full">
                                                <CitationCard citation={citation} />
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

export default TurnDetailModal;
