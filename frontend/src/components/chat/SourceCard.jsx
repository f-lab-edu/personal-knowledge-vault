import { FileText } from 'lucide-react';

const SourceCard = ({ source, onClick }) => {
    return (
        <div
            onClick={onClick}
            className="group relative border border-[var(--color-border-light)] rounded-md p-3 bg-white cursor-pointer w-[220px] shrink-0 transition-all hover:border-black/20 hover:shadow-sm"
        >
            <div className="flex items-center gap-2 mb-2">
                <FileText className="size-3.5 text-[var(--color-tertiary)] transition-colors group-hover:text-foreground" />
                <span className="text-xs font-bold text-muted-foreground truncate transition-colors group-hover:text-foreground" title={source.fileName}>
                    {source.fileName}
                </span>
            </div>

            <div className="text-[0.625rem] text-muted-foreground leading-relaxed opacity-90 mb-3 line-clamp-3">
                &ldquo;{source.snippet}&rdquo;
            </div>

            <div className="flex items-center">
                <span className="text-[0.5625rem] font-semibold uppercase tracking-widest text-[var(--color-tertiary)] border border-[var(--color-border-light)] px-2 py-1 rounded-sm">
                    P. {source.pageNumber}
                </span>
            </div>
        </div>
    );
};

export default SourceCard;
