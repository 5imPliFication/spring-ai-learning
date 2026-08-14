import React, { createContext, useContext, useState, useEffect } from 'react';
import type { User, AuthResponse } from '../types';
import { authApi } from '../services/api';
import { wsService } from '../services/websocket';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const savedToken = localStorage.getItem('token');
    const savedUserStr = localStorage.getItem('user');

    if (savedToken && savedUserStr) {
      try {
        const savedUser: User = JSON.parse(savedUserStr);
        setToken(savedToken);
        setUser(savedUser);
        wsService.connect();
      } catch (err) {
        console.error('Failed to parse saved user credentials', err);
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
    setIsLoading(false);
  }, []);

  const handleAuthSuccess = (res: AuthResponse) => {
    const authUser: User = {
      id: res.userId,
      username: res.username,
      displayName: res.displayName,
    };
    setToken(res.token);
    setUser(authUser);

    localStorage.setItem('token', res.token);
    localStorage.setItem('user', JSON.stringify(authUser));

    wsService.connect();
  };

  const login = async (username: string, password: string) => {
    const res = await authApi.login(username, password);
    handleAuthSuccess(res);
  };

  const register = async (username: string, password: string, displayName: string) => {
    const res = await authApi.register(username, password, displayName);
    handleAuthSuccess(res);
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    wsService.disconnect();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
