import { FileText } from 'lucide-react';

const CitationCard = ({ citation, onClick }) => {
    const fileName = citation?.fileName || '알 수 없는 문서';
    const snippet = citation?.snippet || '';
    const pageText = citation?.pageNumber == null ? '-' : String(citation.pageNumber);

    return (
        <div
            onClick={onClick}
            className="group min-w-[220px] max-w-[260px] border rounded-lg p-4 bg-background shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md cursor-pointer"
        >
            <div className="flex items-center gap-2 mb-2">
                <FileText className="size-3.5 text-[var(--color-tertiary)] transition-colors group-hover:text-foreground" />
                <span className="text-xs font-bold text-muted-foreground truncate transition-colors group-hover:text-foreground" title={fileName}>
                    {fileName}
                </span>
            </div>

            <div className="text-[0.625rem] text-muted-foreground leading-relaxed opacity-90 mb-3 line-clamp-3">
                &ldquo;{snippet}&rdquo;
            </div>

            <div className="flex items-center">
                <span className="text-[0.5625rem] font-semibold uppercase tracking-widest text-[var(--color-tertiary)] border border-[var(--color-border-light)] px-2 py-1 rounded-sm">
                    P. {pageText}
                </span>
            </div>
        </div>
    );
};

export default CitationCard;
