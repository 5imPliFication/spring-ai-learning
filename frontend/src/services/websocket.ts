import { Client } from '@stomp/stompjs';
import type { StompSubscription, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { ChatMessagePayload, AppNotification } from '../types';

export class WebSocketService {
  private client: Client | null = null;
  private subscriptions = new Map<string, StompSubscription>();
  private pendingSubscriptions = new Map<string, (frame: any) => void>();
  private isConnected = false;

  public connect(token?: string, onConnected?: () => void, onError?: (err: any) => void): void {
    if (this.client && this.isConnected) {
      if (onConnected) onConnected();
      return;
    }

    const authToken = token || localStorage.getItem('token');
    const connectHeaders: StompHeaders = {};
    if (authToken) {
      connectHeaders.Authorization = `Bearer ${authToken}`;
    }

    const socket = new SockJS('http://localhost:8080/ws-chat');
    this.client = new Client({
      webSocketFactory: () => socket,
      connectHeaders,
      reconnectDelay: 5000,
      onConnect: () => {
        this.isConnected = true;
        console.log('[WebSocket] Connected');
        this.flushPendingSubscriptions();
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

  private flushPendingSubscriptions(): void {
    if (!this.client || !this.isConnected) return;
    this.pendingSubscriptions.forEach((callback, topic) => {
      if (!this.subscriptions.has(topic)) {
        this.subscriptions.set(topic, this.client!.subscribe(topic, callback));
      }
    });
    this.pendingSubscriptions.clear();
  }

  private subscribe(topic: string, callback: (frame: any) => void): void {
    if (this.subscriptions.has(topic)) return;
    if (!this.client || !this.isConnected) {
      this.pendingSubscriptions.set(topic, callback);
      return;
    }
    this.subscriptions.set(topic, this.client.subscribe(topic, callback));
  }

  public subscribeToRoom(roomId: string, onMessageReceived: (payload: ChatMessagePayload) => void): void {
    this.subscribe(`/topic/room/${roomId}`, (frame) => {
      try {
        const payload: ChatMessagePayload = JSON.parse(frame.body);
        onMessageReceived(payload);
      } catch (err) {
        console.error('[WebSocket] Error parsing message payload:', err);
      }
    });
  }

  public unsubscribeRoom(roomId: string): void {
    const topic = `/topic/room/${roomId}`;
    const sub = this.subscriptions.get(topic);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(topic);
    }
    this.pendingSubscriptions.delete(topic);
  }

  public subscribeToUserNotifications(userId: string, onNotification: (notification: AppNotification) => void): void {
    const topic = `/topic/user/${userId}/notifications`;
    this.subscribe(topic, (frame) => {
      try {
        const notification: AppNotification = JSON.parse(frame.body);
        onNotification(notification);
      } catch (err) {
        console.error('[WebSocket] Error parsing notification payload:', err);
      }
    });
  }

  public isSubscribed(roomId: string): boolean {
    return this.subscriptions.has(`/topic/room/${roomId}`) || this.pendingSubscriptions.has(`/topic/room/${roomId}`);
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