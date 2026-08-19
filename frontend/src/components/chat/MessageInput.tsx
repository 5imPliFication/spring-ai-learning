import React, { useEffect, useRef, useState } from 'react';
import { Send, Sparkles, X, CornerUpLeft, Paperclip, Loader2, FileText, AlertCircle } from 'lucide-react';
import type { Message, MessageMedia } from '../../types';
import { mediaApi } from '../../services/api';

interface MessageInputProps {
  onSendMessage: (content: string, replyToId?: number, media?: MessageMedia) => void;
  disabled?: boolean;
  replyingTo?: Message | null;
  onCancelReply?: () => void;
}

const MAX_INPUT_HEIGHT = 128;
const MAX_FILE_SIZE = 25 * 1024 * 1024;
const ACCEPTED_TYPES = 'image/*,audio/*,video/*,.pdf,.txt,.zip,.doc,.docx,.xls,.xlsx,.json,.js';

export const MessageInput: React.FC<MessageInputProps> = ({
  onSendMessage,
  disabled = false,
  replyingTo = null,
  onCancelReply,
}) => {
  const [content, setContent] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
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
    if (!content.trim() && !selectedFile) return;

    if (!selectedFile) {
      onSendMessage(content.trim(), replyingTo?.id ?? undefined);
      setContent('');
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

  const canSend = !disabled && !isUploading && (content.trim().length > 0 || !!selectedFile);

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
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled || isUploading}
          placeholder="Type a message... (use @ai to ask Azura) — Shift+Enter for a new line"
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