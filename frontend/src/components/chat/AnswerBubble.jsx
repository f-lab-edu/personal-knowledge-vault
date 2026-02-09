import { Zap } from 'lucide-react';
import SourceCard from './SourceCard';

const AnswerBubble = ({ content, sources }) => {
    return (
        <div className="flex flex-col gap-3 animate-fade-in">
            {/* Answer Badge */}
            <div className="flex items-center gap-2 mb-1">
                <div className="w-5 h-5 rounded-full bg-black flex items-center justify-center shadow-sm">
                    <Zap className="size-2.5 text-white" strokeWidth={2.5} />
                </div>
                <span className="text-xs font-bold uppercase tracking-widest text-foreground/90">
                    AI 답변
                </span>
            </div>

            {/* Answer Content */}
            <div className="pl-0 md:pl-7">
                <div className="text-base leading-[1.8] text-foreground tracking-tight antialiased selection:bg-[var(--color-selection)] selection:text-black">
                    {content}
                </div>

                {sources && sources.length > 0 && (
                    <div className="mt-8 pt-6 border-t border-[var(--color-border-light)]">
                        <h4 className="text-[0.625rem] font-bold uppercase tracking-widest text-[var(--color-tertiary)] mb-4 flex items-center gap-2">
                            참고 문서
                        </h4>
                        <div className="flex gap-3 overflow-x-auto pb-4 -ml-1 pl-1 scrollbar-hide [&::-webkit-scrollbar]:hidden">
                            {sources.map((source, index) => (
                                <SourceCard key={index} source={source} />
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AnswerBubble;
