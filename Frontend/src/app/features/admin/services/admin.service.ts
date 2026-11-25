import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../../core/api';
import { ApprovalStatus, CollegeSummary } from '../../../core/models/college.models';
import { FileService } from '../../../core/services/file.service';
import { ProctorLog } from '../../student/services/prep.service';

// ---------------------------------------------------------------- models
export interface AiUsage { since: string; cloudCalls: number; cloudTokens: number; cloudFailures: number; localCalls: number;
  cloudTranscriptions: number; localTranscriptions: number; }
export interface AuditRow { id: string; adminName?: string; action: string; targetType?: string; targetId?: string; summary: string; createdAt: string; }
export interface AdminDashboard {
  colleges: Partial<Record<string, number>>; users: Partial<Record<string, number>>; blockedUsers: number; students: number; placedStudents: number;
  drives: number; liveDrives: number; offers: number; acceptedOffers: number; mockAttempts: number; flaggedAttempts: number;
  autoSubmittedAttempts: number; interviews: number; completedInterviews: number; paymentsWaiting: number; revenueRupees: number;
  revenueThisMonthRupees: number; ai: AiUsage; pendingColleges: CollegeSummary[]; recentActions: AuditRow[];
}
export interface TeamMember { userId: string; fullName: string; email: string; role: string; status: string; blocked: boolean; }
export interface CollegeDetail { college: CollegeSummary; students: number; placedStudents: number; drives: number; offers: number; mockAttempts: number;
  interviews: number; team: TeamMember[]; }
export interface UserRow { id: string; fullName: string; email: string; phone?: string; role: string; active: boolean; verified: boolean;
  createdAt: string; collegeName?: string; detail?: string; }
export interface Page<T> { rows: T[]; total: number; page: number; size: number; }
export interface AdminRow { id: string; fullName: string; email: string; active: boolean; createdAt: string; }
export interface CxoUserRow { userId: string; memberId: string; fullName: string; email: string; designation?: string; collegeId: string;
  collegeName?: string; status: 'ACTIVE' | 'REVOKED'; active: boolean; lastLoginAt?: string; createdAt: string; }
export type Audience = 'ALL' | 'STUDENTS' | 'COLLEGE_STAFF' | 'COLLEGE';
export interface SettingEntry { key: string; group: string; label: string; help: string; type: 'INT' | 'TEXT' | 'BOOL'; min: number; max: number;
  value: string; defaultValue: string; overridden: boolean; updatedAt?: string; }
export interface FlaggedAttempt { attemptId: string; collegeName?: string; studentName: string; rollNumber?: string; testTitle: string; startedAt: string;
  submittedAt?: string; score?: number; maxScore: number; tabSwitches: number; autoSubmitReason?: string; aiWarnings?: number; }

export const ROLE_LABELS: Record<string, string> = {
  CANDIDATE: 'Student', COLLEGE_ADMIN: 'TPO / college admin', COLLEGE_COORDINATOR: 'Coordinator', COLLEGE_VIEWER: 'Viewer',
  RECRUITER: 'Company', ADMIN: 'Platform admin', ORGANIZATION: 'Organisation',
};

const A = '/api/v1/admin';

/** Platform ADMIN portal: dashboard, colleges, users, admins, payments export, proctoring, broadcast, settings and audit log. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private files = inject(FileService);
  private get<T>(path: string, query: Record<string, unknown> = {}): Observable<T> {
    return this.http.get<ApiResponse<T>>(api(A + path), { params: params(query) }).pipe(map(r => r.data));
  }

  dashboard(): Observable<AdminDashboard> { return this.get('/dashboard'); }

  // ---- colleges
  colleges(status?: ApprovalStatus | null): Observable<CollegeSummary[]> { return this.get('/colleges', { status }); }
  college(id: string): Observable<CollegeDetail> { return this.get(`/colleges/${id}`); }
  decide(collegeId: string, status: ApprovalStatus, note?: string): Observable<CollegeSummary> {
    return this.http.post<ApiResponse<CollegeSummary>>(api(`${A}/colleges/${collegeId}/decision`), { status, note }).pipe(map(r => r.data));
  }

  // ---- users and admins
  users(q: string, role: string, active: boolean | null, page: number, size = 25): Observable<Page<UserRow>> {
    return this.get('/users', { q: q || null, role: role || null, active, page, size });
  }
  setBlocked(id: string, blocked: boolean): Observable<unknown> { return this.http.post(api(`${A}/users/${id}/${blocked ? 'block' : 'unblock'}`), {}); }
  resetPassword(id: string): Observable<{ devOtp?: string }> {
    return this.http.post<ApiResponse<{ devOtp?: string }>>(api(`${A}/users/${id}/reset-password`), {}).pipe(map(r => r.data));
  }
  grantCredits(studentEmail: string, count: number): Observable<{ studentName: string; paidBalance: number }> {
    return this.http.post<ApiResponse<{ studentName: string; paidBalance: number }>>(api(`${A}/interview/credits`), { studentEmail, count }).pipe(map(r => r.data));
  }
  admins(): Observable<AdminRow[]> { return this.get('/admins'); }
  // ---- CXO (college leadership) accounts
  cxoUsers(): Observable<CxoUserRow[]> { return this.get('/cxo-users'); }
  createCxo(body: { collegeId: string; fullName: string; email: string; designation?: string; password: string }): Observable<CxoUserRow> {
    return this.http.post<ApiResponse<CxoUserRow>>(api(`${A}/cxo-users`), body).pipe(map(r => r.data));
  }
  setCxoActive(memberId: string, active: boolean): Observable<unknown> {
    return this.http.post(api(`${A}/cxo-users/${memberId}/${active ? 'restore' : 'revoke'}`), {});
  }
  createAdmin(body: { email: string; fullName: string; password: string }): Observable<AdminRow> {
    return this.http.post<ApiResponse<AdminRow>>(api(`${A}/admins`), body).pipe(map(r => r.data));
  }

  // ---- payments, proctoring
  exportPayments(status: string | null, format: 'xlsx' | 'csv'): Observable<void> {
    return this.files.download(`${A}/payments/export`, { status, format }, `interview-payments.${format}`);
  }
  flagged(): Observable<FlaggedAttempt[]> { return this.get('/proctoring'); }
  proctorLog(attemptId: string): Observable<ProctorLog> { return this.get(`/proctoring/attempts/${attemptId}`); }
  screenshotUrl(eventId: string): Observable<string> { return this.files.objectUrl(`${A}/proctoring/screenshots/${eventId}`); }

  // ---- broadcast
  recipients(audience: Audience, collegeId?: string | null): Observable<number> {
    return this.get<{ recipients: number }>('/broadcast/count', { audience, collegeId }).pipe(map(r => r.recipients));
  }
  broadcast(body: { audience: Audience; collegeId?: string | null; title: string; message: string; sendEmail: boolean }): Observable<{ recipients: number; emails: number }> {
    return this.http.post<ApiResponse<{ recipients: number; emails: number }>>(api(`${A}/broadcast`), body).pipe(map(r => r.data));
  }

  // ---- settings, audit
  settings(): Observable<SettingEntry[]> { return this.get('/settings'); }
  saveSettings(values: Record<string, string>): Observable<SettingEntry[]> {
    return this.http.put<ApiResponse<SettingEntry[]>>(api(`${A}/settings`), values).pipe(map(r => r.data));
  }
  resetSetting(key: string): Observable<SettingEntry[]> {
    return this.http.delete<ApiResponse<SettingEntry[]>>(api(`${A}/settings/${key}`)).pipe(map(r => r.data));
  }
  audit(action: string | null, page: number, size = 30): Observable<Page<AuditRow>> { return this.get('/audit', { action, page, size }); }
}
