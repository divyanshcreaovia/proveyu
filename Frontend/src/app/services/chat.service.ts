import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subject, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { environment } from '../core/environments/environment';
import { ChatContact } from '../core/models/college.models';
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

  connect(): void {
    const token = this.storage.token;
    if (!token || this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return;
    this.socket = new WebSocket(`${environment.wsBaseUrl}/ws/chat?token=${encodeURIComponent(token)}`);
    this.socket.onopen = () => this.connected.set(true);
    this.socket.onclose = () => {
      this.connected.set(false);
      if (this.storage.token) this.retry = setTimeout(() => this.connect(), 5000);
    };
    this.socket.onmessage = ev => {
      try {
        const data = JSON.parse(ev.data);
        const msg = data.payload ?? data.data ?? data;
        if (msg && typeof msg === 'object' && (msg.senderId || msg.receiverId)) this.incoming.next(msg as ChatMessage);
      } catch { /* ignore non-JSON frames */ }
    };
  }

  disconnect(): void {
    clearTimeout(this.retry);
    this.socket?.close();
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
