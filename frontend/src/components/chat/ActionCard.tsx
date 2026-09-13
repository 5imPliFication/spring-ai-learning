import React, { useState } from 'react';
import type { CalendarActionCard as CalendarActionCardData } from '../../types';
import { calendarActionApi } from '../../services/api';
import { Calendar, Clock, MapPin, Check, X, Sparkles } from 'lucide-react';

export interface ActionCardResult {
  status: 'APPROVED' | 'DECLINED';
  message?: string;
}

interface ActionCardProps {
  card: CalendarActionCardData;
  currentUserId: string;
  onResult?: (result: ActionCardResult) => void;
}

const formatWhen = (startTime: string, endTime: string): string => {
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/;
  if (dateOnly.test(startTime) && dateOnly.test(endTime)) {
    return `${startTime}`;
  }
  const fmt = (v: string): string => {
    try {
      const d = new Date(v);
      if (Number.isNaN(d.getTime())) return v;
      return d.toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      });
    } catch {
      return v;
    }
  };
  return `${fmt(startTime)} \u2013 ${fmt(endTime)}`;
};

const StatusBadge: React.FC<{ status: string | undefined }> = ({ status }) => {
  if (!status || status === 'PENDING') {
    return (
      <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30">
        Pending your approval
      </span>
    );
  }
  if (status === 'APPROVED') {
    return (
      <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
        Event created
      </span>
    );
  }
  return (
    <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-red-500/15 text-red-300 border border-red-500/30">
      Declined
    </span>
  );
};

export const ActionCard: React.FC<ActionCardProps> = ({ card, currentUserId, onResult }) => {
  const [status, setStatus] = useState<string>(card.status || 'PENDING');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOwner = !!card.userId && card.userId === currentUserId;
  const actionable = status === 'PENDING';

  const handleConfirm = async () => {
    if (!actionable || !isOwner || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await calendarActionApi.confirm(card.actionId);
      setStatus(res.status === 'APPROVED' ? 'APPROVED' : 'DECLINED');
      onResult?.({ status: res.status === 'APPROVED' ? 'APPROVED' : 'DECLINED', message: res.message });
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Could not create the event.';
      setError(msg);
      setStatus('DECLINED');
    } finally {
      setBusy(false);
    }
  };

  const handleDecline = async () => {
    if (!actionable || !isOwner || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await calendarActionApi.decline(card.actionId);
      setStatus(res.status === 'DECLINED' ? 'DECLINED' : 'PENDING');
      onResult?.({ status: 'DECLINED', message: res.message });
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Could not decline the event.';
      setError(msg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="w-full max-w-sm rounded-2xl border border-blue-500/30 bg-gradient-to-br from-blue-950/80 to-slate-900 overflow-hidden shadow-lg">
      <div className="flex items-center gap-2 px-4 py-2.5 bg-blue-500/10 border-b border-blue-500/20">
        <Sparkles className="w-3.5 h-3.5 text-blue-300" />
        <span className="text-xs font-semibold text-blue-200 flex-1">New calendar event</span>
        <StatusBadge status={status} />
      </div>
      <div className="px-4 py-3 flex flex-col gap-2">
        <span className="text-sm font-semibold text-white">{card.summary || 'Untitled event'}</span>
        <span className="flex items-center gap-1.5 text-xs text-slate-300">
          <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          {formatWhen(card.startTime, card.endTime)}
        </span>
        {card.location && (
          <span className="flex items-center gap-1.5 text-xs text-slate-300">
            <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            {card.location}
          </span>
        )}
        {card.description && (
          <span className="text-xs text-slate-400 whitespace-pre-wrap break-words">{card.description}</span>
        )}
        <span className="flex items-center gap-1.5 text-[10px] text-slate-500 uppercase tracking-wide">
          <Calendar className="w-3 h-3" />
          {card.calendarId || 'primary'}
        </span>
      </div>
      <div className="px-4 pb-3 flex flex-col gap-2">
        {error && <span className="text-[11px] text-red-400">{error}</span>}
        {actionable && isOwner ? (
          <div className="flex gap-2">
            <button
              onClick={handleConfirm}
              disabled={busy}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-semibold transition-colors"
            >
              <Check className="w-3.5 h-3.5" />
              {busy ? 'Creating\u2026' : 'Approve & create'}
            </button>
            <button
              onClick={handleDecline}
              disabled={busy}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 text-xs font-semibold transition-colors"
            >
              <X className="w-3.5 h-3.5" />
              Decline
            </button>
          </div>
        ) : actionable ? (
          <span className="text-[11px] text-slate-500 italic text-center">
            Waiting for approval from the requester
          </span>
        ) : (
          <span className="text-[11px] text-slate-500 italic text-center">
            This action is no longer available.
          </span>
        )}
      </div>
    </div>
  );
};