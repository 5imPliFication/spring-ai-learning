import React, { useState, useRef, useEffect } from 'react';
import { useNotifications } from '../../context/NotificationContext';
import { Bell, CheckCheck, UserPlus, MessageSquare, DoorOpen, Sparkles } from 'lucide-react';
import type { AppNotification } from '../../types';

interface NotificationBellProps {
  onOpenFriends: () => void;
  onOpenRoom: (roomId: string) => void;
}

const formatTime = (iso: string): string => {
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString();
};

const iconFor = (n: AppNotification) => {
  switch (n.type) {
    case 'FRIEND_REQUEST':
    case 'FRIEND_ACCEPTED':
      return <UserPlus className="w-3.5 h-3.5 text-blue-400" />;
    case 'DM_MESSAGE':
      return <MessageSquare className="w-3.5 h-3.5 text-emerald-400" />;
    case 'AI_REPLY':
      return <Sparkles className="w-3.5 h-3.5 text-violet-400" />;
    case 'ROOM_JOINED':
      return <DoorOpen className="w-3.5 h-3.5 text-amber-400" />;
    default:
      return <Bell className="w-3.5 h-3.5 text-slate-400" />;
  }
};

export const NotificationBell: React.FC<NotificationBellProps> = ({ onOpenFriends, onOpenRoom }) => {
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleItemClick = (n: AppNotification) => {
    markRead(n.id);
    setIsOpen(false);
    if (n.type === 'FRIEND_REQUEST' || n.type === 'FRIEND_ACCEPTED') {
      onOpenFriends();
    } else if (n.roomId) {
      onOpenRoom(n.roomId);
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={() => setIsOpen((o) => !o)}
        className="relative p-2 bg-slate-950/60 hover:bg-slate-800 border border-slate-800/80 rounded-xl text-slate-300 hover:text-white transition-all"
        title="Notifications"
      >
        <Bell className="w-4 h-4" />
        {unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-bold">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute left-0 top-full mt-2 w-80 bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-800">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Notifications</span>
            {unreadCount > 0 && (
              <button
                onClick={markAllRead}
                className="text-[11px] font-semibold text-blue-400 hover:underline flex items-center gap-1"
              >
                <CheckCheck className="w-3 h-3" /> Mark all read
              </button>
            )}
          </div>
          <div className="overflow-y-auto max-h-72">
            {notifications.length === 0 ? (
              <div className="py-10 text-center text-xs text-slate-500">No notifications yet</div>
            ) : (
              notifications.map((n) => (
                <button
                  key={n.id}
                  onClick={() => handleItemClick(n)}
                  className={`w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-slate-800/60 transition-colors ${n.read ? 'opacity-60' : ''}`}
                >
                  <div className="w-7 h-7 rounded-lg bg-slate-800 flex items-center justify-center shrink-0">
                    {iconFor(n)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-bold text-white truncate">{n.title}</span>
                      {!n.read && <span className="w-2 h-2 rounded-full bg-blue-500 shrink-0" />}
                    </div>
                    {n.body && (
                      <span className="text-[11px] text-slate-400 line-clamp-2 block">{n.body}</span>
                    )}
                    <span className="text-[10px] text-slate-500 block mt-0.5">{formatTime(n.createdAt)}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};