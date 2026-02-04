import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AuthContext from './auth-context';
import { getCurrentMember, logout as logoutRequest, startOAuthLogin, withdraw as withdrawRequest } from '../api/auth';

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    const refreshUser = useCallback(async () => {
        setIsLoading(true);
        try {
            const member = await getCurrentMember();
            setUser(member);
        } catch {
            setUser(null);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        refreshUser();
    }, [refreshUser]);

    const login = useCallback(() => {
        startOAuthLogin();
    }, []);

    const logout = useCallback(async () => {
        await logoutRequest();
        setUser(null);
    }, []);

    const withdraw = useCallback(async () => {
        await withdrawRequest();
        setUser(null);
    }, []);

    const value = useMemo(
        () => ({
            user,
            isAuthenticated: Boolean(user),
            isLoading,
            login,
            logout,
            withdraw,
            refreshUser,
        }),
        [user, isLoading, login, logout, withdraw, refreshUser]
    );

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};
