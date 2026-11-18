import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../../core/api';
import { Dashboard, SkillGapRow } from '../../../core/models/college.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);

  dashboard(batchYear?: number | null, from?: string | null, to?: string | null): Observable<Dashboard> {
    return this.http.get<ApiResponse<Dashboard>>(api('/api/v1/college/analytics/dashboard'), { params: params({ batchYear, from, to }) }).pipe(map(r => r.data));
  }
  skillGap(batchYear?: number | null): Observable<SkillGapRow[]> {
    return this.http.get<ApiResponse<SkillGapRow[]>>(api('/api/v1/college/analytics/skill-gap'), { params: params({ batchYear }) }).pipe(map(r => r.data));
  }
}
