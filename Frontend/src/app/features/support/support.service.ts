import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../core/api';

export type SupportCategory = 'ACCOUNT' | 'PLACEMENT' | 'MOCK_TEST' | 'AI_INTERVIEW' | 'PAYMENT' | 'BUG' | 'OTHER';
export type SupportStatus = 'OPEN' | 'ANSWERED' | 'CLOSED';
export interface SupportTicket {
  id: string; userName: string; userEmail: string; userRole: string; portal: 'STUDENT' | 'COLLEGE'; collegeId?: string; collegeName?: string;
  category: SupportCategory; subject: string; message: string; status: SupportStatus; reply?: string; repliedBy?: string; repliedAt?: string;
  createdAt: string; updatedAt: string;
}
export interface SupportPage { rows: SupportTicket[]; total: number; page: number; size: number; }

export const SUPPORT_EMAIL = 'support@proveyu.com';
export const SUPPORT_CATEGORIES: { key: SupportCategory; label: string }[] = [
  { key: 'ACCOUNT', label: 'Account & sign-in' }, { key: 'PLACEMENT', label: 'Drives, applications & offers' },
  { key: 'MOCK_TEST', label: 'Mock tests' }, { key: 'AI_INTERVIEW', label: 'AI interview' }, { key: 'PAYMENT', label: 'Payment' },
  { key: 'BUG', label: 'Something is not working' }, { key: 'OTHER', label: 'Other' },
];
export const categoryLabel = (k: string) => SUPPORT_CATEGORIES.find(c => c.key === k)?.label ?? k;

/** Help & support: students and college staff write to the ProveYu team; admins answer from the admin portal. */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private http = inject(HttpClient);
  /** Open requests, for the admin menu badge. */
  readonly open = signal(0);

  // ---- college and student portals
  send(body: { category: SupportCategory; subject: string; message: string }): Observable<SupportTicket> {
    return this.http.post<ApiResponse<SupportTicket>>(api('/api/v1/support/tickets'), body).pipe(map(r => r.data));
  }
  mine(): Observable<SupportTicket[]> {
    return this.http.get<ApiResponse<SupportTicket[]>>(api('/api/v1/support/tickets')).pipe(map(r => r.data));
  }

  // ---- admin portal
  list(q: { status?: SupportStatus | null; q?: string; page?: number; size?: number }): Observable<SupportPage> {
    return this.http.get<ApiResponse<SupportPage>>(api('/api/v1/admin/support'), { params: params(q) }).pipe(map(r => r.data));
  }
  refreshOpen(): void {
    this.http.get<ApiResponse<{ open: number }>>(api('/api/v1/admin/support/open-count')).subscribe({ next: r => this.open.set(r.data.open), error: () => undefined });
  }
  reply(id: string, body: { reply: string; close: boolean; sendEmail: boolean }): Observable<SupportTicket> {
    return this.http.post<ApiResponse<SupportTicket>>(api(`/api/v1/admin/support/${id}/reply`), body).pipe(map(r => r.data));
  }
  setStatus(id: string, status: SupportStatus): Observable<SupportTicket> {
    return this.http.post<ApiResponse<SupportTicket>>(api(`/api/v1/admin/support/${id}/status`), { status }).pipe(map(r => r.data));
  }
}
