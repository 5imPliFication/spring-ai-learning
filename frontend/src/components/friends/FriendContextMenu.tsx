import React, { useEffect, useRef } from 'react';
import type { Friend } from '../../types';
import { friendApi } from '../../services/api';
import { User, UserX } from 'lucide-react';

interface FriendContextMenuProps {
  friend: Friend;
  x: number;
  y: number;
  onClose: () => void;
  onViewProfile: (userId: string) => void;
  onUnfriend?: (friendId: string) => void;
  showMessageOption?: boolean;
  onMessage?: (friendId: string) => void;
}

export const FriendContextMenu: React.FC<FriendContextMenuProps> = ({
  friend,
  x,
  y,
  onClose,
  onViewProfile,
  onUnfriend,
  showMessageOption = false,
  onMessage,
}) => {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const closeOnClick = (e: MouseEvent) => {
      if (menuRef.current && menuRef.current.contains(e.target as Node)) return;
      onClose();
    };
    const closeOnEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('mousedown', closeOnClick);
    window.addEventListener('keydown', closeOnEsc);
    return () => {
      window.removeEventListener('mousedown', closeOnClick);
      window.removeEventListener('keydown', closeOnEsc);
    };
  }, [onClose]);

  const handleViewProfile = (e: React.MouseEvent) => {
    e.stopPropagation();
    onViewProfile(friend.friendId);
    onClose();
  };

  const handleMessage = (e: React.MouseEvent) => {
    e.stopPropagation();
    onMessage?.(friend.friendId);
    onClose();
  };

  const handleUnfriend = (e: React.MouseEvent) => {
    e.stopPropagation();
    onClose();
    friendApi.unfriend(friend.friendId)
      .then(() => onUnfriend?.(friend.friendId))
      .catch((err) => console.error('Failed to unfriend:', err));
  };

  return (
    <div
      ref={menuRef}
      onContextMenu={(e) => e.preventDefault()}
      className="fixed z-50 w-52 rounded-xl bg-slate-950 border border-slate-700 shadow-2xl overflow-hidden"
      style={{ left: x, top: y }}
    >
      <div className="px-3 py-2 border-b border-slate-800">
        <span className="text-[11px] font-bold text-white truncate block">{friend.friendDisplayName}</span>
        <span className="text-[10px] text-slate-500">@{friend.friendUsername}</span>
      </div>
      <div className="py-1">
        <button
          onClick={handleViewProfile}
          className="w-full flex items-center gap-2 px-3 py-2 text-left text-slate-300 hover:bg-slate-800 transition-colors"
        >
          <User className="w-3.5 h-3.5" />
          <span className="text-xs font-semibold">View Profile</span>
        </button>

        {showMessageOption && (
          <button
            onClick={handleMessage}
            className="w-full flex items-center gap-2 px-3 py-2 text-left text-slate-300 hover:bg-slate-800 transition-colors"
          >
            <User className="w-3.5 h-3.5" />
            <span className="text-xs font-semibold">Message</span>
          </button>
        )}

        <div className="my-1 border-t border-slate-800" />

        <button
          onClick={handleUnfriend}
          className="w-full flex items-center gap-2 px-3 py-2 text-left text-red-400 hover:bg-red-500/10 transition-colors"
        >
          <UserX className="w-3.5 h-3.5" />
          <span className="text-xs font-semibold">Unfriend</span>
        </button>
      </div>
    </div>
  );
};
