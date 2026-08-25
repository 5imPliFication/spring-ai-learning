import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { AppNotification } from '../types';
import { notificationApi } from '../services/api';
import { wsService } from '../services/websocket';
import { useAuth } from './AuthContext';

interface NotificationContextType {
  notifications: AppNotification[];
  unreadCount: number;
  roomUnreadDeltas: Record<string, number>;
  setActiveRoomId: (roomId: string | null) => void;
  clearRoomDelta: (roomId: string) => void;
  markRead: (id: number) => void;
  markAllRead: () => void;
}

const NOTIFICATION_MESSAGE_TYPES = new Set(['MENTION', 'GROUP_MESSAGE', 'DM_MESSAGE', 'AI_REPLY']);

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [roomUnreadDeltas, setRoomUnreadDeltas] = useState<Record<string, number>>({});
  const activeRoomIdRef = useRef<string | null>(null);

  const setActiveRoomId = useCallback((roomId: string | null) => {
    activeRoomIdRef.current = roomId;
  }, []);

  const clearRoomDelta = useCallback((roomId: string) => {
    setRoomUnreadDeltas((prev) => {
      if (!(roomId in prev)) return prev;
      const next = { ...prev };
      delete next[roomId];
      return next;
    });
  }, []);

  useEffect(() => {
    if (!user) return;

    let cancelled = false;
    (async () => {
      try {
        const [list, count] = await Promise.all([
          notificationApi.getNotifications(50),
          notificationApi.getUnreadCount(),
        ]);
        if (cancelled) return;
        setNotifications(list);
        setUnreadCount(count);
      } catch (err) {
        console.error('Failed to load notifications:', err);
      }
    })();

    const handleNotification = (n: AppNotification) => {
      if (n.roomId && n.roomId === activeRoomIdRef.current) {
        setNotifications((prev) => [n, ...prev].slice(0, 50));
        notificationApi.markRead(n.id).catch(() => {});
        return;
      }
      setNotifications((prev) => [n, ...prev].slice(0, 50));
      if (!n.roomId || NOTIFICATION_MESSAGE_TYPES.has(n.type)) {
        setUnreadCount((c) => c + 1);
      }
      if (n.roomId && NOTIFICATION_MESSAGE_TYPES.has(n.type)) {
        setRoomUnreadDeltas((prev) => ({
          ...prev,
          [n.roomId as string]: (prev[n.roomId as string] ?? 0) + 1,
        }));
      }
    };

    wsService.subscribeToUserNotifications(user.id, handleNotification);

    return () => {
      cancelled = true;
    };
  }, [user]);

  const markRead = useCallback((id: number) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    setUnreadCount((c) => Math.max(0, c - 1));
    notificationApi.markRead(id).catch(() => {});
  }, []);

  const markAllRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
    notificationApi.markAllRead().catch(() => {});
  }, []);

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        unreadCount,
        roomUnreadDeltas,
        setActiveRoomId,
        clearRoomDelta,
        markRead,
        markAllRead,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
};

export const useNotifications = (): NotificationContextType => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
};