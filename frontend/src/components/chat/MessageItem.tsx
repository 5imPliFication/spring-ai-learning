import React from 'react';
import type { Message } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Sparkles } from 'lucide-react';

interface MessageItemProps {
  message: Message;
  isSelf: boolean;
}

export const MessageItem: React.FC<MessageItemProps> = ({ message, isSelf }) => {
  const isAi = message.senderId === 'ai-bot';

  const formatTime = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  };

  if (isSelf) {
    return (
      <div className="flex flex-col items-end my-1 group">
        <div className="max-w-[75%] md:max-w-[65%] bg-blue-600 text-white rounded-2xl rounded-tr-xs px-4 py-2.5 shadow-md text-sm leading-relaxed word-break">
          {message.content}
        </div>
        <span className="text-[10px] text-slate-500 mt-1 mr-1 opacity-0 group-hover:opacity-100 transition-opacity">
          {formatTime(message.createdAt)}
        </span>
      </div>
    );
  }

  if (isAi) {
    return (
      <div className="flex gap-3 my-2 max-w-[85%] md:max-w-[75%] group">
        <Avatar name="Azura" isAi size="md" />
        <div className="flex flex-col">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-semibold text-emerald-400 flex items-center gap-1">
              Azura
              <Sparkles className="w-3 h-3 text-emerald-400" />
            </span>
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
              AI
            </span>
          </div>
          <div className="bg-slate-900 border border-emerald-500/20 text-slate-100 rounded-2xl rounded-tl-xs px-4 py-3 shadow-lg text-sm leading-relaxed whitespace-pre-wrap">
            {message.content}
          </div>
          <span className="text-[10px] text-slate-500 mt-1 ml-1 opacity-0 group-hover:opacity-100 transition-opacity">
            {formatTime(message.createdAt)}
          </span>
        </div>
      </div>
    );
  }

  return (
    <div className="flex gap-3 my-1.5 max-w-[75%] md:max-w-[65%] group">
      <Avatar name={message.senderName} size="md" />
      <div className="flex flex-col">
        <span className="text-xs font-medium text-slate-400 mb-1 ml-1">{message.senderName}</span>
        <div className="bg-slate-800/90 text-slate-100 border border-slate-700/50 rounded-2xl rounded-tl-xs px-4 py-2.5 shadow-md text-sm leading-relaxed word-break">
          {message.content}
        </div>
        <span className="text-[10px] text-slate-500 mt-1 ml-1 opacity-0 group-hover:opacity-100 transition-opacity">
          {formatTime(message.createdAt)}
        </span>
      </div>
    </div>
  );
};
