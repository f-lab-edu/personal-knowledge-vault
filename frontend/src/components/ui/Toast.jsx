import * as ToastPrimitive from '@radix-ui/react-toast';
import { clsx } from 'clsx';
import useToastStore from '../../stores/toastStore';
import styles from './Toast.module.css';

const ToastItem = ({ toast: t, onRemove }) => (
    <ToastPrimitive.Root
        className={clsx(styles.root, styles[t.variant])}
        duration={5000}
        onOpenChange={(open) => {
            if (!open) onRemove(t.id);
        }}
    >
        <ToastPrimitive.Description className={styles.description}>
            {t.message}
        </ToastPrimitive.Description>
        <ToastPrimitive.Close className={styles.close} aria-label="닫기">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 6L6 18M6 6l12 12" />
            </svg>
        </ToastPrimitive.Close>
    </ToastPrimitive.Root>
);

const ToastContainer = () => {
    const toasts = useToastStore((s) => s.toasts);
    const remove = useToastStore((s) => s.remove);

    return (
        <ToastPrimitive.Provider swipeDirection="right">
            {toasts.map((t) => (
                <ToastItem key={t.id} toast={t} onRemove={remove} />
            ))}
            <ToastPrimitive.Viewport className={styles.viewport} />
        </ToastPrimitive.Provider>
    );
};

export default ToastContainer;
