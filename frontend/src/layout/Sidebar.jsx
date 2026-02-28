import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import FileList from '@/components/file/FileList';
import UploadModal from '@/components/file/UploadModal';
import { useAuth } from '@/hooks/useAuth';
import { useSources } from '@/hooks/useSource';
import { toast } from 'sonner';
import { confirm } from '@/stores/confirmStore';
import { MAX_FILES, MAX_STORAGE_MB } from '@/utils/constants';

const Sidebar = () => {
    const { logout, withdraw, isLoggingOut, isWithdrawing } = useAuth();
    const { data: sources = [] } = useSources();
    const navigate = useNavigate();
    const [isUploadOpen, setIsUploadOpen] = useState(false);

    const fileCount = sources.length;
    const totalBytes = sources.reduce((sum, s) => sum + (s.fileSize || 0), 0);
    const totalMB = totalBytes / (1024 * 1024);
    const usageText = `${fileCount}/${MAX_FILES} 파일 사용 중 (${totalMB < 1 ? totalMB.toFixed(1) : Math.round(totalMB)}MB/${MAX_STORAGE_MB}MB)`;

    const handleLogout = async () => {
        if (isLoggingOut) return;

        try {
            await logout();
            navigate('/login', { replace: true });
        } catch {
            toast.error('로그아웃에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        }
    };

    const handleWithdraw = async () => {
        if (isWithdrawing) return;

        const confirmed = await confirm({
            title: '회원 탈퇴',
            description: '정말 탈퇴하시겠습니까?\n탈퇴 후에는 복구할 수 없습니다.',
            variant: 'danger',
        });
        if (!confirmed) return;

        try {
            await withdraw();
            navigate('/login', { replace: true });
        } catch {
            toast.error('회원 탈퇴에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        }
    };

    return (
        <div className="flex flex-col h-full p-5 bg-muted/50">
            <div className="mb-8">
                <h2 className="font-bold text-xl text-foreground mb-1 tracking-tight">내 보관함</h2>
                <p className="text-xs text-[var(--color-tertiary)] font-medium uppercase tracking-widest">{usageText}</p>
            </div>

            <div className="mb-6">
                <Button className="w-full" onClick={() => setIsUploadOpen(true)}>
                    <Plus className="size-4 mr-2" />
                    문서 추가
                </Button>
            </div>

            <div className="flex-1 overflow-y-auto">
                <FileList />
            </div>

            <div className="mt-auto pt-4 border-t">
                <div className="flex flex-col gap-2">
                    <Button
                        variant="ghost"
                        className="w-full justify-start"
                        onClick={handleLogout}
                        disabled={isLoggingOut || isWithdrawing}
                    >
                        <LogOut className="size-4 mr-2" />
                        {isLoggingOut ? '로그아웃 중...' : '로그아웃'}
                    </Button>
                    <Button
                        variant="ghost"
                        className="w-full justify-start text-[var(--color-error)] hover:text-[var(--color-error)] hover:bg-[rgba(211,47,47,0.08)]"
                        onClick={handleWithdraw}
                        disabled={isLoggingOut || isWithdrawing}
                    >
                        {isWithdrawing ? '탈퇴 처리 중...' : '회원 탈퇴'}
                    </Button>
                </div>
            </div>

            <UploadModal open={isUploadOpen} onOpenChange={setIsUploadOpen} />
        </div>
    );
};

export default Sidebar;
