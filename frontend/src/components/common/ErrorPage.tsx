import React from 'react';
import { Lock, ShieldAlert, FileQuestion, ServerCrash, ArrowLeft, RefreshCw, LogIn } from 'lucide-react';
import type { AppError } from '../../types';

interface ErrorPageProps {
  error: AppError;
  onClearError: () => void;
}

export const ErrorPage: React.FC<ErrorPageProps> = ({ error, onClearError }) => {
  const getErrorIcon = () => {
    switch (error.code) {
      case 401:
        return <Lock className="w-12 h-12 text-amber-400" />;
      case 403:
        return <ShieldAlert className="w-12 h-12 text-red-400" />;
      case 404:
        return <FileQuestion className="w-12 h-12 text-blue-400" />;
      case 500:
      default:
        return <ServerCrash className="w-12 h-12 text-rose-400" />;
    }
  };

  return (
    <div className="min-h-screen w-full bg-slate-950 flex items-center justify-center p-6 relative overflow-hidden">
      {/* Glow effect */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-red-600/10 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md bg-slate-900/90 border border-slate-800 rounded-3xl p-8 shadow-2xl text-center relative z-10">
        <div className="w-20 h-20 rounded-2xl bg-slate-950 border border-slate-800 flex items-center justify-center mx-auto mb-6 shadow-inner">
          {getErrorIcon()}
        </div>

        <span className="inline-block px-3 py-1 rounded-full bg-slate-800 text-slate-400 font-mono text-xs font-semibold mb-3">
          HTTP {error.code} ERROR
        </span>

        <h1 className="text-2xl font-bold text-white mb-2 tracking-tight">{error.title}</h1>
        <p className="text-slate-400 text-sm mb-8 leading-relaxed">{error.message}</p>

        <div className="flex flex-col gap-3">
          {error.code === 401 ? (
            <button
              onClick={() => {
                onClearError();
                window.location.reload();
              }}
              className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm rounded-xl shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2 transition-all"
            >
              <LogIn className="w-4 h-4" />
              <span>Sign In Again</span>
            </button>
          ) : (
            <button
              onClick={onClearError}
              className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm rounded-xl shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2 transition-all"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Return to Application</span>
            </button>
          )}

          <button
            onClick={() => window.location.reload()}
            className="w-full py-3 px-4 bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-sm rounded-xl border border-slate-700 flex items-center justify-center gap-2 transition-all"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Reload Page</span>
          </button>
        </div>
      </div>
    </div>
  );
};
