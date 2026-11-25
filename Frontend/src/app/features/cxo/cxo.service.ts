import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, ApiResponse } from '../../core/api';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { AuthService } from '../../core/services/auth.service';
import { SILENT_ERRORS } from '../../core/interceptors/error.interceptor';
import {
  BuildEvent, Catalog, CxoMe, DashboardDetail, DashboardSpec, DashboardSummary, Insight, Snapshot, WidgetData,
} from './cxo.models';

const C = '/api/v1/cxo';

/** CXO portal API. The live build is a fetch() stream (EventSource cannot POST or send the Authorization header). */
@Injectable({ providedIn: 'root' })
export class CxoService {
  private http = inject(HttpClient);
  private storage = inject(TokenStorageService);
  private auth = inject(AuthService);

  /** The signed-in CXO (loaded by the shell). */
  readonly me = signal<CxoMe | null>(null);

  loadMe(): Observable<CxoMe> { return this.get<CxoMe>('/me').pipe(tap(m => this.me.set(m))); }
  catalog(): Observable<Catalog> { return this.get('/catalog'); }

  dashboards(): Observable<DashboardSummary[]> { return this.get('/dashboards'); }
  dashboard(id: string): Observable<DashboardDetail> { return this.get(`/dashboards/${id}`); }
  save(body: { title: string; prompt?: string | null; spec: DashboardSpec; pinned?: boolean }): Observable<DashboardDetail> {
    return this.http.post<ApiResponse<DashboardDetail>>(api(`${C}/dashboards`), body).pipe(map(r => r.data));
  }
  update(id: string, body: { title: string; prompt?: string | null; spec: DashboardSpec; pinned?: boolean }): Observable<DashboardDetail> {
    return this.http.put<ApiResponse<DashboardDetail>>(api(`${C}/dashboards/${id}`), body).pipe(map(r => r.data));
  }
  pin(id: string, pinned: boolean): Observable<unknown> { return this.http.post(api(`${C}/dashboards/${id}/pin`), { pinned }); }
  remove(id: string): Observable<unknown> { return this.http.delete(api(`${C}/dashboards/${id}`)); }

  /** Fresh numbers for a spec (live refresh); silent so a background refresh never pops an error toast. */
  run(spec: DashboardSpec): Observable<WidgetData[]> {
    return this.http.post<ApiResponse<WidgetData[]>>(api(`${C}/dashboards/run`), { spec }, { context: new HttpContext().set(SILENT_ERRORS, true) }).pipe(map(r => r.data));
  }

  /** AI summary of a dashboard's current numbers (silent: the board simply shows no summary if it fails). */
  summary(prompt: string | null, spec: DashboardSpec): Observable<Insight> {
    return this.http.post<ApiResponse<Insight>>(api(`${C}/dashboards/summary`), { prompt, spec },
      { context: new HttpContext().set(SILENT_ERRORS, true) }).pipe(map(r => r.data));
  }

  /**
   * Builds a dashboard and reports every server event as it arrives. Resolves when the stream ends; rejects with a readable
   * message on failure. Abort with the given signal (e.g. when the page is left).
   */
  async build(prompt: string, current: DashboardSpec | null, onEvent: (e: BuildEvent) => void, abort?: AbortSignal): Promise<void> {
    const res = await fetch(api(`${C}/dashboards/generate`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${this.storage.token ?? ''}` },
      body: JSON.stringify({ prompt, current }),
      signal: abort,
    });
    if (res.status === 401) {
      this.auth.logout('/cxo/login', 'Your session has expired. Please sign in again.');
      throw new Error('Session expired');
    }
    if (!res.ok || !res.body) {
      let message = 'Could not build the dashboard. Please try again.';
      try { message = ((await res.json()) as ApiResponse<unknown>).message || message; } catch { /* not JSON */ }
      throw new Error(message);
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let cut: number;
      while ((cut = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, cut);
        buffer = buffer.slice(cut + 2);
        const evt = parseFrame(frame);
        if (evt) onEvent(evt);
      }
    }
    const tail = parseFrame(buffer.trim());
    if (tail) onEvent(tail);
  }

  // ------------------------------------------------------------------ last dashboard, cached in this browser

  private snapKey(): string | null {
    const id = this.me()?.userId ?? this.storage.get('userId');
    return id ? `proveyu_cxo_last_${id}` : null;
  }

  /** The last dashboard this leader had on screen (renders instantly on the next visit; numbers are then refreshed live). */
  loadSnapshot(): Snapshot | null {
    const k = this.snapKey();
    if (!k) return null;
    try {
      const s = JSON.parse(localStorage.getItem(k) ?? 'null') as Snapshot | null;
      return s && s.spec?.widgets?.length ? s : null;
    } catch {
      return null;
    }
  }

  saveSnapshot(s: Snapshot): void {
    const k = this.snapKey();
    if (!k) return;
    try { localStorage.setItem(k, JSON.stringify(s)); } catch { /* storage full or off: skip */ }
  }

  clearSnapshot(): void {
    const k = this.snapKey();
    if (k) localStorage.removeItem(k);
  }

  private get<T>(path: string): Observable<T> {
    return this.http.get<ApiResponse<T>>(api(C + path)).pipe(map(r => r.data));
  }
}

/** One Server-Sent Events frame ("event: x" + "data: {...}" lines) to a typed event. */
function parseFrame(frame: string): BuildEvent | null {
  if (!frame) return null;
  let type = 'message';
  const data: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) type = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
  }
  if (!data.length) return null;
  try {
    return { type, data: JSON.parse(data.join('\n')) } as BuildEvent;
  } catch {
    return null;
  }
}
