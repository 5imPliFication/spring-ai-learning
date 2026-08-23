import React, { useEffect, useMemo, useRef } from 'react';
import type { Message, RoomMember } from '../../types';
import { MessageItem } from './MessageItem';
import { Avatar } from '../ui/Avatar';
import { MessageSquare, Sparkles } from 'lucide-react';

const GROUP_WINDOW_MS = 5 * 60 * 1000;

interface MessageFeedProps {
  messages: Message[];
  currentUserId: string;
  isAiTyping: boolean;
  roomOwnerId?: string;
  currentUserRole?: string;
  members?: RoomMember[];
  mentionedMessageIds?: Set<number>;
  onReply?: (message: Message) => void;
  onDelete?: (messageId: number) => void;
}

interface GroupedMessage extends Message {
  isFirstInGroup: boolean;
  isLastInGroup: boolean;
}

const timeGapMs = (a: string, b: string): number =>
  new Date(b).getTime() - new Date(a).getTime();

const groupMessages = (messages: Message[]): GroupedMessage[] =>
  messages.map((msg, i) => {
    const prev = messages[i - 1];
    const next = messages[i + 1];

    const isFirstInGroup = !prev || prev.senderId !== msg.senderId
      || timeGapMs(prev.createdAt, msg.createdAt) > GROUP_WINDOW_MS;
    const isLastInGroup = !next || next.senderId !== msg.senderId
      || timeGapMs(msg.createdAt, next.createdAt) > GROUP_WINDOW_MS;

    return { ...msg, isFirstInGroup, isLastInGroup };
  });

export const MessageFeed: React.FC<MessageFeedProps> = ({
  messages,
  currentUserId,
  isAiTyping,
  roomOwnerId,
  currentUserRole,
  members = [],
  mentionedMessageIds,
  onReply,
  onDelete,
}) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  const grouped = useMemo(() => groupMessages(messages), [messages]);
  const memberUsernames = useMemo(() => members.map((m) => m.username), [members]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isAiTyping]);

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
    <div className="flex-1 overflow-y-auto p-6">
      {grouped.map((msg, i) => {
        const canDelete =
          msg.senderId === currentUserId ||
          (roomOwnerId && roomOwnerId === currentUserId) ||
          currentUserRole === 'ADMIN';
        return (
          <MessageItem
            key={msg.id || i}
            message={msg}
            isSelf={msg.senderId === currentUserId}
            isFirstInGroup={msg.isFirstInGroup}
            isLastInGroup={msg.isLastInGroup}
            canDelete={canDelete}
            memberUsernames={memberUsernames}
            mentionsMe={mentionedMessageIds?.has(msg.id) ?? false}
            onReply={onReply}
            onDelete={onDelete}
          />
        );
      })}

      {isAiTyping && (
        <div className="flex gap-3 max-w-[85%] md:max-w-[75%] my-2">
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
            <div className="bg-slate-900 border border-emerald-500/20 text-slate-100 rounded-2xl rounded-tl-xs px-4 py-3 shadow-lg flex items-center gap-1.5 w-fit">
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
            </div>
          </div>
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  );
};