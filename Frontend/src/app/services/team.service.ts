import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { Invite, Member, MemberRole, RoleMatrixRow } from '../core/models/college.models';

const BASE = '/api/v1/college/team';

@Injectable({ providedIn: 'root' })
export class TeamService {
  private http = inject(HttpClient);

  members(): Observable<Member[]> { return this.http.get<ApiResponse<Member[]>>(api(`${BASE}/members`)).pipe(map(r => r.data)); }
  invites(): Observable<Invite[]> { return this.http.get<ApiResponse<Invite[]>>(api(`${BASE}/invites`)).pipe(map(r => r.data)); }
  matrix(): Observable<RoleMatrixRow[]> { return this.http.get<ApiResponse<RoleMatrixRow[]>>(api(`${BASE}/role-matrix`)).pipe(map(r => r.data)); }
  invite(b: { email: string; fullName: string; role: MemberRole; designation?: string; branchIds?: string[] }): Observable<Invite> {
    return this.http.post<ApiResponse<Invite>>(api(`${BASE}/invites`), b).pipe(map(r => r.data));
  }
  resend(id: string): Observable<Invite> { return this.http.post<ApiResponse<Invite>>(api(`${BASE}/invites/${id}/resend`), {}).pipe(map(r => r.data)); }
  revokeInvite(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/invites/${id}`)); }
  update(id: string, b: { role: MemberRole; designation?: string; branchIds?: string[] }): Observable<Member> {
    return this.http.put<ApiResponse<Member>>(api(`${BASE}/members/${id}`), b).pipe(map(r => r.data));
  }
  revoke(id: string): Observable<Member> { return this.http.post<ApiResponse<Member>>(api(`${BASE}/members/${id}/revoke`), {}).pipe(map(r => r.data)); }
  restore(id: string): Observable<Member> { return this.http.post<ApiResponse<Member>>(api(`${BASE}/members/${id}/restore`), {}).pipe(map(r => r.data)); }
}
