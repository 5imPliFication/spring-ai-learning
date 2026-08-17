import React from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AuthModal } from './components/auth/AuthModal';
import { ChatLayout } from './components/chat/ChatLayout';
import { InviteJoin } from './components/chat/InviteJoin';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import { Loader2 } from 'lucide-react';

const AppContent: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen w-full bg-slate-950 flex items-center justify-center text-slate-400">
        <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <AuthModal />;
  }

  if (location.pathname.startsWith('/invite/')) {
    return <InviteJoin />;
  }

  return <ChatLayout />;
};

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/" element={<AppContent />} />
            <Route path="/rooms/:roomId" element={<AppContent />} />
            <Route path="/invite/:roomId" element={<AppContent />} />
            <Route path="*" element={<AppContent />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
};

export default App;