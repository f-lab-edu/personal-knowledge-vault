import * as Dialog from '@radix-ui/react-dialog';
import useConfirmStore from '../../stores/confirmStore';
import Button from './Button';
import styles from './ConfirmDialog.module.css';

const ConfirmDialog = () => {
    const { open, title, description, variant, accept, cancel } = useConfirmStore();

    return (
        <Dialog.Root open={open} onOpenChange={(o) => { if (!o) cancel(); }}>
            <Dialog.Portal>
                <Dialog.Overlay className={styles.overlay} />
                <Dialog.Content className={styles.content}>
                    <Dialog.Title className={styles.title}>{title}</Dialog.Title>
                    {description && (
                        <Dialog.Description className={styles.description}>
                            {description}
                        </Dialog.Description>
                    )}
                    <div className={styles.actions}>
                        <Button variant="ghost" onClick={cancel}>취소</Button>
                        <Button variant={variant} onClick={accept}>확인</Button>
                    </div>
                </Dialog.Content>
            </Dialog.Portal>
        </Dialog.Root>
    );
};

export default ConfirmDialog;
