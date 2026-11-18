import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../../core/api';
import { EligibilityPreview, Override, OverrideAction } from '../../../core/models/college.models';

@Injectable({ providedIn: 'root' })
export class EligibilityService {
  private http = inject(HttpClient);

  preview(driveId: string, eligible: boolean | null, q = '', page = 0, size = 50): Observable<EligibilityPreview> {
    return this.http.get<ApiResponse<EligibilityPreview>>(api(`/api/v1/college/drives/${driveId}/eligibility`),
      { params: params({ eligible, q, page, size }) }).pipe(map(r => r.data));
  }
  overrides(driveId: string): Observable<Override[]> {
    return this.http.get<ApiResponse<Override[]>>(api(`/api/v1/college/drives/${driveId}/overrides`)).pipe(map(r => r.data));
  }
  addOverride(driveId: string, studentId: string, action: OverrideAction, reason: string): Observable<Override> {
    return this.http.post<ApiResponse<Override>>(api(`/api/v1/college/drives/${driveId}/overrides`), { studentId, action, reason }).pipe(map(r => r.data));
  }
  removeOverride(id: string): Observable<unknown> { return this.http.delete(api(`/api/v1/college/overrides/${id}`)); }
}
