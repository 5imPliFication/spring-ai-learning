// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Toast } from './Toast';

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('Toast', () => {
  it('renders nothing without a toast', () => {
    const { container } = render(<Toast toast={null} onDismiss={() => {}} />);
    expect(container.innerHTML).toBe('');
  });

  it('shows the message as a polite status and dismisses on click', () => {
    const onDismiss = vi.fn();
    render(<Toast toast={{ id: 1, message: "Azura couldn't finish replying." }} onDismiss={onDismiss} />);
    expect(screen.getByRole('status').textContent).toContain("Azura couldn't finish replying.");
    fireEvent.click(screen.getByLabelText('Dismiss notification'));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it('dismisses itself after the duration', () => {
    vi.useFakeTimers();
    const onDismiss = vi.fn();
    render(<Toast toast={{ id: 1, message: 'Slow down' }} onDismiss={onDismiss} durationMs={6000} />);
    act(() => { vi.advanceTimersByTime(5999); });
    expect(onDismiss).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(1); });
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
