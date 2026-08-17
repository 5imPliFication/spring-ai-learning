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

    if (savedToken) {
      setToken(savedToken);
      if (savedUserStr) {
        try {
          setUser(JSON.parse(savedUserStr));
        } catch (e) {
          console.error(e);
        }
      }

      // Fetch fresh profile from backend to ensure latest role & data
      import('../services/api').then(({ userApi }) => {
        userApi.getProfile()
          .then((freshUser) => {
            setUser(freshUser);
            localStorage.setItem('user', JSON.stringify(freshUser));
          })
          .catch((err) => {
            console.error('Failed to sync user profile:', err);
          })
          .finally(() => {
            setIsLoading(false);
          });
      });

wsService.connect(savedToken);
    } else {
      setIsLoading(false);
    }
  }, []);

  const handleAuthSuccess = (res: AuthResponse) => {
    const authUser: User = {
      id: res.userId,
      username: res.username,
      displayName: res.displayName,
      role: res.role as User['role'],
    };
    setToken(res.token);
    setUser(authUser);

    localStorage.setItem('token', res.token);
    localStorage.setItem('user', JSON.stringify(authUser));

    wsService.connect(res.token);
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
