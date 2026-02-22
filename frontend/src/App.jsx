import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './layout/AppLayout';
import LoginPage from './pages/LoginPage';
import { useAuth } from './hooks/useAuth';

function App() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="appLoading">
        로그인 상태를 확인하는 중입니다...
      </div>
    );
  }

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        path="/"
        element={isAuthenticated ? <AppLayout /> : <Navigate to="/login" replace />}
      />
    </Routes>
  );
}

export default App;
