import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { Conflict, Slot, SlotRequest, SlotStatus } from '../core/models/college.models';

const BASE = '/api/v1/college/interviews';

@Injectable({ providedIn: 'root' })
export class InterviewScheduleService {
  private http = inject(HttpClient);

  calendar(from: Date, to: Date): Observable<Slot[]> {
    return this.http.get<ApiResponse<Slot[]>>(api(BASE), { params: { from: from.toISOString(), to: to.toISOString() } }).pipe(map(r => r.data));
  }
  forDrive(driveId: string): Observable<Slot[]> { return this.http.get<ApiResponse<Slot[]>>(api(`${BASE}/drive/${driveId}`)).pipe(map(r => r.data)); }
  check(b: SlotRequest): Observable<Conflict[]> { return this.http.post<ApiResponse<Conflict[]>>(api(`${BASE}/check`), b).pipe(map(r => r.data)); }
  schedule(b: SlotRequest): Observable<Slot> { return this.http.post<ApiResponse<Slot>>(api(BASE), b).pipe(map(r => r.data)); }
  bulk(b: { driveId: string; roundId?: string | null; applicationIds: string[]; startAt: string; durationMinutes: number; gapMinutes?: number;
            panelName?: string | null; panelMembers?: string | null; room?: string | null; meetingLink?: string | null }): Observable<{ created: Slot[]; skipped: string[] }> {
    return this.http.post<ApiResponse<{ created: Slot[]; skipped: string[] }>>(api(`${BASE}/bulk`), b).pipe(map(r => r.data));
  }
  reschedule(id: string, b: SlotRequest): Observable<Slot> { return this.http.put<ApiResponse<Slot>>(api(`${BASE}/${id}`), b).pipe(map(r => r.data)); }
  setStatus(id: string, status: SlotStatus): Observable<Slot> {
    return this.http.patch<ApiResponse<Slot>>(api(`${BASE}/${id}/status`), { status }).pipe(map(r => r.data));
  }
  remind(id: string): Observable<Slot> { return this.http.post<ApiResponse<Slot>>(api(`${BASE}/${id}/remind`), {}).pipe(map(r => r.data)); }
}
