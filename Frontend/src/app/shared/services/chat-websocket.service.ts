import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable, BehaviorSubject } from 'rxjs';

export interface WsChatMessageEvent {
  event: string;
  data: {
    id: string;
    senderId: string;
    receiverId: string;
    message?: string;
    messageType?: string;
    filePath?: string;
    fileName?: string;
    fileSize?: number;
    fileContentType?: string;
    status?: string;
    createdAt?: string;
  };
}

@Injectable({
  providedIn: 'root'
})
export class ChatWebSocketService implements OnDestroy {
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<WsChatMessageEvent>();
  private connectedSubject = new BehaviorSubject<boolean>(false);
  private pingIntervalId: any = null;
  private reconnectTimeoutId: any = null;
  private isExplicitlyClosed = false;

  public messages$: Observable<WsChatMessageEvent> = this.messageSubject.asObservable();
  public connected$: Observable<boolean> = this.connectedSubject.asObservable();

  constructor() {
    this.connect();
  }

  public connect(): void {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    if (!token) {
      console.warn('[WS] No authentication token found. Skipping WebSocket connection.');
      return;
    }

    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isExplicitlyClosed = false;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.hostname || 'localhost';
    const port = '8080';
    const wsUrl = `${protocol}//${host}:${port}/ws/chat?token=${token}`;

    try {
      console.log('[WS] Connecting to:', wsUrl);
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        console.log('[WS] Connected successfully to chat WebSocket.');
        this.connectedSubject.next(true);
        this.startHeartbeat();
      };

      this.socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.event === 'pong') {
            return;
          }
          console.log('[WS] Real-time message received:', payload);
          this.messageSubject.next(payload);
        } catch (err) {
          console.warn('[WS] Non-JSON payload received:', event.data);
        }
      };

      this.socket.onclose = (event) => {
        console.warn(`[WS] Connection closed: code=${event.code}, reason=${event.reason}`);
        this.connectedSubject.next(false);
        this.stopHeartbeat();
        if (!this.isExplicitlyClosed) {
          this.scheduleReconnect();
        }
      };

      this.socket.onerror = (error) => {
        console.warn('[WS] WebSocket error:', error);
      };
    } catch (e) {
      console.error('[WS] Failed to create WebSocket connection:', e);
      this.scheduleReconnect();
    }
  }

  public isConnected(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  public sendMessage(receiverId: string, message: string): boolean {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      const payload = {
        event: 'message.send',
        data: { receiverId, message }
      };
      this.socket.send(JSON.stringify(payload));
      console.log('[WS] Sent message via WebSocket:', payload);
      return true;
    }
    return false;
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.pingIntervalId = setInterval(() => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.socket.send('ping');
      }
    }, 25000);
  }

  private stopHeartbeat(): void {
    if (this.pingIntervalId) {
      clearInterval(this.pingIntervalId);
      this.pingIntervalId = null;
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeoutId) return;
    this.reconnectTimeoutId = setTimeout(() => {
      this.reconnectTimeoutId = null;
      console.log('[WS] Attempting reconnection...');
      this.connect();
    }, 3000);
  }

  public disconnect(): void {
    this.isExplicitlyClosed = true;
    this.stopHeartbeat();
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.connectedSubject.next(false);
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
