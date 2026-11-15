import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../core/api';
import { Drive, DriveRequest, DriveStatus, DriveType } from '../core/models/college.models';
import { FileService } from './file.service';

const BASE = '/api/v1/college/drives';

export interface DriveFilter { q?: string; status?: DriveStatus | null; type?: DriveType | null; companyId?: string | null; from?: string; to?: string; deleted?: boolean; }

@Injectable({ providedIn: 'root' })
export class PlacementDriveService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  list(f: DriveFilter, page = 0, size = 20, sort = 'createdAt', dir = 'desc'): Observable<Page<Drive>> {
    return this.http.get<ApiResponse<Page<Drive>>>(api(BASE), { params: params({ ...f, page, size, sort, dir }) }).pipe(map(r => r.data));
  }
  get(id: string): Observable<Drive> { return this.http.get<ApiResponse<Drive>>(api(`${BASE}/${id}`)).pipe(map(r => r.data)); }
  create(b: DriveRequest): Observable<Drive> { return this.http.post<ApiResponse<Drive>>(api(BASE), b).pipe(map(r => r.data)); }
  update(id: string, b: DriveRequest): Observable<Drive> { return this.http.put<ApiResponse<Drive>>(api(`${BASE}/${id}`), b).pipe(map(r => r.data)); }
  clone(id: string): Observable<Drive> { return this.http.post<ApiResponse<Drive>>(api(`${BASE}/${id}/clone`), {}).pipe(map(r => r.data)); }
  transition(id: string, status: DriveStatus, reason?: string): Observable<Drive> {
    return this.http.post<ApiResponse<Drive>>(api(`${BASE}/${id}/status`), { status, reason }).pipe(map(r => r.data));
  }
  remove(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/${id}`)); }
  restore(id: string): Observable<Drive> { return this.http.post<ApiResponse<Drive>>(api(`${BASE}/${id}/restore`), {}).pipe(map(r => r.data)); }
  export(format: string, f: DriveFilter = {}): Observable<void> { return this.files.download(`${BASE}/export`, { ...f, format }, `drives.${format}`); }
}
