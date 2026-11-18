import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Observable, Subject, map } from 'rxjs';
import { api, ApiResponse } from '../api';
import { environment } from '../environments/environment';
import { ChatContact } from '../models/college.models';
import { TokenStorageService } from './token-storage.service';
import { FileService } from './file.service';

export interface ChatMessage {
  id: string; senderId: string; receiverId: string; message?: string; messageType?: 'TEXT' | 'FILE' | string; fileName?: string;
  fileSize?: number; fileContentType?: string; status?: string; createdAt?: string; readAt?: string;
}

/** Chat over the existing /ws/chat?token= WebSocket + /api/v1/chat REST endpoints. */
@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {
  private http = inject(HttpClient);
  private storage = inject(TokenStorageService);
  private files = inject(FileService);
  private socket?: WebSocket;
  private retry?: ReturnType<typeof setTimeout>;
  readonly connected = signal(false);
  readonly incoming = new Subject<ChatMessage>();
  /** Unread message count per sender (WhatsApp-style badges); kept live from the socket. */
  readonly unread = signal<Record<string, number>>({});
  readonly totalUnread = computed(() => Object.values(this.unread()).reduce((a, b) => a + b, 0));
  /** The conversation currently open on screen: its messages are read immediately, so they never count as unread. */
  readonly activePeer = signal<string | null>(null);

  connect(): void {
    const token = this.storage.token;
    if (!token || this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return;
    this.socket = new WebSocket(`${environment.wsBaseUrl}/ws/chat?token=${encodeURIComponent(token)}`);
    this.socket.onopen = () => { this.connected.set(true); this.refreshUnread(); };
    this.socket.onclose = () => {
      this.connected.set(false);
      if (this.storage.token) this.retry = setTimeout(() => this.connect(), 5000);
    };
    this.socket.onmessage = ev => {
      try {
        const data = JSON.parse(ev.data);
        if (data.event && data.event !== 'message.new') return; // e.g. message.read receipts are not new messages
        const msg = data.payload ?? data.data ?? data;
        if (!msg || typeof msg !== 'object' || !(msg.senderId || msg.receiverId)) return;
        if (msg.receiverId === this.storage.get('userId') && msg.senderId !== this.activePeer()) {
          this.unread.update(u => ({ ...u, [msg.senderId]: (u[msg.senderId] ?? 0) + 1 }));
        }
        this.incoming.next(msg as ChatMessage);
      } catch { /* ignore non-JSON frames */ }
    };
  }

  /** Reloads per-sender unread counts from the server (login, reconnect, periodic fallback). */
  refreshUnread(): void {
    if (!this.storage.token) return;
    this.http.get<ApiResponse<{ totalUnreadCount: number; senders: { senderId: string; unreadCount: number }[] }>>(api('/api/v1/chat/notifications'))
      .subscribe({
        next: r => {
          const map: Record<string, number> = {};
          (r.data?.senders ?? []).forEach(x => { if (x.senderId !== this.activePeer()) map[x.senderId] = x.unreadCount; });
          this.unread.set(map);
        },
        error: () => { /* badge stays as it was */ },
      });
  }

  /** Call when a conversation is opened: clears its badge. */
  openConversation(peerId: string): void {
    this.activePeer.set(peerId);
    this.unread.update(u => { const { [peerId]: _, ...rest } = u; return rest; });
  }

  disconnect(): void {
    clearTimeout(this.retry);
    this.unread.set({});
    this.activePeer.set(null);
    if (this.socket) this.socket.onclose = null; // a deliberate close must not trigger the auto-reconnect
    this.socket?.close();
    this.connected.set(false);
    this.socket = undefined;
  }

  ngOnDestroy(): void { this.disconnect(); }

  contacts(q = ''): Observable<ChatContact[]> {
    return this.http.get<ApiResponse<ChatContact[]>>(api('/api/v1/college/chat/contacts'), { params: { q } }).pipe(map(r => r.data));
  }
  history(userId: string): Observable<ChatMessage[]> {
    return this.http.get<ApiResponse<ChatMessage[] | { content: ChatMessage[] }>>(api(`/api/v1/chat/messages/${userId}`))
      .pipe(map(r => Array.isArray(r.data) ? r.data : (r.data?.content ?? [])));
  }
  send(receiverId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ApiResponse<ChatMessage>>(api('/api/v1/chat/messages'), { receiverId, message: content }).pipe(map(r => r.data));
  }
  sendFile(receiverId: string, file: File): Observable<ChatMessage> {
    return this.http.post<ApiResponse<ChatMessage>>(api('/api/v1/chat/messages/file'), FileService.form(file, { receiverId })).pipe(map(r => r.data));
  }
  markRead(messageId: string): Observable<unknown> { return this.http.patch(api(`/api/v1/chat/messages/${messageId}/read`), {}); }
  openAttachment(messageId: string): void { this.files.open(`/api/v1/chat/messages/${messageId}/file`); }
}
