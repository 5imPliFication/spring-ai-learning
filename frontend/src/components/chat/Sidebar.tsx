import React, { useState, useEffect } from 'react';
import type { Room, User, Friend } from '../../types';
import { friendApi } from '../../services/api';
import { Avatar } from '../ui/Avatar';
import { Hash, Plus, LogOut, Search, X, Users, User as UserIcon, ShieldAlert, Lock, Compass, MessageSquare, EyeOff } from 'lucide-react';

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
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [friendsList, setFriendsList] = useState<Friend[]>([]);
  const [dmLoadingId, setDmLoadingId] = useState<string | null>(null);

  useEffect(() => {
    loadFriendsPreview();
  }, [friendsRefreshKey]);

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
      {/* User Header */}
      <div className="p-4 border-b border-slate-800 flex items-center justify-between">
        <button
          onClick={onOpenProfile}
          className="flex items-center gap-3 text-left group hover:opacity-90 transition-opacity"
        >
          <Avatar name={user.displayName} size="md" />
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

        <button
          onClick={onOpenProfile}
          className="py-2 px-3 bg-slate-950/60 hover:bg-slate-800 border border-slate-800/80 rounded-xl text-slate-300 hover:text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-all"
        >
          <UserIcon className="w-3.5 h-3.5 text-slate-400" />
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
      </div>

      {/* DEDICATED FRIENDS SIDEBAR CARD */}
      <div className="p-3 border-b border-slate-800 bg-slate-950/40">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-blue-400" />
            Friends ({friendsList.length})
          </span>
          <button
            onClick={onOpenFriends}
            className="text-[11px] font-semibold text-blue-400 hover:underline"
          >
            Manage
          </button>
        </div>

        {friendsList.length === 0 ? (
          <div className="text-center py-2 text-[11px] text-slate-500">
            No friends added yet. Click "Manage" to add friends!
          </div>
        ) : (
          <div className="space-y-1 max-h-72 overflow-y-auto pr-1">
            {friendsList.map((f) => {
              const isLoading = dmLoadingId === f.friendId;
              return (
                <button
                  key={f.id}
                  onClick={() => handleStartDM(f.friendId)}
                  disabled={isLoading}
                  title={`Message ${f.friendDisplayName}`}
                  className="w-full flex items-center gap-2 p-2 rounded-xl bg-slate-900/80 hover:bg-slate-800 border border-slate-800/60 transition-colors disabled:opacity-60 disabled:cursor-wait text-left"
                >
                  <Avatar name={f.friendDisplayName} size="sm" />
                  <span className="text-xs font-medium text-slate-200 truncate flex-1">{f.friendDisplayName}</span>
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
            return (
              <button
                key={room.id}
                onClick={() => onSelectRoom(room.id)}
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
                <span className="truncate flex-1">{room.name}</span>
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
    </aside>
  );
};
