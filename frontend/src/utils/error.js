export const getErrorMessage = (error, fallback = '오류가 발생했습니다.') =>
    error?.payload?.error?.message || error?.message || fallback;
