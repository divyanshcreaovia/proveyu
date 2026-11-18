import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, ApiResponse, Page } from '../api';
import { Announcement, AppNotification, AudienceFilter } from '../models/college.models';

/** In-app notifications (any user) + college announcements. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  readonly items = signal<AppNotification[]>([]);
  readonly unread = computed(() => this.items().filter(n => !n.read).length);

  load(): Observable<AppNotification[]> {
    return this.http.get<ApiResponse<AppNotification[]>>(api('/api/v1/notifications')).pipe(map(r => r.data ?? []), tap(n => this.items.set(n)));
  }
  markRead(id: string): Observable<unknown> {
    this.items.update(list => list.map(n => n.id === id ? { ...n, read: true } : n));
    return this.http.put(api(`/api/v1/notifications/${id}/read`), {});
  }

  audienceCount(filter: AudienceFilter): Observable<number> {
    return this.http.post<ApiResponse<{ count: number }>>(api('/api/v1/college/announcements/audience'), filter).pipe(map(r => r.data.count));
  }
  announce(b: { title: string; body: string; filter: AudienceFilter; sendEmail: boolean }): Observable<Announcement> {
    return this.http.post<ApiResponse<Announcement>>(api('/api/v1/college/announcements'), b).pipe(map(r => r.data));
  }
  announcements(page = 0, size = 20): Observable<Page<Announcement>> {
    return this.http.get<ApiResponse<Page<Announcement>>>(api('/api/v1/college/announcements'), { params: { page, size } }).pipe(map(r => r.data));
  }
}
