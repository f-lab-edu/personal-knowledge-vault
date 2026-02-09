import { useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

const ChatInput = ({ onSend, disabled }) => {
    const [text, setText] = useState('');

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!text.trim() || disabled) return;
        onSend(text);
        setText('');
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit(e);
        }
    };

    return (
        <div className="w-full max-w-[800px] mx-auto">
            <form
                onSubmit={handleSubmit}
                className="relative flex items-end border rounded-xl bg-white shadow-sm transition-all focus-within:border-foreground focus-within:shadow-[0_0_0_1px_var(--color-foreground)]"
            >
                <textarea
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="문서에 대해 무엇이든 물어보세요..."
                    disabled={disabled}
                    className="w-full p-4 pr-12 bg-transparent border-none resize-none min-h-[60px] max-h-[200px] text-sm leading-relaxed text-foreground focus:outline-none placeholder:text-[var(--color-tertiary)]"
                    rows={1}
                />
                <div className="absolute right-3 bottom-3">
                    <Button
                        type="submit"
                        size="icon-sm"
                        disabled={!text.trim() || disabled}
                        className="rounded-md"
                    >
                        <ArrowRight className="size-3.5" />
                    </Button>
                </div>
            </form>
            <div className="text-center mt-3">
                <span className="text-[0.625rem] text-[var(--color-tertiary)] uppercase tracking-widest font-medium opacity-60">
                    AI 생성 콘텐츠에는 오류가 포함될 수 있습니다
                </span>
            </div>
        </div>
    );
};

export default ChatInput;
