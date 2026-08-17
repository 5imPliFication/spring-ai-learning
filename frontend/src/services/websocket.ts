import { Client } from '@stomp/stompjs';
import type { StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { ChatMessagePayload } from '../types';

export class WebSocketService {
  private client: Client | null = null;
  private subscriptions = new Map<string, StompSubscription>();
  private isConnected = false;

  public connect(onConnected?: () => void, onError?: (err: any) => void): void {
    if (this.client && this.isConnected) {
      if (onConnected) onConnected();
      return;
    }

    const socket = new SockJS('http://localhost:8080/ws-chat');
    this.client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        this.isConnected = true;
        console.log('[WebSocket] Connected');
        if (onConnected) onConnected();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP Error:', frame);
        if (onError) onError(frame);
      },
      onWebSocketClose: () => {
        this.isConnected = false;
        console.log('[WebSocket] Connection closed');
      }
    });

    this.client.activate();
  }

  public subscribeToRoom(roomId: string, onMessageReceived: (payload: ChatMessagePayload) => void): void {
    if (!this.client || !this.isConnected) {
      console.warn('[WebSocket] Cannot subscribe: client not connected');
      return;
    }

    if (this.subscriptions.has(roomId)) {
      return;
    }

    const sub = this.client.subscribe(`/topic/room/${roomId}`, (frame) => {
      try {
        const payload: ChatMessagePayload = JSON.parse(frame.body);
        onMessageReceived(payload);
      } catch (err) {
        console.error('[WebSocket] Error parsing message payload:', err);
      }
    });

    this.subscriptions.set(roomId, sub);
  }

  public unsubscribeRoom(roomId: string): void {
    const sub = this.subscriptions.get(roomId);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(roomId);
    }
  }

  public isSubscribed(roomId: string): boolean {
    return this.subscriptions.has(roomId);
  }

  public sendMessage(roomId: string, payload: ChatMessagePayload): void {
    if (!this.client || !this.isConnected) {
      console.error('[WebSocket] Cannot send message: not connected');
      return;
    }

    this.client.publish({
      destination: `/app/chat/${roomId}`,
      body: JSON.stringify(payload),
    });
  }

  public disconnect(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.isConnected = false;
  }
}

export const wsService = new WebSocketService();