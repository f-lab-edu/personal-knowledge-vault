import { create } from 'zustand';

let id = 0;

const useToastStore = create((set) => ({
    toasts: [],
    add: (variant, message) => {
        const toastId = ++id;
        set((state) => ({
            toasts: [...state.toasts, { id: toastId, variant, message }],
        }));
    },
    remove: (toastId) => {
        set((state) => ({
            toasts: state.toasts.filter((t) => t.id !== toastId),
        }));
    },
}));

export const toast = {
    error: (message) => useToastStore.getState().add('error', message),
    success: (message) => useToastStore.getState().add('success', message),
    info: (message) => useToastStore.getState().add('info', message),
};

export default useToastStore;
