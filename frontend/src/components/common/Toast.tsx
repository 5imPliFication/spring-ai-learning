import React, { useEffect } from 'react';
import { X } from 'lucide-react';

export interface ToastData {
  /** Changes for every toast, so the same text shown twice restarts the timer. */
  id: number;
  message: string;
}

interface ToastProps {
  toast: ToastData | null;
  onDismiss: () => void;
  durationMs?: number;
}

/** A single bottom-centre toast that dismisses itself. */
export const Toast: React.FC<ToastProps> = ({ toast, onDismiss, durationMs = 6000 }) => {
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [toast, onDismiss, durationMs]);

  if (!toast) return null;

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4">
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-auto flex max-w-md items-start gap-3 rounded-xl border border-amber-500/30 bg-slate-900/95 px-4 py-3 text-sm text-amber-100 shadow-2xl backdrop-blur"
      >
        <span>{toast.message}</span>
        <button
          onClick={onDismiss}
          className="shrink-0 text-amber-300/70 transition-colors hover:text-amber-100"
          aria-label="Dismiss notification"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
};
