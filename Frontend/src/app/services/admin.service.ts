import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../core/api';
import { ApprovalStatus, CollegeSummary } from '../core/models/college.models';

/** Platform ADMIN: review college registrations. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  colleges(status?: ApprovalStatus | null): Observable<CollegeSummary[]> {
    return this.http.get<ApiResponse<CollegeSummary[]>>(api('/api/v1/admin/colleges'), { params: params({ status }) }).pipe(map(r => r.data));
  }
  decide(collegeId: string, status: ApprovalStatus, note?: string): Observable<CollegeSummary> {
    return this.http.post<ApiResponse<CollegeSummary>>(api(`/api/v1/admin/colleges/${collegeId}/decision`), { status, note }).pipe(map(r => r.data));
  }
}
