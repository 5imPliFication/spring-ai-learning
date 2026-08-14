import React, { useEffect, useRef } from 'react';
import type { Message } from '../../types';
import { MessageItem } from './MessageItem';
import { MessageSquare } from 'lucide-react';

interface MessageFeedProps {
  messages: Message[];
  currentUserId: string;
}

export const MessageFeed: React.FC<MessageFeedProps> = ({ messages, currentUserId }) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 text-center text-slate-500">
        <div className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center mb-3">
          <MessageSquare className="w-6 h-6 text-slate-400" />
        </div>
        <p className="text-sm font-medium text-slate-400 mb-1">No messages yet</p>
        <p className="text-xs text-slate-500 max-w-xs">
          Start the conversation or type <code className="text-emerald-400 font-mono">@ai</code> to ask Azura something!
        </p>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-2">
      {messages.map((msg, i) => (
        <MessageItem
          key={msg.id || i}
          message={msg}
          isSelf={msg.senderId === currentUserId}
        />
      ))}
      <div ref={bottomRef} />
    </div>
  );
};
