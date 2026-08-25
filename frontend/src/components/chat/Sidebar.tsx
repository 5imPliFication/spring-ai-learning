import React, { useState, useEffect, useRef } from 'react';
import type { Room, User, Friend, NotificationMode } from '../../types';
import { friendApi } from '../../services/api';
import { Avatar } from '../ui/Avatar';
import { NotificationBell } from '../notifications/NotificationBell';
import { FriendContextMenu } from '../friends/FriendContextMenu';
import {
  Hash, Plus, LogOut, Search, X, Users, ShieldAlert, Lock, Compass,
  MessageSquare, EyeOff, AtSign, BellOff, BellRing,
} from 'lucide-react';

interface SidebarProps {
  user: User;
  rooms: Room[];
  activeRoomId: string | null;
  onSelectRoom: (roomId: string) => void;
  onOpenCreateRoom: () => void;
  onOpenDiscoverRooms: () => void;
  onOpenProfile: () => void;
  onOpenFriends: () => void;
  onOpenAdmin?: () => void;
  onLogout: () => void;
  onCloseMobile?: () => void;
  friendsRefreshKey?: number;
  onUpdateNotificationMode?: (roomId: string, mode: NotificationMode) => void;
  onViewProfile?: (userId: string) => void;
}

const MODE_OPTIONS: {
  value: NotificationMode;
  label: string;
  description: string;
  icon: React.ReactNode;
}[] = [
  { value: 'ALL', label: 'All messages', description: 'Notify me about everything', icon: <BellRing className="w-3.5 h-3.5" /> },
  { value: 'MENTIONS_ONLY', label: 'Mentions only', description: 'Only when someone tags me', icon: <AtSign className="w-3.5 h-3.5" /> },
  { value: 'MUTED', label: 'Mute', description: 'Never notify me', icon: <BellOff className="w-3.5 h-3.5" /> },
];

interface RoomMenuState {
  roomId: string;
  roomName: string;
  currentMode: NotificationMode;
  x: number;
  y: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  user,
  rooms,
  activeRoomId,
  onSelectRoom,
  onOpenCreateRoom,
  onOpenDiscoverRooms,
  onOpenProfile,
  onOpenFriends,
  onOpenAdmin,
  onLogout,
  onCloseMobile,
  friendsRefreshKey = 0,
  onUpdateNotificationMode,
  onViewProfile,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [friendsList, setFriendsList] = useState<Friend[]>([]);
  const [dmLoadingId, setDmLoadingId] = useState<string | null>(null);
  const [roomMenu, setRoomMenu] = useState<RoomMenuState | null>(null);
  const [friendMenu, setFriendMenu] = useState<{ friend: Friend; x: number; y: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    loadFriendsPreview();
  }, [friendsRefreshKey]);

  useEffect(() => {
    if (!roomMenu) return;
    const closeOnClick = (e: MouseEvent) => {
      if (menuRef.current && menuRef.current.contains(e.target as Node)) return;
      setRoomMenu(null);
    };
    const closeOnEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setRoomMenu(null);
    };
    window.addEventListener('click', closeOnClick);
    window.addEventListener('contextmenu', closeOnClick);
    window.addEventListener('keydown', closeOnEsc);
    return () => {
      window.removeEventListener('click', closeOnClick);
      window.removeEventListener('contextmenu', closeOnClick);
      window.removeEventListener('keydown', closeOnEsc);
    };
  }, [roomMenu]);

  const handleSetMode = (mode: NotificationMode) => {
    if (roomMenu && onUpdateNotificationMode) {
      onUpdateNotificationMode(roomMenu.roomId, mode);
    }
    setRoomMenu(null);
  };

  const openRoomMenu = (e: React.MouseEvent, room: Room) => {
    if (!onUpdateNotificationMode) return;
    e.preventDefault();
    e.stopPropagation();
    setRoomMenu({
      roomId: room.id,
      roomName: room.name,
      currentMode: room.notificationMode ?? 'ALL',
      x: Math.min(e.clientX, window.innerWidth - 232),
      y: Math.min(e.clientY, window.innerHeight - 180),
    });
  };

  const openFriendMenu = (e: React.MouseEvent, friend: Friend) => {
    e.preventDefault();
    e.stopPropagation();
    setFriendMenu({
      friend,
      x: Math.min(e.clientX, window.innerWidth - 220),
      y: Math.min(e.clientY, window.innerHeight - 280),
    });
  };

  const handleFriendUnfriended = (friendId: string) => {
    setFriendsList((prev) => prev.filter((f) => f.friendId !== friendId));
  };

  const loadFriendsPreview = async () => {
    try {
      const data = await friendApi.getFriends();
      setFriendsList(data);
    } catch (err) {
      console.error('Failed to load sidebar friends preview:', err);
    }
  };

  const filteredRooms = rooms
    .filter((r) => r.type !== 'DIRECT')
    .filter((r) => r.name.toLowerCase().includes(searchTerm.toLowerCase()));

  const handleStartDM = async (friendId: string) => {
    if (dmLoadingId) return;
    setDmLoadingId(friendId);
    try {
      const dmRoom = await friendApi.getOrCreateDM(friendId);
      onSelectRoom(dmRoom.id);
    } catch (err) {
      console.error('Failed to start DM:', err);
    } finally {
      setDmLoadingId(null);
    }
  };

  return (
    <aside className="w-80 h-full bg-slate-900 border-r border-slate-800 flex flex-col flex-shrink-0 select-none">
      {/* Room notification mode context menu */}
      {roomMenu && (
        <div
          ref={menuRef}
          onClick={(e) => e.stopPropagation()}
          onContextMenu={(e) => e.preventDefault()}
          className="fixed z-50 w-56 rounded-xl bg-slate-950 border border-slate-700 shadow-2xl overflow-hidden"
          style={{ left: roomMenu.x, top: roomMenu.y }}
        >
          <div className="px-3 py-2 border-b border-slate-800">
            <span className="text-[11px] font-bold text-white truncate block">#{roomMenu.roomName}</span>
            <span className="text-[10px] text-slate-500">Notifications</span>
          </div>
          <div className="py-1">
            {MODE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => handleSetMode(opt.value)}
                className={`w-full flex items-center gap-2 px-3 py-2 text-left transition-colors ${
                  roomMenu.currentMode === opt.value
                    ? 'bg-blue-600/20 text-blue-300'
                    : 'text-slate-300 hover:bg-slate-900'
                }`}
              >
                {opt.icon}
                <span className="flex flex-col min-w-0">
                  <span className="text-xs font-semibold">{opt.label}</span>
                  <span className="text-[10px] text-slate-500 truncate">{opt.description}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* User Header */}
      <div className="p-4 border-b border-slate-800 flex items-center justify-between">
        <button
          onClick={onOpenProfile}
          className="flex items-center gap-3 text-left group hover:opacity-90 transition-opacity"
        >
          <Avatar name={user.displayName} src={user.avatarUrl} size="md" />
          <div className="flex flex-col">
            <span className="text-sm font-bold text-white tracking-tight group-hover:text-blue-400 transition-colors">
              {user.displayName}
            </span>
            <span className="text-xs text-slate-400 font-mono">@{user.username}</span>
          </div>
        </button>
        {onCloseMobile && (
          <button
            onClick={onCloseMobile}
            className="md:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Quick Access Bar */}
      <div className="p-3 border-b border-slate-800 flex gap-2">
        <button
          onClick={onOpenFriends}
          className="flex-1 py-2 px-3 bg-slate-950/60 hover:bg-slate-800 border border-slate-800/80 rounded-xl text-slate-300 hover:text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-all"
        >
          <Users className="w-3.5 h-3.5 text-blue-400" />
          <span>Friends</span>
        </button>

        {user?.role?.toUpperCase() === 'ADMIN' && onOpenAdmin && (
          <button
            onClick={onOpenAdmin}
            className="py-2 px-3 bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/20 rounded-xl text-emerald-400 text-xs font-semibold flex items-center justify-center gap-1.5 transition-all"
            title="Admin Dashboard"
          >
            <ShieldAlert className="w-3.5 h-3.5 text-emerald-400" />
          </button>
        )}

        <NotificationBell onOpenFriends={onOpenFriends} onOpenRoom={onSelectRoom} />
      </div>

      {/* DEDICATED FRIENDS SIDEBAR CARD */}
      <div className="p-3 border-b border-slate-800 bg-slate-950/40">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-blue-400" />
            Friends ({friendsList.length})
          </span>
        </div>

        {friendsList.length === 0 ? (
          <div className="text-center py-2 text-[11px] text-slate-500">
            brodie got no friends
          </div>
        ) : (
          <div className="space-y-1 max-h-72 overflow-y-auto pr-1">
            {friendsList.map((f) => {
              const isLoading = dmLoadingId === f.friendId;
              return (
                <button
                  key={f.id}
                  onClick={() => handleStartDM(f.friendId)}
                  onContextMenu={(e) => openFriendMenu(e, f)}
                  disabled={isLoading}
                  title={`Message ${f.friendDisplayName}`}
                  className="w-full flex items-center gap-2 p-2 rounded-xl bg-slate-900/80 hover:bg-slate-800 border border-slate-800/60 transition-colors disabled:opacity-60 disabled:cursor-wait text-left"
                >
                  <Avatar name={f.friendDisplayName} src={f.friendAvatarUrl} size="sm" />
                  <span className="text-xs font-medium text-slate-200 truncate flex-1">
                    {f.friendDisplayName}
                  </span>
                  <MessageSquare className="w-3 h-3 text-blue-400 shrink-0" />
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Rooms Search & Discover */}
      <div className="p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Joined Rooms</span>
          <div className="flex items-center gap-1">
            <button
              onClick={onOpenDiscoverRooms}
              title="Discover & Join Rooms"
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors text-xs font-semibold flex items-center gap-1"
            >
              <Compass className="w-3.5 h-3.5 text-blue-400" />
              <span className="text-[11px]">Discover</span>
            </button>
            <button
              onClick={onOpenCreateRoom}
              title="Create New Room"
              className="p-1.5 rounded-lg bg-blue-600/10 hover:bg-blue-600/20 text-blue-400 transition-colors text-xs font-semibold flex items-center gap-1"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div className="relative">
          <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-2.5" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search joined rooms..."
            className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-8 pr-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
          />
        </div>
      </div>

      {/* Room List (Joined Rooms Only) */}
      <div className="flex-1 overflow-y-auto px-3 space-y-1">
        {filteredRooms.length === 0 ? (
          <div className="text-center py-8 text-slate-500 text-xs px-2">
            {searchTerm ? 'No joined rooms match your search' : 'You have not joined any rooms yet. Click "Discover" or "+" above!'}
          </div>
        ) : (
          filteredRooms.map((room) => {
            const isActive = room.id === activeRoomId;
            const isMuted = room.notificationMode === 'MUTED';
            const isMentionsOnly = room.notificationMode === 'MENTIONS_ONLY';
            const unread = room.unreadCount ?? 0;
            const showBadge = !isMuted && unread > 0;
            return (
              <button
                key={room.id}
                onClick={() => onSelectRoom(room.id)}
                onContextMenu={(e) => openRoomMenu(e, room)}
                title={isMuted ? `${room.name} (muted)` : 'Right-click for notification options'}
                className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl text-xs font-medium transition-all text-left ${
                  isActive
                    ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20 font-semibold'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`}
              >
                <div
                  className={`w-6 h-6 rounded-lg flex items-center justify-center ${
                    isActive ? 'bg-white/20 text-white' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {room.isPrivate ? (
                    <EyeOff className="w-3 h-3 text-violet-400" />
                  ) : room.isProtected ? (
                    <Lock className="w-3 h-3 text-amber-400" />
                  ) : (
                    <Hash className="w-3.5 h-3.5" />
                  )}
                </div>
                <span className={`truncate flex-1 ${isMuted && !isActive ? 'opacity-60' : ''}`}>
                  {room.name}
                </span>
                {isMuted && !isActive && (
                  <BellOff className="w-3 h-3 text-slate-500 shrink-0" />
                )}
                {showBadge &&
                  (isMentionsOnly ? (
                    <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-violet-500 text-white text-[10px] font-bold shrink-0">
                      <AtSign className="w-2.5 h-2.5" />
                      {unread > 99 ? '99+' : unread}
                    </span>
                  ) : (
                    <span className="px-1.5 py-0.5 rounded-full bg-blue-500 text-white text-[10px] font-bold shrink-0 min-w-[20px] text-center">
                      {unread > 99 ? '99+' : unread}
                    </span>
                  ))}
              </button>
            );
          })
        )}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-slate-800">
        <button
          onClick={onLogout}
          className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl border border-slate-800 text-slate-400 hover:text-red-400 hover:bg-red-500/10 hover:border-red-500/20 text-xs font-medium transition-all"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Sign Out</span>
        </button>
      </div>
      {/* Friend context menu */}
      {friendMenu && (
        <FriendContextMenu
          friend={friendMenu.friend}
          x={friendMenu.x}
          y={friendMenu.y}
          onClose={() => setFriendMenu(null)}
          onViewProfile={(userId) => { onViewProfile?.(userId); setFriendMenu(null); }}
          onUnfriend={handleFriendUnfriended}
          showMessageOption
          onMessage={(friendId) => handleStartDM(friendId)}
        />
      )}
    </aside>
  );
};
