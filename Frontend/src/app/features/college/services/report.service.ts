import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../../core/api';
import { ReportFormat, ReportFrequency, ReportSchedule, ReportTable, ReportType } from '../../../core/models/college.models';
import { FileService } from '../../../core/services/file.service';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  types(): Observable<ReportType[]> { return this.http.get<ApiResponse<ReportType[]>>(api('/api/v1/college/reports/types')).pipe(map(r => r.data)); }
  preview(type: string, batchYear?: number | null, driveId?: string | null): Observable<ReportTable> {
    return this.http.get<ApiResponse<ReportTable>>(api(`/api/v1/college/reports/${type}/preview`), { params: params({ batchYear, driveId }) }).pipe(map(r => r.data));
  }
  download(type: string, format: 'xlsx' | 'csv' | 'pdf', batchYear?: number | null, driveId?: string | null): Observable<void> {
    return this.files.download(`/api/v1/college/reports/${type}`, { format, batchYear, driveId }, `${type}.${format}`);
  }
  schedules(): Observable<ReportSchedule[]> { return this.http.get<ApiResponse<ReportSchedule[]>>(api('/api/v1/college/report-schedules')).pipe(map(r => r.data)); }
  schedule(b: { reportType: string; format: ReportFormat; frequency: ReportFrequency; recipients: string }): Observable<ReportSchedule> {
    return this.http.post<ApiResponse<ReportSchedule>>(api('/api/v1/college/report-schedules'), b).pipe(map(r => r.data));
  }
  toggle(id: string, active: boolean): Observable<ReportSchedule> {
    return this.http.patch<ApiResponse<ReportSchedule>>(api(`/api/v1/college/report-schedules/${id}`), { active }).pipe(map(r => r.data));
  }
  removeSchedule(id: string): Observable<unknown> { return this.http.delete(api(`/api/v1/college/report-schedules/${id}`)); }
}
