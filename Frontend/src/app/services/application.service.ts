import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../core/api';
import { Application, ApplicationStatus, BulkResult } from '../core/models/college.models';
import { FileService } from './file.service';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  list(driveId: string, status: ApplicationStatus | null, q = '', page = 0, size = 50): Observable<Page<Application>> {
    return this.http.get<ApiResponse<Page<Application>>>(api(`/api/v1/college/drives/${driveId}/applications`),
      { params: params({ status, q, page, size }) }).pipe(map(r => r.data));
  }
  add(driveId: string, studentIds: string[], overrideReason?: string): Observable<BulkResult> {
    return this.http.post<ApiResponse<BulkResult>>(api(`/api/v1/college/drives/${driveId}/applications`), { studentIds, overrideReason }).pipe(map(r => r.data));
  }
  bulkStatus(applicationIds: string[], status: ApplicationStatus, remarks?: string): Observable<BulkResult> {
    return this.http.patch<ApiResponse<BulkResult>>(api('/api/v1/college/applications/status'), { applicationIds, status, remarks }).pipe(map(r => r.data));
  }
  export(driveId: string, format: string): Observable<void> {
    return this.files.download(`/api/v1/college/drives/${driveId}/applications/export`, { format }, `applicants.${format}`);
  }
}
