import type { ChatMessagePayload, Message } from '../../types';

/**
 * Pure helpers for applying room WebSocket events to chat state.
 * Kept free of React so they can be unit tested; ChatLayout calls them through functional setters.
 */

export const AI_SENDER_ID = 'ai-bot';

export const mediaLabel = (m: Message | undefined): string | undefined => {
  if (!m?.mediaUrl) return undefined;
  switch (m.messageType) {
    case 'IMAGE':
      return '[Image]';
    case 'AUDIO':
      return '[Audio]';
    default:
      return '[File]';
  }
};

/** Builds the feed message for a CHAT event, resolving reply context from what's already loaded. */
export function toMessage(payload: ChatMessagePayload, existing: Message[]): Message {
  const replyMsg = payload.replyToId != null
    ? existing.find((m) => m.id === payload.replyToId)
    : undefined;
  return {
    id: payload.messageId ?? Date.now(),
    senderId: payload.senderId,
    senderName: payload.senderName,
    content: payload.content || '',
    messageType: (payload.messageType as Message['messageType']) || 'TEXT',
    mediaUrl: payload.mediaUrl || undefined,
    createdAt: payload.timestamp || new Date().toISOString(),
    replyToId: payload.replyToId ?? undefined,
    replyToSenderName: replyMsg?.senderName,
    replyToContent: replyMsg?.deleted ? undefined : (replyMsg?.content || mediaLabel(replyMsg)),
    deleted: false,
    actionCard: payload.actionCard,
  };
}

/**
 * A stable temporary id for a streaming bubble: negative, so it can never collide with a real message id,
 * and derived from the streamId so every update for the same stream finds the same bubble.
 */
export function streamTempId(streamId: string): number {
  let hash = 0;
  for (let i = 0; i < streamId.length; i++) {
    hash = (Math.imul(31, hash) + streamId.charCodeAt(i)) | 0;
  }
  return -(Math.abs(hash) + 1);
}

function streamingBubble(payload: ChatMessagePayload, existing: Message[], content: string): Message {
  return {
    ...toMessage(payload, existing),
    id: streamTempId(payload.streamId as string),
    content,
    streamId: payload.streamId,
    streaming: true,
  };
}

/** The message list after this event. Returns the same array when the event doesn't touch messages. */
export function reduceMessages(prev: Message[], payload: ChatMessagePayload): Message[] {
  switch (payload.type) {
    case 'DELETE':
      if (payload.messageId == null) return prev;
      return prev.map((m) => (m.id === payload.messageId ? { ...m, deleted: true, content: '' } : m));

    case 'STREAM_START': {
      if (!payload.streamId || prev.some((m) => m.streamId === payload.streamId)) return prev;
      return [...prev, streamingBubble(payload, prev, '')];
    }

    case 'STREAM_DELTA': {
      if (!payload.streamId) return prev;
      const idx = prev.findIndex((m) => m.streamId === payload.streamId);
      if (idx < 0) {
        // Joined mid-stream and missed STREAM_START: show what we have; the final CHAT fills in the rest.
        return [...prev, streamingBubble(payload, prev, payload.content || '')];
      }
      // A delta after the final CHAT replaced the bubble must not touch the saved message.
      if (!prev[idx].streaming) return prev;
      const next = prev.slice();
      next[idx] = { ...prev[idx], content: prev[idx].content + (payload.content || '') };
      return next;
    }

    case 'STREAM_ERROR':
      if (!payload.streamId) return prev;
      if (!prev.some((m) => m.streaming && m.streamId === payload.streamId)) return prev;
      return prev.filter((m) => !(m.streaming && m.streamId === payload.streamId));

    case 'CHAT': {
      // A redelivered event (e.g. after reconnect) must not duplicate a message we already have.
      if (payload.messageId != null && prev.some((m) => m.id === payload.messageId)) return prev;
      const message = toMessage(payload, prev);
      if (payload.streamId) {
        const idx = prev.findIndex((m) => m.streamId === payload.streamId);
        if (idx >= 0) {
          // Swap the streamed bubble for the saved message in place, keeping its position in the feed.
          const next = prev.slice();
          next[idx] = { ...message, streamId: payload.streamId, streaming: false };
          return next;
        }
        return [...prev, { ...message, streamId: payload.streamId, streaming: false }];
      }
      return [...prev, message];
    }

    default:
      return prev;
  }
}

/** Whether the AI typing indicator should show after this event. */
export function reduceAiTyping(current: boolean, payload: ChatMessagePayload): boolean {
  switch (payload.type) {
    case 'TYPING':
      return true;
    case 'IDLE':
    case 'STREAM_START': // the streaming bubble replaces the dots
    case 'STREAM_DELTA':
      return false;
    case 'CHAT':
      return payload.senderId === AI_SENDER_ID ? false : current;
    default:
      return current;
  }
}

/** The message id to highlight if this event mentions the current user, otherwise null. */
export function mentionedMessageId(payload: ChatMessagePayload, currentUserId: string | undefined): number | null {
  if (payload.type !== 'CHAT' || !currentUserId || payload.messageId == null) return null;
  return payload.mentionedUserIds?.includes(currentUserId) ? payload.messageId : null;
}

/** Everything a room's WebSocket events can change. */
export interface ChatState {
  messages: Message[];
  isAiTyping: boolean;
  /** Messages that mention the viewer, for highlighting. */
  mentionedMessageIds: Set<number>;
  /** The viewer; needed to tell whether a message mentions them. */
  currentUserId?: string;
}

export const initialChatState = (currentUserId?: string): ChatState => ({
  messages: [],
  isAiTyping: false,
  mentionedMessageIds: new Set(),
  currentUserId,
});

/**
 * The single entry point for room WebSocket events: the chat state after this event.
 * Pure; returns the same object when nothing changed, so React can skip re-rendering.
 */
export function chatReducer(state: ChatState, payload: ChatMessagePayload): ChatState {
  const messages = reduceMessages(state.messages, payload);
  const isAiTyping = reduceAiTyping(state.isAiTyping, payload);
  const mentionedId = mentionedMessageId(payload, state.currentUserId);
  const mentionedMessageIds = mentionedId != null && !state.mentionedMessageIds.has(mentionedId)
    ? new Set(state.mentionedMessageIds).add(mentionedId)
    : state.mentionedMessageIds;

  if (messages === state.messages && isAiTyping === state.isAiTyping
      && mentionedMessageIds === state.mentionedMessageIds) {
    return state;
  }
  return { ...state, messages, isAiTyping, mentionedMessageIds };
}

/** Toast text for room events everyone should hear about, or null. */
export function toastForEvent(payload: ChatMessagePayload): string | null {
  if (payload.type === 'STREAM_ERROR') {
    return `${payload.senderName || 'The AI'} couldn't finish replying. Please try again.`;
  }
  return null;
}
