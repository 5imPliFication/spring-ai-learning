import React, { useEffect, useRef, useState } from 'react';
import { Send, Sparkles, X, CornerUpLeft } from 'lucide-react';
import type { Message } from '../../types';

interface MessageInputProps {
  onSendMessage: (content: string, replyToId?: number) => void;
  disabled?: boolean;
  replyingTo?: Message | null;
  onCancelReply?: () => void;
}

const MAX_INPUT_HEIGHT = 128;

export const MessageInput: React.FC<MessageInputProps> = ({
  onSendMessage,
  disabled = false,
  replyingTo = null,
  onCancelReply,
}) => {
  const [content, setContent] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const autoResize = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, MAX_INPUT_HEIGHT)}px`;
  };

  useEffect(() => {
    autoResize();
  }, [content]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim() || disabled) return;
    onSendMessage(content.trim(), replyingTo?.id ?? undefined);
    setContent('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const insertAiTag = () => {
    if (!content.includes('@ai')) {
      setContent((prev) => (prev ? `${prev} @ai ` : '@ai '));
    }
  };

  return (
    <div className="p-4 bg-slate-900/80 border-t border-slate-800 flex-shrink-0">
      {replyingTo && (
        <div className="flex items-center gap-2 mb-2 px-3 py-2 rounded-xl bg-slate-950 border border-blue-500/30">
          <CornerUpLeft className="w-3.5 h-3.5 text-blue-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <span className="text-[11px] font-semibold text-blue-400 block">
              Replying to {replyingTo.senderName}
            </span>
            <span className="text-xs text-slate-400 truncate block">
              {replyingTo.deleted ? 'Message deleted' : replyingTo.content}
            </span>
          </div>
          <button
            type="button"
            onClick={onCancelReply}
            className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex items-end gap-2">
        <button
          type="button"
          onClick={insertAiTag}
          title="Tag Azura AI"
          className="p-2.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/20 text-emerald-400 text-xs font-semibold flex items-center gap-1 transition-all flex-shrink-0 mb-1"
        >
          <Sparkles className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">@ai</span>
        </button>

        <textarea
          ref={textareaRef}
          rows={1}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder="Type a message... (use @ai to ask Azura) — Shift+Enter for a new line"
          className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors disabled:opacity-50 resize-none overflow-y-auto max-h-32 leading-relaxed"
        />

        <button
          type="submit"
          disabled={disabled || !content.trim()}
          className="p-3 bg-blue-600 hover:bg-blue-500 text-white rounded-xl shadow-lg shadow-blue-600/20 flex items-center justify-center transition-all disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0 mb-1"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};