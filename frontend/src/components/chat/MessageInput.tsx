import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Send, Sparkles, X, CornerUpLeft, Paperclip, Loader2, FileText, AlertCircle, AtSign } from 'lucide-react';
import type { Message, MessageMedia, RoomMember } from '../../types';
import { mediaApi } from '../../services/api';
import { Avatar } from '../ui/Avatar';

interface MessageInputProps {
  onSendMessage: (content: string, replyToId?: number, media?: MessageMedia) => void;
  disabled?: boolean;
  replyingTo?: Message | null;
  onCancelReply?: () => void;
  members?: RoomMember[];
}

const MAX_INPUT_HEIGHT = 128;
const MAX_FILE_SIZE = 25 * 1024 * 1024;
const ACCEPTED_TYPES = 'image/*,audio/*,video/*,.pdf,.txt,.zip,.doc,.docx,.xls,.xlsx,.json,.js';
const MENTION_QUERY_REGEX = /(^|\s)@([a-zA-Z0-9_.-]*)$/;
const MAX_VISIBLE_SUGGESTIONS = 6;

interface MentionState {
  start: number;
  query: string;
}

export const MessageInput: React.FC<MessageInputProps> = ({
  onSendMessage,
  disabled = false,
  replyingTo = null,
  onCancelReply,
  members = [],
}) => {
  const [content, setContent] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [mentionState, setMentionState] = useState<MentionState | null>(null);
  const [mentionHighlight, setMentionHighlight] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const autoResize = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, MAX_INPUT_HEIGHT)}px`;
  };

  useEffect(() => {
    autoResize();
  }, [content]);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const mentionCandidates = useMemo(() => {
    if (!mentionState) return [];
    const q = mentionState.query.toLowerCase();
    return members
      .filter((m) =>
        q.length === 0 ||
        m.username.toLowerCase().includes(q) ||
        m.displayName.toLowerCase().includes(q)
      )
      .slice(0, MAX_VISIBLE_SUGGESTIONS);
  }, [members, mentionState]);

  const syncMentionState = (value: string, caretPos: number) => {
    const upto = value.slice(0, caretPos);
    const match = MENTION_QUERY_REGEX.exec(upto);
    if (match) {
      setMentionState({ start: caretPos - match[2].length - 1, query: match[2] });
      setMentionHighlight(0);
    } else {
      setMentionState(null);
    }
  };

  const selectMember = (member: RoomMember) => {
    const el = textareaRef.current;
    if (!el || !mentionState) return;
    const caret = el.selectionStart ?? content.length;
    const insertText = `@${member.username} `;
    const before = content.slice(0, Math.max(mentionState.start, 0));
    const after = content.slice(caret);
    const next = before + insertText + after;

    setContent(next);
    setMentionState(null);
    requestAnimationFrame(() => {
      const pos = (before + insertText).length;
      el.focus();
      el.setSelectionRange(pos, pos);
    });
  };

  const closeMentionMenu = () => setMentionState(null);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > MAX_FILE_SIZE) {
      setUploadError('File is too large. Maximum size is 25MB.');
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setUploadError(null);
    setSelectedFile(file);
    setPreviewUrl(file.type.startsWith('image/') ? URL.createObjectURL(file) : null);
  };

  const clearFile = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setSelectedFile(null);
    setPreviewUrl(null);
    setUploadError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (disabled || isUploading) return;
    if (mentionState && mentionCandidates.length > 0) return;
    if (!content.trim() && !selectedFile) return;

    if (!selectedFile) {
      onSendMessage(content.trim(), replyingTo?.id ?? undefined);
      setContent('');
      closeMentionMenu();
      if (replyingTo) onCancelReply?.();
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);
    setUploadError(null);
    try {
      const resp = await mediaApi.requestPresignedUpload({
        fileName: selectedFile.name,
        contentType: selectedFile.type || 'application/octet-stream',
        size: selectedFile.size,
      });
      await mediaApi.uploadToR2(resp.uploadUrl, selectedFile, setUploadProgress);
      onSendMessage(content.trim(), replyingTo?.id ?? undefined, {
        messageType: resp.messageType,
        mediaUrl: resp.mediaUrl,
      });
      setContent('');
      closeMentionMenu();
      clearFile();
      if (replyingTo) onCancelReply?.();
    } catch (err: any) {
      console.error('Upload failed:', err);
      setUploadError(err?.response?.data?.message || 'Upload failed. Please try again.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (mentionState && mentionCandidates.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setMentionHighlight((i) => (i + 1) % mentionCandidates.length);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setMentionHighlight((i) => (i - 1 + mentionCandidates.length) % mentionCandidates.length);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        selectMember(mentionCandidates[mentionHighlight]);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        closeMentionMenu();
        return;
      }
    }

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

  const canSend =
    !disabled &&
    !isUploading &&
    !(mentionState && mentionCandidates.length > 0) &&
    (content.trim().length > 0 || !!selectedFile);

  return (
    <div className="p-4 bg-slate-900/80 border-t border-slate-800 flex-shrink-0">
      {/* Mention Autocomplete */}
      {mentionState && mentionCandidates.length > 0 && (
        <div className="relative mb-1">
          <div className="absolute bottom-1 left-0 z-20 w-full sm:w-80 rounded-xl bg-slate-950 border border-slate-700 shadow-2xl overflow-hidden">
            <div className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-500 border-b border-slate-800 flex items-center gap-1.5">
              <AtSign className="w-3 h-3" />
              Members
            </div>
            <div className="max-h-56 overflow-y-auto py-1">
              {mentionCandidates.map((m, idx) => (
                <button
                  key={m.userId}
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => selectMember(m)}
                  onMouseEnter={() => setMentionHighlight(idx)}
                  className={`w-full flex items-center gap-2 px-3 py-2 text-left transition-colors ${
                    idx === mentionHighlight ? 'bg-blue-600/20' : 'hover:bg-slate-900'
                  }`}
                >
                  <Avatar name={m.displayName} src={m.avatarUrl} size="xs" />
                  <span className="text-xs font-medium text-white truncate">{m.displayName}</span>
                  <span className="text-[10px] text-slate-500 truncate">@{m.username}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {replyingTo && (
        <div className="flex items-center gap-2 mb-2 px-3 py-2 rounded-xl bg-slate-950 border border-blue-500/30">
          <CornerUpLeft className="w-3.5 h-3.5 text-blue-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <span className="text-[11px] font-semibold text-blue-400 block">
              Replying to {replyingTo.senderName}
            </span>
            <span className="text-xs text-slate-400 truncate block">
              {replyingTo.deleted ? 'Message deleted' : (replyingTo.content || '[Media]')}
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

      {(selectedFile || isUploading) && (
        <div className="flex items-center gap-2 mb-2 px-3 py-2 rounded-xl bg-slate-950 border border-slate-700">
          {previewUrl ? (
            <img
              src={previewUrl}
              alt="preview"
              className="w-10 h-10 rounded-lg object-cover shrink-0"
            />
          ) : (
            <div className="w-10 h-10 rounded-lg bg-slate-800 flex items-center justify-center text-slate-400 shrink-0">
              <FileText className="w-4 h-4" />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <span className="text-xs font-medium text-slate-200 truncate block">
              {selectedFile?.name}
            </span>
            <span className="text-[10px] text-slate-500">
              {isUploading
                ? `Uploading... ${uploadProgress}%`
                : `${((selectedFile?.size ?? 0) / 1024 / 1024).toFixed(2)} MB`}
            </span>
            {isUploading && (
              <div className="h-1 mt-1 rounded-full bg-slate-800 overflow-hidden">
                <div
                  className="h-full bg-blue-500 transition-all duration-300"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}
          </div>
          {!isUploading && (
            <button
              type="button"
              onClick={clearFile}
              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      )}

      {uploadError && (
        <div className="flex items-center gap-2 mb-2 px-3 py-2 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          <span>{uploadError}</span>
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
          onChange={(e) => {
            const val = e.target.value;
            setContent(val);
            syncMentionState(val, e.target.selectionStart ?? val.length);
          }}
          onClick={(e) => syncMentionState(content, e.currentTarget.selectionStart ?? content.length)}
          onKeyDown={handleKeyDown}
          onBlur={() => setTimeout(closeMentionMenu, 120)}
          disabled={disabled || isUploading}
          placeholder="Type a message... (@ to tag members, @ai to ask Azura) — Shift+Enter for a new line"
          className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors disabled:opacity-50 resize-none overflow-y-auto max-h-32 leading-relaxed"
        />

        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_TYPES}
          onChange={handleFileSelect}
          className="hidden"
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled || isUploading}
          title="Attach file"
          className="p-3 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl flex items-center justify-center transition-all disabled:opacity-50 flex-shrink-0 mb-1"
        >
          <Paperclip className="w-4 h-4" />
        </button>

        <button
          type="submit"
          disabled={!canSend}
          className="p-3 bg-blue-600 hover:bg-blue-500 text-white rounded-xl shadow-lg shadow-blue-600/20 flex items-center justify-center transition-all disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0 mb-1"
        >
          {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
        </button>
      </form>
    </div>
  );
};
