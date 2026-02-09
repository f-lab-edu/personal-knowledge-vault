import { useAuth } from '@/hooks/useAuth';

const LoginPage = () => {
    const { login } = useAuth();

    return (
        <div className="flex flex-col items-center justify-center h-screen bg-background text-center px-4">
            <h1 className="font-bold text-foreground mb-6 tracking-tight text-[2rem] md:text-[2.5rem]">
                Personal Knowledge Vault
            </h1>
            <button
                onClick={login}
                className="group flex items-center gap-3 px-8 py-4 bg-white border border-[var(--color-border-light)] rounded-xl shadow-sm transition-all text-base font-medium text-foreground hover:shadow-md hover:border-border"
            >
                <svg className="w-5 h-5 transition-transform group-hover:scale-110" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
                    <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"></path>
                    <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.716H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853"></path>
                    <path d="M3.964 10.705A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.705V4.962H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.038l3.007-2.333z" fill="#FBBC05"></path>
                    <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.962l3.007 2.333C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335"></path>
                </svg>
                <span>Google로 계속하기</span>
            </button>
        </div>
    );
};

export default LoginPage;
