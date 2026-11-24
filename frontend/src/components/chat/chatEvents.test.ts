import { describe, expect, it } from 'vitest';
import type { ChatMessagePayload, Message } from '../../types';
import { chatReducer, initialChatState, mentionedMessageId, reduceAiTyping, reduceMessages, streamTempId, toastForEvent } from './chatEvents';

const payload = (over: Partial<ChatMessagePayload>): ChatMessagePayload => ({
  senderId: 'u1',
  senderName: 'Alice',
  content: 'hello',
  type: 'CHAT',
  ...over,
});

const message = (over: Partial<Message>): Message => ({
  id: 1,
  senderId: 'u1',
  senderName: 'Alice',
  content: 'hi',
  messageType: 'TEXT',
  createdAt: '2026-01-01T00:00:00Z',
  ...over,
});

describe('reduceMessages', () => {
  it('appends a CHAT message', () => {
    const next = reduceMessages([], payload({ messageId: 5, content: 'yo' }));
    expect(next).toHaveLength(1);
    expect(next[0]).toMatchObject({ id: 5, content: 'yo', senderName: 'Alice', messageType: 'TEXT', deleted: false });
  });

  it('ignores a redelivered CHAT with an id we already have', () => {
    const prev = [message({ id: 5 })];
    expect(reduceMessages(prev, payload({ messageId: 5 }))).toBe(prev);
  });

  it('fills reply context from the loaded message', () => {
    const prev = [message({ id: 1, senderName: 'Bob', content: 'original' })];
    const next = reduceMessages(prev, payload({ messageId: 2, replyToId: 1 }));
    expect(next[1]).toMatchObject({ replyToId: 1, replyToSenderName: 'Bob', replyToContent: 'original' });
  });

  it('labels a reply to a media message and hides deleted reply content', () => {
    const prev = [
      message({ id: 1, content: '', messageType: 'IMAGE', mediaUrl: 'https://cdn/x.png' }),
      message({ id: 2, content: 'gone', deleted: true }),
    ];
    expect(reduceMessages(prev, payload({ messageId: 3, replyToId: 1 }))[2].replyToContent).toBe('[Image]');
    expect(reduceMessages(prev, payload({ messageId: 4, replyToId: 2 }))[2].replyToContent).toBeUndefined();
  });

  it('marks a message deleted on DELETE', () => {
    const prev = [message({ id: 1, content: 'secret' }), message({ id: 2 })];
    const next = reduceMessages(prev, payload({ type: 'DELETE', messageId: 1 }));
    expect(next[0]).toMatchObject({ deleted: true, content: '' });
    expect(next[1]).toBe(prev[1]);
  });

  it('leaves messages untouched for TYPING/IDLE', () => {
    const prev = [message({})];
    expect(reduceMessages(prev, payload({ type: 'TYPING' }))).toBe(prev);
    expect(reduceMessages(prev, payload({ type: 'IDLE' }))).toBe(prev);
  });
});

describe('reduceAiTyping', () => {
  it('follows TYPING and IDLE', () => {
    expect(reduceAiTyping(false, payload({ type: 'TYPING' }))).toBe(true);
    expect(reduceAiTyping(true, payload({ type: 'IDLE' }))).toBe(false);
  });

  it('stops on an AI reply but not on a human message', () => {
    expect(reduceAiTyping(true, payload({ senderId: 'ai-bot' }))).toBe(false);
    expect(reduceAiTyping(true, payload({ senderId: 'u1' }))).toBe(true);
  });
});

describe('mentionedMessageId', () => {
  it('returns the id when the current user is mentioned', () => {
    expect(mentionedMessageId(payload({ messageId: 9, mentionedUserIds: ['me'] }), 'me')).toBe(9);
  });

  it('returns null otherwise', () => {
    expect(mentionedMessageId(payload({ messageId: 9, mentionedUserIds: ['other'] }), 'me')).toBeNull();
    expect(mentionedMessageId(payload({ messageId: 9 }), undefined)).toBeNull();
    expect(mentionedMessageId(payload({ type: 'DELETE', messageId: 9, mentionedUserIds: ['me'] }), 'me')).toBeNull();
  });
});

describe('streaming AI replies', () => {
  const ai = (over: Partial<ChatMessagePayload>): ChatMessagePayload =>
    payload({ senderId: 'ai-bot', senderName: 'Azura', ...over });
  const start = (streamId: string) => ai({ type: 'STREAM_START', streamId, content: '' });
  const delta = (streamId: string, content: string) => ai({ type: 'STREAM_DELTA', streamId, content });

  const run = (events: ChatMessagePayload[], initial: Message[] = []) =>
    events.reduce((msgs, e) => reduceMessages(msgs, e), initial);

  it('STREAM_START adds an empty streaming bubble with a temporary id', () => {
    const msgs = run([start('s1')]);
    expect(msgs).toHaveLength(1);
    expect(msgs[0]).toMatchObject({ content: '', streaming: true, streamId: 's1', senderId: 'ai-bot' });
    expect(msgs[0].id).toBeLessThan(0);
  });

  it('a repeated STREAM_START does not add a second bubble', () => {
    expect(run([start('s1'), start('s1')])).toHaveLength(1);
  });

  it('STREAM_DELTA appends new text', () => {
    const msgs = run([start('s1'), delta('s1', 'Hel'), delta('s1', 'lo '), delta('s1', 'world')]);
    expect(msgs).toHaveLength(1);
    expect(msgs[0]).toMatchObject({ content: 'Hello world', streaming: true });
  });

  it('the final CHAT (stream end) swaps the bubble for the saved message in place', () => {
    const before = [message({ id: 1, content: 'question' })];
    const msgs = run([
      start('s1'),
      delta('s1', 'partial'),
      payload({ messageId: 2, content: 'someone else' }),
      ai({ type: 'CHAT', streamId: 's1', messageId: 50, content: 'final answer' }),
    ], before);
    expect(msgs.map((m) => m.id)).toEqual([1, 50, 2]);
    expect(msgs[1]).toMatchObject({ content: 'final answer', streaming: false });
  });

  it('ignores a late delta after the final message arrived', () => {
    const msgs = run([
      start('s1'),
      delta('s1', 'partial'),
      ai({ type: 'CHAT', streamId: 's1', messageId: 50, content: 'final' }),
      delta('s1', ' more'),
    ]);
    expect(msgs).toHaveLength(1);
    expect(msgs[0]).toMatchObject({ id: 50, content: 'final', streaming: false });
  });

  it('shows the final message when no stream events were received (reconnect)', () => {
    const msgs = run([ai({ type: 'CHAT', streamId: 's1', messageId: 50, content: 'final' })]);
    expect(msgs).toHaveLength(1);
    expect(msgs[0]).toMatchObject({ id: 50, content: 'final', streaming: false });
  });

  it('a late joiner who missed STREAM_START still gets a bubble, completed by the final CHAT', () => {
    const partial = run([delta('s9', 'the rest of it')]);
    expect(partial[0]).toMatchObject({ content: 'the rest of it', streaming: true });
    const done = run([ai({ type: 'CHAT', streamId: 's9', messageId: 70, content: 'All of it: the rest of it' })], partial);
    expect(done).toHaveLength(1);
    expect(done[0]).toMatchObject({ id: 70, content: 'All of it: the rest of it', streaming: false });
  });

  it('STREAM_ERROR drops the bubble but keeps a saved message with the same streamId', () => {
    expect(run([start('s1'), delta('s1', 'partial'), ai({ type: 'STREAM_ERROR', streamId: 's1' })])).toEqual([]);

    const saved = run([
      start('s1'),
      delta('s1', 'partial'),
      ai({ type: 'CHAT', streamId: 's1', messageId: 50, content: 'final' }),
      ai({ type: 'STREAM_ERROR', streamId: 's1' }),
    ]);
    expect(saved).toHaveLength(1);
    expect(saved[0].id).toBe(50);
  });

  it('STREAM_ERROR asks for a toast; other events do not', () => {
    expect(toastForEvent(ai({ type: 'STREAM_ERROR', streamId: 's1' }))).toBe("Azura couldn't finish replying. Please try again.");
    expect(toastForEvent(delta('s1', 'x'))).toBeNull();
    expect(toastForEvent(payload({}))).toBeNull();
  });

  it('keeps concurrent streams separate', () => {
    const msgs = run([start('a'), start('b'), delta('a', 'one'), delta('b', 'two'), delta('a', ' more')]);
    expect(msgs.map((m) => m.content)).toEqual(['one more', 'two']);
    expect(msgs[0].id).not.toBe(msgs[1].id);
  });

  it('hides the typing dots once the stream starts', () => {
    expect(reduceAiTyping(true, start('s1'))).toBe(false);
    expect(reduceAiTyping(true, delta('s1', 'x'))).toBe(false);
  });

  it('temp ids are stable, negative and distinct', () => {
    expect(streamTempId('abc')).toBe(streamTempId('abc'));
    expect(streamTempId('abc')).toBeLessThan(0);
    expect(streamTempId('')).toBeLessThan(0);
    expect(streamTempId('abc')).not.toBe(streamTempId('abd'));
  });
});

describe('chatReducer', () => {
  it('applies one event to messages, typing and mentions together', () => {
    let state = chatReducer(initialChatState('me'), payload({ type: 'TYPING', senderId: 'ai-bot' }));
    expect(state.isAiTyping).toBe(true);

    state = chatReducer(state, payload({ senderId: 'u2', messageId: 7, content: 'hey @me', mentionedUserIds: ['me'] }));
    expect(state.messages.map((m) => m.id)).toEqual([7]);
    expect(state.mentionedMessageIds.has(7)).toBe(true);
    expect(state.isAiTyping).toBe(true);

    state = chatReducer(state, payload({ type: 'STREAM_START', senderId: 'ai-bot', streamId: 's1' }));
    expect(state.isAiTyping).toBe(false);
    state = chatReducer(state, payload({ type: 'STREAM_DELTA', senderId: 'ai-bot', streamId: 's1', content: 'hi' }));
    state = chatReducer(state, payload({ type: 'CHAT', senderId: 'ai-bot', streamId: 's1', messageId: 8, content: 'hi!' }));
    expect(state.messages.map((m) => [m.id, m.content])).toEqual([[7, 'hey @me'], [8, 'hi!']]);
  });

  it('returns the same state object when an event changes nothing', () => {
    const state = initialChatState('me');
    expect(chatReducer(state, payload({ type: 'IDLE' }))).toBe(state);
    expect(chatReducer(state, payload({ type: 'TYPING' })).isAiTyping).toBe(true);
  });

  it('does not highlight mentions of other users or without a viewer', () => {
    const event = payload({ messageId: 3, mentionedUserIds: ['someone-else'] });
    expect(chatReducer(initialChatState('me'), event).mentionedMessageIds.size).toBe(0);
    expect(chatReducer(initialChatState(), payload({ messageId: 4, mentionedUserIds: ['me'] })).mentionedMessageIds.size).toBe(0);
  });

  it('keeps the same mention set when a mention repeats', () => {
    const once = chatReducer(initialChatState('me'), payload({ messageId: 5, mentionedUserIds: ['me'] }));
    const again = chatReducer(once, payload({ type: 'DELETE', messageId: 1 }));
    expect(again.mentionedMessageIds).toBe(once.mentionedMessageIds);
  });
});
