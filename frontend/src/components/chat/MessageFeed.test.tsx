// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ChatMessagePayload, Message } from '../../types';
import { MessageFeed } from './MessageFeed';
import { reduceMessages } from './chatEvents';

const ai = (over: Partial<ChatMessagePayload>): ChatMessagePayload => ({
  senderId: 'ai-bot', senderName: 'Azura', content: '', type: 'CHAT', ...over,
});
const question: Message = {
  id: 1, senderId: 'me', senderName: 'Me', content: '@ai when?', messageType: 'TEXT', createdAt: '2026-11-24T10:00:00Z',
};

let scrollSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  scrollSpy = vi.fn();
  Element.prototype.scrollIntoView = scrollSpy as unknown as Element['scrollIntoView'];
});
afterEach(cleanup);

function feed(messages: Message[]) {
  return <MessageFeed messages={messages} currentUserId="me" isAiTyping={false} onReply={() => {}} onDelete={() => {}} />;
}

describe('MessageFeed while an AI reply streams', () => {
  it('shows a growing bubble with a cursor and no actions, then the saved message with actions', () => {
    let msgs = reduceMessages([question], ai({ type: 'STREAM_START', streamId: 's1' }));
    const { container, rerender } = render(feed(msgs));
    expect(container.querySelector('.stream-cursor')).not.toBeNull();

    msgs = reduceMessages(msgs, ai({ type: 'STREAM_DELTA', streamId: 's1', content: 'Friday ' }));
    msgs = reduceMessages(msgs, ai({ type: 'STREAM_DELTA', streamId: 's1', content: 'at 5pm' }));
    rerender(feed(msgs));
    expect(screen.getByText('Friday at 5pm')).toBeTruthy();
    expect(container.querySelector('.stream-cursor')).not.toBeNull();
    // Only the user's own question has a Reply action; the streaming bubble has none yet.
    expect(screen.getAllByText('Reply')).toHaveLength(1);

    msgs = reduceMessages(msgs, ai({ type: 'CHAT', streamId: 's1', messageId: 42, content: 'Friday at 5pm works!' }));
    rerender(feed(msgs));
    expect(screen.getByText('Friday at 5pm works!')).toBeTruthy();
    expect(screen.queryByText('Friday at 5pm')).toBeNull();
    expect(container.querySelector('.stream-cursor')).toBeNull();
    expect(screen.getAllByText('Reply')).toHaveLength(2);
  });

  it('removes the bubble on STREAM_ERROR', () => {
    let msgs = reduceMessages([question], ai({ type: 'STREAM_START', streamId: 's1' }));
    msgs = reduceMessages(msgs, ai({ type: 'STREAM_DELTA', streamId: 's1', content: 'half an ans' }));
    const { container, rerender } = render(feed(msgs));
    expect(screen.getByText('half an ans')).toBeTruthy();

    rerender(feed(reduceMessages(msgs, ai({ type: 'STREAM_ERROR', streamId: 's1' }))));
    expect(screen.queryByText('half an ans')).toBeNull();
    expect(container.querySelector('.stream-cursor')).toBeNull();
  });

  it('follows the stream when at the bottom, jumping instead of smooth-scrolling', () => {
    let msgs = reduceMessages([question], ai({ type: 'STREAM_START', streamId: 's1' }));
    const { rerender } = render(feed(msgs));
    scrollSpy.mockClear();

    msgs = reduceMessages(msgs, ai({ type: 'STREAM_DELTA', streamId: 's1', content: 'more' }));
    rerender(feed(msgs));
    expect(scrollSpy).toHaveBeenCalledWith({ behavior: 'auto' });
  });

  it('does not yank the reader down while they are scrolled up', () => {
    let msgs = reduceMessages([question], ai({ type: 'STREAM_START', streamId: 's1' }));
    const { container, rerender } = render(feed(msgs));
    const scroller = container.firstElementChild as HTMLElement;
    Object.defineProperty(scroller, 'scrollHeight', { value: 2000, configurable: true });
    Object.defineProperty(scroller, 'clientHeight', { value: 500, configurable: true });
    scroller.scrollTop = 100; // far from the bottom
    fireEvent.scroll(scroller);
    scrollSpy.mockClear();

    msgs = reduceMessages(msgs, ai({ type: 'STREAM_DELTA', streamId: 's1', content: 'more' }));
    rerender(feed(msgs));
    expect(scrollSpy).not.toHaveBeenCalled();
  });
});
