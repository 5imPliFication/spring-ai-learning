import React from 'react';
import { Hash, Sparkles, Menu, Settings, Lock, EyeOff } from 'lucide-react';
import type { Room } from '../../types';

interface ChatHeaderProps {
  room: Room | null;
  isAiTyping: boolean;
  onToggleSidebar?: () => void;
  onOpenSettings?: () => void;
}

export const ChatHeader: React.FC<ChatHeaderProps> = ({
  room,
  isAiTyping,
  onToggleSidebar,
  onOpenSettings,
}) => {
  if (!room) return null;

  return (
    <header className="h-16 border-b border-slate-800 bg-slate-900/60 backdrop-blur-md px-6 flex items-center justify-between flex-shrink-0 z-10">
      <div className="flex items-center gap-3">
        {onToggleSidebar && (
          <button
            onClick={onToggleSidebar}
            className="md:hidden p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <Menu className="w-5 h-5" />
          </button>
        )}
        <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center text-slate-400">
          {room.isPrivate ? (
            <EyeOff className="w-4 h-4 text-violet-400" />
          ) : room.isProtected ? (
            <Lock className="w-4 h-4 text-amber-400" />
          ) : (
            <Hash className="w-4 h-4" />
          )}
        </div>
        <div>
          <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
            {room.name}
            {room.type === 'DIRECT' && (
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-semibold">
                DM
              </span>
            )}
          </h2>
          <span className="text-xs text-slate-400 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            {room.type === 'DIRECT' ? '1-on-1 Direct Message' : 'Group Room'}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {isAiTyping && (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium animate-pulse">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Azura is thinking...</span>
          </div>
        )}

        {onOpenSettings && (
          <button
            onClick={onOpenSettings}
            title="Room Settings"
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <Settings className="w-5 h-5" />
          </button>
        )}
      </div>
    </header>
  );
};
