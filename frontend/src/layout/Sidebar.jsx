/**
 * 좌측 사이드바. 파일 목록, 업로드, 로그아웃
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { clsx } from 'clsx';
import styles from './Sidebar.module.css';
import Button from '../components/ui/Button';
import FileList from '../components/file/FileList';
import UploadModal from '../components/file/UploadModal';
import { useAuth } from '../hooks/useAuth';
import { useSources } from '../hooks/useSource';
import { toast } from '../stores/toastStore';
import { confirm } from '../stores/confirmStore';

const Sidebar = () => {
    const { logout, withdraw, isLoggingOut, isWithdrawing } = useAuth();
    const { data: sources = [] } = useSources();
    const navigate = useNavigate();
    const [isUploadOpen, setIsUploadOpen] = useState(false);

    const fileCount = sources.length;
    const totalBytes = sources.reduce((sum, s) => sum + (s.fileSize || 0), 0);
    const totalMB = totalBytes / (1024 * 1024);
    const usageText = `${fileCount}/30 파일 사용 중 (${totalMB < 1 ? totalMB.toFixed(1) : Math.round(totalMB)}MB/300MB)`;

    const handleLogout = async () => {
        if (isLoggingOut) {
            return;
        }

        try {
            await logout();
            navigate('/login', { replace: true });
        } catch (error) {
            console.error(error);
            toast.error('로그아웃에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        }
    };

    const handleWithdraw = async () => {
        if (isWithdrawing) {
            return;
        }

        const confirmed = await confirm({
            title: '회원 탈퇴',
            description: '정말 탈퇴하시겠습니까?\n탈퇴 후에는 복구할 수 없습니다.',
            variant: 'danger',
        });
        if (!confirmed) {
            return;
        }

        try {
            await withdraw();
            navigate('/login', { replace: true });
        } catch (error) {
            console.error(error);
            toast.error('회원 탈퇴에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        }
    };

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h2 className={styles.title}>내 보관함</h2>
                <p className={styles.usage}>{usageText}</p>
            </div>

            <div className={styles.actionArea}>
                <Button
                    variant="primary"
                    isFullWidth
                    onClick={() => setIsUploadOpen(true)}
                >
                    <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" className={styles.buttonIcon}>
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    문서 추가
                </Button>
            </div>

            <div className={styles.listArea}>
                <FileList />
            </div>

            <div className={styles.footer}>
                <div className={styles.footerActions}>
                    <Button
                        variant="ghost"
                        isFullWidth
                        className={clsx(styles.footerButton)}
                        onClick={handleLogout}
                        disabled={isLoggingOut || isWithdrawing}
                    >
                        <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" className={styles.buttonIcon}>
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                        </svg>
                        {isLoggingOut ? '로그아웃 중...' : '로그아웃'}
                    </Button>
                    <Button
                        variant="ghost"
                        isFullWidth
                        className={clsx(styles.footerButton, styles.withdrawButton)}
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
