import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { Branch, Batch, Me, SearchResult, Tier } from '../core/models/college.models';

/** Current college context (me) plus cached reference data used across pages. */
@Injectable({ providedIn: 'root' })
export class CollegeService {
  private http = inject(HttpClient);
  readonly me = signal<Me | null>(null);
  readonly branches = signal<Branch[]>([]);
  readonly batches = signal<Batch[]>([]);
  readonly tiers = signal<Tier[]>([]);

  loadMe(): Observable<Me> {
    return this.http.get<ApiResponse<Me>>(api('/api/v1/college/me')).pipe(map(r => r.data), tap(m => this.me.set(m)));
  }

  /** Loads branches / batches / tiers once per session (call refresh after edits). */
  loadReference(): void {
    this.http.get<ApiResponse<Branch[]>>(api('/api/v1/college/settings/branches')).subscribe(r => this.branches.set(r.data));
    this.http.get<ApiResponse<Batch[]>>(api('/api/v1/college/settings/batches')).subscribe(r => this.batches.set(r.data));
    this.http.get<ApiResponse<Tier[]>>(api('/api/v1/college/settings/tiers')).subscribe(r => this.tiers.set(r.data));
  }

  search(q: string): Observable<SearchResult> {
    return this.http.get<ApiResponse<SearchResult>>(api('/api/v1/college/search'), { params: { q } }).pipe(map(r => r.data));
  }

  isAdmin(): boolean { return this.me()?.role === 'COLLEGE_ADMIN'; }
  canWrite(): boolean { const r = this.me()?.role; return r === 'COLLEGE_ADMIN' || r === 'COLLEGE_COORDINATOR'; }

  reset(): void { this.me.set(null); this.branches.set([]); this.batches.set([]); this.tiers.set([]); }
}
