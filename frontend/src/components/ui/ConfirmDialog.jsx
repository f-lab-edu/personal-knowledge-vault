import useConfirmStore from '@/stores/confirmStore';
import {
    AlertDialog,
    AlertDialogContent,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogCancel,
    AlertDialogAction,
} from '@/components/ui/alert-dialog';

const ConfirmDialog = () => {
    const { open, title, description, variant, accept, cancel } = useConfirmStore();

    return (
        <AlertDialog open={open} onOpenChange={(o) => { if (!o) cancel(); }}>
            <AlertDialogContent className="max-w-[380px]">
                <AlertDialogHeader>
                    <AlertDialogTitle>{title}</AlertDialogTitle>
                    {description && (
                        <AlertDialogDescription>{description}</AlertDialogDescription>
                    )}
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel variant="ghost" onClick={cancel}>취소</AlertDialogCancel>
                    <AlertDialogAction
                        variant={variant === 'danger' ? 'destructive' : 'default'}
                        onClick={accept}
                    >
                        확인
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
};

export default ConfirmDialog;
