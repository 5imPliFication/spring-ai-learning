import React from 'react';
import type { Message } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Sparkles, CornerUpLeft, Trash2, FileText, Download } from 'lucide-react';

interface MessageItemProps {
  message: Message;
  isSelf: boolean;
  isFirstInGroup: boolean;
  isLastInGroup: boolean;
  canDelete?: boolean;
  onReply?: (message: Message) => void;
  onDelete?: (messageId: number) => void;
}

const QuoteBlock: React.FC<{ message: Message }> = ({ message }) => {
  if (!message.replyToId) return null;
  return (
    <div className="flex flex-col gap-0.5 mb-1 px-3 py-1.5 rounded-lg bg-slate-800/60 border-l-2 border-blue-500 max-w-full overflow-hidden">
      <span className="text-[10px] font-semibold text-blue-400">
        {message.replyToSenderName ? `Reply to ${message.replyToSenderName}` : 'Reply'}
      </span>
      {message.replyToContent ? (
        <span className="text-xs text-slate-400 line-clamp-2 whitespace-pre-wrap break-words">
          {message.replyToContent}
        </span>
      ) : (
        <span className="text-xs text-slate-500 italic">Message deleted</span>
      )}
    </div>
  );
};

const fileNameFromUrl = (url: string): string => {
  try {
    const name = decodeURIComponent(url.split('/').pop() || '');
    const cleaned = name.replace(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-/, '');
    return cleaned || 'file';
  } catch {
    return 'file';
  }
};

const isVideoUrl = (url: string): boolean => {
  return /\.(mp4|webm|mov|m4v|ogg|ogv)$/i.test(url.split('?')[0]);
};

const MediaContent: React.FC<{ message: Message }> = ({ message }) => {
  const { mediaUrl, messageType, content } = message;
  if (!mediaUrl) return null;

  if (messageType === 'IMAGE') {
    return (
      <a href={mediaUrl} target="_blank" rel="noreferrer" className="block">
        <img
          src={mediaUrl}
          alt={content || 'Image'}
          loading="lazy"
          className="max-w-full max-h-80 rounded-xl object-cover bg-black/30"
        />
      </a>
    );
  }

  if (messageType === 'AUDIO') {
    return (
      <audio controls src={mediaUrl} className="w-64 max-w-full h-10" preload="metadata">
        Your browser does not support audio playback.
      </audio>
    );
  }

  if (isVideoUrl(mediaUrl)) {
    return (
      <video controls src={mediaUrl} className="max-w-full max-h-80 rounded-xl bg-black/30" preload="metadata">
        Your browser does not support video playback.
      </video>
    );
  }

  return (
    <a
      href={mediaUrl}
      target="_blank"
      rel="noreferrer"
      className="flex items-center gap-3 p-3 rounded-xl bg-slate-950/50 border border-slate-700/60 hover:border-slate-500 transition-colors max-w-xs"
    >
      <div className="w-10 h-10 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300 shrink-0">
        <FileText className="w-5 h-5" />
      </div>
      <div className="flex-1 min-w-0">
        <span className="text-xs font-medium text-slate-200 truncate block">{fileNameFromUrl(mediaUrl)}</span>
        <span className="text-[10px] text-slate-500">Click to open</span>
      </div>
      <Download className="w-4 h-4 text-slate-400 shrink-0" />
    </a>
  );
};

export const MessageItem: React.FC<MessageItemProps> = ({
  message,
  isSelf,
  isFirstInGroup,
  isLastInGroup,
  canDelete = false,
  onReply,
  onDelete,
}) => {
  const isAi = message.senderId === 'ai-bot';
  const showTime = isLastInGroup;
  const margin = isFirstInGroup ? 'mt-2' : 'mt-0.5';

  const formatTime = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    } catch {
      return '';
    }
  };

  const actions = (
    <div className="absolute -top-3 right-0 z-10 hidden group-hover:flex items-center gap-1 rounded-lg bg-slate-900/95 border border-slate-800/80 p-1 shadow-lg">
      {onReply && (
        <button
          onClick={() => onReply(message)}
          className="px-2 py-1 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white text-[10px] font-semibold flex items-center gap-1 transition-colors"
        >
          <CornerUpLeft className="w-3 h-3" />
          Reply
        </button>
      )}
      {onDelete && canDelete && (
        <button
          onClick={() => onDelete(message.id)}
          className="px-2 py-1 rounded-md bg-red-500/10 hover:bg-red-500/20 text-red-400 text-[10px] font-semibold flex items-center gap-1 transition-colors"
        >
          <Trash2 className="w-3 h-3" />
          Delete
        </button>
      )}
    </div>
  );

  if (message.deleted) {
    if (isSelf) {
      return (
        <div className={`flex flex-col items-end ${margin} group relative`}>
          {actions}
          <div className="max-w-[75%] md:max-w-[65%] bg-slate-900/60 text-slate-500 italic border border-slate-800 rounded-2xl rounded-tr-xs px-4 py-2.5 text-sm">
            Message deleted
          </div>
          {showTime && (
            <span className="text-[10px] text-slate-500 mt-1 mr-1 opacity-0 group-hover:opacity-100 transition-opacity">
              {formatTime(message.createdAt)}
            </span>
          )}
        </div>
      );
    }
    return (
      <div className={`flex gap-3 max-w-[75%] md:max-w-[65%] ${margin} group relative`}>
        {actions}
        {isFirstInGroup ? <Avatar name={message.senderName} size="md" /> : <div className="w-9 shrink-0" />}
        <div className="flex flex-col">
          {isFirstInGroup && (
            <span className="text-xs font-medium text-slate-500 mb-1 ml-1">{message.senderName}</span>
          )}
          <div className="bg-slate-900/60 text-slate-500 italic border border-slate-800 rounded-2xl rounded-tl-xs px-4 py-2.5 text-sm">
            Message deleted
          </div>
        </div>
      </div>
    );
  }

  if (isSelf) {
    return (
      <div className={`flex flex-col items-end ${margin} group relative`}>
        {actions}
        <QuoteBlock message={message} />
        <div className="max-w-[75%] md:max-w-[65%] bg-blue-600 text-white rounded-2xl rounded-tr-xs px-4 py-2.5 shadow-md text-sm leading-relaxed">
          {message.mediaUrl && <MediaContent message={message} />}
          {message.content && (
            <div className="word-break whitespace-pre-wrap mt-1.5">{message.content}</div>
          )}
        </div>
        {showTime && (
          <span className="text-[10px] text-slate-500 mt-1 mr-1 opacity-0 group-hover:opacity-100 transition-opacity">
            {formatTime(message.createdAt)}
          </span>
        )}
      </div>
    );
  }

  if (isAi) {
    return (
      <div className={`flex gap-3 max-w-[85%] md:max-w-[75%] ${margin} group relative`}>
        {actions}
        {isFirstInGroup ? <Avatar name="Azura" isAi size="md" /> : <div className="w-9 shrink-0" />}
        <div className="flex flex-col">
          {isFirstInGroup && (
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-semibold text-emerald-400 flex items-center gap-1">
                Azura
                <Sparkles className="w-3 h-3 text-emerald-400" />
              </span>
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
                AI
              </span>
            </div>
          )}
          <QuoteBlock message={message} />
          <div
            className={`bg-slate-900 border border-emerald-500/20 text-slate-100 rounded-2xl rounded-tl-xs px-4 py-3 shadow-lg text-sm leading-relaxed ${
              !isFirstInGroup ? 'rounded-tl-lg' : ''
            }`}
          >
            {message.mediaUrl && <MediaContent message={message} />}
            {message.content && (
              <div className="whitespace-pre-wrap mt-1.5">{message.content}</div>
            )}
          </div>
          {showTime && (
            <span className="text-[10px] text-slate-500 mt-1 ml-1 opacity-0 group-hover:opacity-100 transition-opacity">
              {formatTime(message.createdAt)}
            </span>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className={`flex gap-3 max-w-[75%] md:max-w-[65%] ${margin} group relative`}>
      {actions}
      {isFirstInGroup ? <Avatar name={message.senderName} size="md" /> : <div className="w-9 shrink-0" />}
      <div className="flex flex-col">
        {isFirstInGroup && (
          <span className="text-xs font-medium text-slate-400 mb-1 ml-1">{message.senderName}</span>
        )}
        <QuoteBlock message={message} />
        <div className="bg-slate-800/90 text-slate-100 border border-slate-700/50 rounded-2xl rounded-tl-xs px-4 py-2.5 shadow-md text-sm leading-relaxed">
          {message.mediaUrl && <MediaContent message={message} />}
          {message.content && (
            <div className="word-break whitespace-pre-wrap mt-1.5">{message.content}</div>
          )}
        </div>
        {showTime && (
          <span className="text-[10px] text-slate-500 mt-1 ml-1 opacity-0 group-hover:opacity-100 transition-opacity">
            {formatTime(message.createdAt)}
          </span>
        )}
      </div>
    </div>
  );
};