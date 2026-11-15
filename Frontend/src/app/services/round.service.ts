import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { Round, RoundResultRow, RoundResultValue, RoundType } from '../core/models/college.models';
import { FileService } from './file.service';

@Injectable({ providedIn: 'root' })
export class RoundService {
  private http = inject(HttpClient);

  list(driveId: string): Observable<Round[]> {
    return this.http.get<ApiResponse<Round[]>>(api(`/api/v1/college/drives/${driveId}/rounds`)).pipe(map(r => r.data));
  }
  add(driveId: string, b: { name: string; roundType: RoundType; scheduledAt?: string | null }): Observable<Round> {
    return this.http.post<ApiResponse<Round>>(api(`/api/v1/college/drives/${driveId}/rounds`), b).pipe(map(r => r.data));
  }
  update(id: string, b: { name: string; roundType: RoundType; scheduledAt?: string | null }): Observable<Round> {
    return this.http.put<ApiResponse<Round>>(api(`/api/v1/college/rounds/${id}`), b).pipe(map(r => r.data));
  }
  remove(id: string): Observable<unknown> { return this.http.delete(api(`/api/v1/college/rounds/${id}`)); }
  reorder(driveId: string, roundIds: string[]): Observable<Round[]> {
    return this.http.put<ApiResponse<Round[]>>(api(`/api/v1/college/drives/${driveId}/rounds/order`), { roundIds }).pipe(map(r => r.data));
  }
  start(id: string): Observable<Round> { return this.http.post<ApiResponse<Round>>(api(`/api/v1/college/rounds/${id}/start`), {}).pipe(map(r => r.data)); }
  complete(id: string): Observable<Round> { return this.http.post<ApiResponse<Round>>(api(`/api/v1/college/rounds/${id}/complete`), {}).pipe(map(r => r.data)); }
  results(id: string): Observable<RoundResultRow[]> {
    return this.http.get<ApiResponse<RoundResultRow[]>>(api(`/api/v1/college/rounds/${id}/results`)).pipe(map(r => r.data));
  }
  saveResults(id: string, results: { applicationId: string; result: RoundResultValue; score?: number | null; remarks?: string | null }[]): Observable<RoundResultRow[]> {
    return this.http.put<ApiResponse<RoundResultRow[]>>(api(`/api/v1/college/rounds/${id}/results`), { results }).pipe(map(r => r.data));
  }
  upload(id: string, file: File): Observable<{ updated: number; errors: string[] }> {
    return this.http.post<ApiResponse<{ updated: number; errors: string[] }>>(api(`/api/v1/college/rounds/${id}/results/upload`), FileService.form(file))
      .pipe(map(r => r.data));
  }
}
