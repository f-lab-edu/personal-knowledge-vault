import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import {
  getCurrentMember,
  logout as logoutRequest,
  withdraw as withdrawRequest,
  startOAuthLogin,
} from '../api/auth';

const AUTH_QUERY_KEY = ['auth', 'user'];

export const useAuth = () => {
  const queryClient = useQueryClient();

  const { data: user, isLoading, refetch: refreshUser } = useQuery({
    queryKey: AUTH_QUERY_KEY,
    queryFn: async () => {
      try {
        return await getCurrentMember();
      } catch (error) {
        if (error.status === 401) {
          return null; // 비로그인 상태를 성공으로 처리
        }
        throw error; // 다른 에러는 그대로 throw
      }
    },
    staleTime: Infinity,
    retry: false,
  });

  const clearUser = () => queryClient.setQueryData(AUTH_QUERY_KEY, null);
  const handleAuthError = (error) => {
    if (error.status === 401) clearUser();
  };

  const logoutMutation = useMutation({
    mutationFn: logoutRequest,
    onSuccess: clearUser,
    onError: handleAuthError,
  });

  const withdrawMutation = useMutation({
    mutationFn: withdrawRequest,
    onSuccess: clearUser,
    onError: handleAuthError,
  });

  const login = useCallback(() => startOAuthLogin(), []);
  const logout = useCallback(() => logoutMutation.mutateAsync(), [logoutMutation]);
  const withdraw = useCallback(() => withdrawMutation.mutateAsync(), [withdrawMutation]);

  return {
    user: user ?? null,
    isAuthenticated: Boolean(user),
    isLoading,
    login,
    logout,
    withdraw,
    refreshUser,
    isLoggingOut: logoutMutation.isPending,
    isWithdrawing: withdrawMutation.isPending,
  };
};
