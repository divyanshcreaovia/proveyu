import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../../../core/api';
import { AuditEntry } from '../../../core/models/college.models';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private http = inject(HttpClient);
  search(f: { entityType?: string; action?: string; q?: string; from?: string; to?: string }, page = 0, size = 50): Observable<Page<AuditEntry>> {
    return this.http.get<ApiResponse<Page<AuditEntry>>>(api('/api/v1/college/audit-logs'), { params: params({ ...f, page, size }) }).pipe(map(r => r.data));
  }
}
