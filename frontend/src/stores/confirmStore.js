import { create } from 'zustand';

const useConfirmStore = create((set) => ({
    open: false,
    title: '',
    description: '',
    variant: 'danger',
    resolve: null,

    show: ({ title, description, variant = 'danger' }) =>
        new Promise((resolve) => {
            set({ open: true, title, description, variant, resolve });
        }),

    accept: () =>
        set((state) => {
            state.resolve?.(true);
            return { open: false, resolve: null };
        }),

    cancel: () =>
        set((state) => {
            state.resolve?.(false);
            return { open: false, resolve: null };
        }),
}));

export const confirm = (opts) => useConfirmStore.getState().show(opts);

export default useConfirmStore;
