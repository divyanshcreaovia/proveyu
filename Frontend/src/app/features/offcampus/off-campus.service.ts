import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../core/api';
import { OffCampusPolicy } from '../../core/models/college.models';
import { FileService } from '../../core/services/file.service';

export type JobType = 'FULL_TIME' | 'INTERNSHIP' | 'INTERNSHIP_PPO';
export type WorkMode = 'ONSITE' | 'REMOTE' | 'HYBRID';
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type AppStatus = 'CLICKED' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'NOT_INTERESTED';
export type JobSource = 'MANUAL' | 'CAREERJET';
export type JobField = 'SOFTWARE' | 'DATA' | 'TESTING' | 'CYBER' | 'DESIGN' | 'MECHANICAL' | 'ELECTRICAL' | 'CIVIL' | 'BUSINESS' | 'FINANCE'
  | 'MARKETING' | 'HR' | 'OTHER';
export type NocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface Noc {
  id: string; status: NocStatus; studentNote?: string; decisionNote?: string; hasNoc: boolean; nocGenerated: boolean; decidedByName?: string;
  decidedAt?: string; createdAt: string;
}
export interface CollegeNoc {
  id: string; applicationId: string; studentName: string; rollNumber: string; branch?: string; batchYear?: number; companyName: string; roleTitle: string;
  jobType: JobType; offerCtcLpa?: number; offerFileName?: string; status: NocStatus; studentNote?: string; decisionNote?: string; hasNoc: boolean;
  nocGenerated: boolean; decidedByName?: string; decidedAt?: string; createdAt: string; placementRecorded: boolean;
}
export interface ImportRun {
  configured: boolean; startedAt?: string; finishedAt?: string; fetched: number; added: number; skipped: number; expired: number; error?: string;
}

export interface JobRequest {
  companyName: string; roleTitle: string; jobType: JobType; workMode: WorkMode; field?: JobField | null; location?: string | null;
  ctcMinLpa?: number | null; ctcMaxLpa?: number | null; stipendPerMonth?: number | null; description?: string | null; skills: string[];
  applyUrl: string; applyDeadline?: string | null; batchYears: number[]; degreeKeywords?: string | null; branchKeywords?: string | null;
  minCgpa?: number | null; maxBacklogs?: number | null; collegeIds: string[];
}
export interface JobCounts { saved: number; clicked: number; applied: number; interviewing: number; offers: number; }
export interface OffCampusJob extends JobRequest {
  id: string; status: JobStatus; publishedAt?: string; createdAt: string; updatedAt: string; source: JobSource; sourcePostedAt?: string; counts: JobCounts;
}
export interface JobPage { rows: OffCampusJob[]; total: number; page: number; size: number; }
export interface Applicant {
  id: string; studentName: string; rollNumber: string; email: string; collegeName: string; branch?: string; batchYear?: number; status: AppStatus;
  saved: boolean; offerCtcLpa?: number; note?: string; clickedAt?: string; appliedAt?: string; updatedAt: string;
}
export interface StudentJob {
  id: string; companyName: string; roleTitle: string; jobType: JobType; workMode: WorkMode; field?: JobField; location?: string; ctcMinLpa?: number;
  ctcMaxLpa?: number;
  stipendPerMonth?: number; description?: string; skills: string[]; applyUrl?: string; applyDeadline?: string; publishedAt?: string;
  eligible: boolean; reasons: string[]; canApply: boolean; status?: AppStatus | null; saved: boolean; offerCtcLpa?: number; note?: string;
  source: JobSource; noc?: Noc | null;
}
export interface StudentJobs { policy: OffCampusPolicy; interests: JobField[]; jobs: StudentJob[]; }
export interface CollegeRow {
  id: string; jobId: string; companyName: string; roleTitle: string; jobType: JobType; studentId: string; studentName: string; rollNumber: string;
  branch?: string; batchYear?: number; status: AppStatus; offerCtcLpa?: number; note?: string; clickedAt?: string; appliedAt?: string; updatedAt: string;
}
export interface CollegeView {
  policy: OffCampusPolicy; students: number; applied: number; interviewing: number; offers: number; pendingNocs: number; rows: CollegeRow[];
}

export const JOB_TYPES: { v: JobType; l: string }[] = [
  { v: 'FULL_TIME', l: 'Full-time' }, { v: 'INTERNSHIP', l: 'Internship' }, { v: 'INTERNSHIP_PPO', l: 'Internship + PPO' },
];
export const WORK_MODES: { v: WorkMode; l: string }[] = [{ v: 'ONSITE', l: 'On-site' }, { v: 'HYBRID', l: 'Hybrid' }, { v: 'REMOTE', l: 'Remote' }];
export const APP_STATUSES: { v: AppStatus; l: string }[] = [
  { v: 'CLICKED', l: 'Opened link' }, { v: 'APPLIED', l: 'Applied' }, { v: 'INTERVIEWING', l: 'Interviewing' }, { v: 'OFFER', l: 'Got an offer' },
  { v: 'REJECTED', l: 'Not selected' }, { v: 'NOT_INTERESTED', l: 'Not interested' },
];
/** Same order and names as JobField in the backend. */
export const JOB_FIELDS: { v: JobField; l: string; icon: string }[] = [
  { v: 'SOFTWARE', l: 'Software development', icon: 'bi-code-slash' }, { v: 'DATA', l: 'Data analytics & science', icon: 'bi-bar-chart-line' },
  { v: 'TESTING', l: 'Software testing & QA', icon: 'bi-bug' }, { v: 'CYBER', l: 'Networking & cybersecurity', icon: 'bi-shield-lock' },
  { v: 'DESIGN', l: 'UI/UX & design', icon: 'bi-palette' }, { v: 'MECHANICAL', l: 'Mechanical', icon: 'bi-gear-wide-connected' },
  { v: 'ELECTRICAL', l: 'Electrical & electronics', icon: 'bi-cpu' }, { v: 'CIVIL', l: 'Civil & construction', icon: 'bi-building' },
  { v: 'BUSINESS', l: 'Business analyst & consulting', icon: 'bi-diagram-3' }, { v: 'FINANCE', l: 'Finance & accounting', icon: 'bi-calculator' },
  { v: 'MARKETING', l: 'Marketing & sales', icon: 'bi-megaphone' }, { v: 'HR', l: 'HR & recruitment', icon: 'bi-people' },
  { v: 'OTHER', l: 'Other', icon: 'bi-three-dots' },
];
export const fieldLabel = (v: string | null | undefined) => JOB_FIELDS.find(f => f.v === v)?.l ?? '';
export const jobTypeLabel = (v: string) => JOB_TYPES.find(t => t.v === v)?.l ?? v;
export const workModeLabel = (v: string) => WORK_MODES.find(t => t.v === v)?.l ?? v;
export const appStatusLabel = (v: string | null | undefined) => APP_STATUSES.find(t => t.v === v)?.l ?? (v ?? '');

/** "6–9 LPA", "12 LPA", "₹25,000/month" or "Not disclosed". */
export function payLabel(j: { ctcMinLpa?: number | null; ctcMaxLpa?: number | null; stipendPerMonth?: number | null }): string {
  const parts: string[] = [];
  if (j.ctcMinLpa && j.ctcMaxLpa && j.ctcMinLpa !== j.ctcMaxLpa) parts.push(`${j.ctcMinLpa}–${j.ctcMaxLpa} LPA`);
  else if (j.ctcMinLpa || j.ctcMaxLpa) parts.push(`${j.ctcMinLpa || j.ctcMaxLpa} LPA`);
  if (j.stipendPerMonth) parts.push(`₹${j.stipendPerMonth.toLocaleString('en-IN')}/month`);
  return parts.length ? parts.join(' · ') : 'Not disclosed';
}

/** Off-campus jobs for the admin, student and college portals. */
@Injectable({ providedIn: 'root' })
export class OffCampusService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  // ---- admin
  list(q: { status?: JobStatus | null; source?: JobSource | null; field?: JobField | null; q?: string; page?: number; size?: number }): Observable<JobPage> {
    return this.http.get<ApiResponse<JobPage>>(api('/api/v1/admin/off-campus/jobs'), { params: params(q) }).pipe(map(r => r.data));
  }
  create(body: JobRequest): Observable<OffCampusJob> {
    return this.http.post<ApiResponse<OffCampusJob>>(api('/api/v1/admin/off-campus/jobs'), body).pipe(map(r => r.data));
  }
  update(id: string, body: JobRequest): Observable<OffCampusJob> {
    return this.http.put<ApiResponse<OffCampusJob>>(api(`/api/v1/admin/off-campus/jobs/${id}`), body).pipe(map(r => r.data));
  }
  setStatus(id: string, status: JobStatus, sendNotification = true): Observable<OffCampusJob> {
    return this.http.post<ApiResponse<OffCampusJob>>(api(`/api/v1/admin/off-campus/jobs/${id}/status`), { status, sendNotification }).pipe(map(r => r.data));
  }
  bulkStatus(ids: string[], status: JobStatus): Observable<number> {
    return this.http.post<ApiResponse<{ updated: number }>>(api('/api/v1/admin/off-campus/jobs/bulk-status'), { ids, status }).pipe(map(r => r.data.updated));
  }
  importStatus(): Observable<ImportRun> {
    return this.http.get<ApiResponse<ImportRun>>(api('/api/v1/admin/off-campus/import')).pipe(map(r => r.data));
  }
  runImport(): Observable<ImportRun> {
    return this.http.post<ApiResponse<ImportRun>>(api('/api/v1/admin/off-campus/import/run'), {}).pipe(map(r => r.data));
  }
  duplicate(id: string): Observable<OffCampusJob> {
    return this.http.post<ApiResponse<OffCampusJob>>(api(`/api/v1/admin/off-campus/jobs/${id}/duplicate`), {}).pipe(map(r => r.data));
  }
  applicants(id: string): Observable<Applicant[]> {
    return this.http.get<ApiResponse<Applicant[]>>(api(`/api/v1/admin/off-campus/jobs/${id}/applicants`)).pipe(map(r => r.data));
  }

  // ---- student
  studentJobs(): Observable<StudentJobs> {
    return this.http.get<ApiResponse<StudentJobs>>(api('/api/v1/college/student/off-campus')).pipe(map(r => r.data));
  }
  setInterests(fields: JobField[]): Observable<JobField[]> {
    return this.http.put<ApiResponse<JobField[]>>(api('/api/v1/college/student/off-campus/interests'), { fields }).pipe(map(r => r.data));
  }
  click(jobId: string): Observable<{ url: string }> {
    return this.http.post<ApiResponse<{ url: string }>>(api(`/api/v1/college/student/off-campus/${jobId}/click`), {}).pipe(map(r => r.data));
  }
  toggleSave(jobId: string): Observable<boolean> {
    return this.http.post<ApiResponse<{ saved: boolean }>>(api(`/api/v1/college/student/off-campus/${jobId}/save`), {}).pipe(map(r => r.data.saved));
  }
  updateStatus(jobId: string, body: { status: AppStatus; offerCtcLpa?: number | null; note?: string | null }): Observable<StudentJob> {
    return this.http.put<ApiResponse<StudentJob>>(api(`/api/v1/college/student/off-campus/${jobId}/status`), body).pipe(map(r => r.data));
  }

  requestNoc(jobId: string, offerLetter: File, note: string): Observable<Noc> {
    const fd = new FormData();
    fd.append('offerLetter', offerLetter);
    if (note.trim()) fd.append('note', note.trim());
    return this.http.post<ApiResponse<Noc>>(api(`/api/v1/college/student/off-campus/${jobId}/noc`), fd).pipe(map(r => r.data));
  }
  openStudentNocFile(nocId: string, kind: 'noc' | 'offer'): void { this.files.open(`/api/v1/college/student/off-campus/noc/${nocId}/${kind}`); }

  // ---- college
  nocs(status: NocStatus | null): Observable<CollegeNoc[]> {
    return this.http.get<ApiResponse<CollegeNoc[]>>(api('/api/v1/college/off-campus/nocs'), { params: params({ status }) }).pipe(map(r => r.data));
  }
  decideNoc(id: string, status: 'APPROVED' | 'REJECTED', note: string, file: File | null, ctcLpa: number | null = null): Observable<CollegeNoc> {
    const fd = new FormData();
    fd.append('status', status);
    if (ctcLpa != null) fd.append('ctcLpa', String(ctcLpa));
    if (note.trim()) fd.append('note', note.trim());
    if (file) fd.append('file', file);
    return this.http.post<ApiResponse<CollegeNoc>>(api(`/api/v1/college/off-campus/nocs/${id}/decision`), fd).pipe(map(r => r.data));
  }
  openCollegeNocFile(id: string, kind: 'noc' | 'offer'): void { this.files.open(`/api/v1/college/off-campus/nocs/${id}/${kind}`); }
  college(q: { status?: AppStatus | null; q?: string }): Observable<CollegeView> {
    return this.http.get<ApiResponse<CollegeView>>(api('/api/v1/college/off-campus'), { params: params(q) }).pipe(map(r => r.data));
  }
  exportCollege(status: AppStatus | null): Observable<void> {
    return this.files.download('/api/v1/college/off-campus/export', { format: 'xlsx', status }, 'off-campus.xlsx');
  }
}
