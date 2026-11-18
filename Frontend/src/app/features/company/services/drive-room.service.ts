import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, ApiResponse } from '../../../core/api';
import { AuthSession, Offer, RoundResultRow } from '../../../core/models/college.models';
import { AuthService } from '../../../core/services/auth.service';
import { FileService } from '../../../core/services/file.service';

// ---------------------------------------------------------------- models (mirror com.proveyu.college.application.dto.DriveRoomDtos)
export type AccessStatus = 'INVITED' | 'ACTIVE' | 'REVOKED';
export type ResultValue = 'PENDING' | 'PASS' | 'FAIL' | 'ABSENT' | 'HOLD';
export interface RecruiterAccess {
  id: string; driveId: string; email: string; fullName: string; designation?: string; status: AccessStatus; shareDetails: boolean;
  expiresAt?: string; acceptedAt?: string; lastAccessAt?: string; createdAt: string; devInviteToken?: string;
}
export interface RecruiterInviteInfo { collegeName: string; driveTitle: string; companyName: string; email: string; fullName: string; existingAccount: boolean; expiresAt: string; }
export interface RoomDriveCard {
  driveId: string; collegeName: string; companyName: string; title: string; roleTitle: string; status: string; driveDate?: string; ctcLpa?: number;
  applicants: number; shortlisted: number; selected: number; offers: number;
}
export interface RoomRound { id: string; seq: number; name: string; roundType: string; scheduledAt?: string; status: 'PENDING' | 'ONGOING' | 'COMPLETED'; resultCounts: Record<string, number>; }
export interface RoomSummary {
  driveId: string; collegeName: string; companyName: string; companyId: string; title: string; roleTitle: string; driveType: string; status: string;
  ctcLpa?: number; stipendMonthly?: number; location?: string; mode: string; venue?: string; driveDate?: string; registrationDeadline?: string;
  jobDescription?: string; shareDetails: boolean; canEdit: boolean; funnel: Record<string, number>; rounds: RoomRound[]; myName: string;
}
export interface RoomApplicant {
  applicationId: string; studentId: string; name: string; rollNumber: string; branch?: string; branchCode?: string; batchYear?: number; degree?: string;
  cgpa?: number; activeBacklogs?: number; skills: string[]; linkedinUrl?: string; githubUrl?: string; email?: string; phone?: string;
  tenthPercent?: number; twelfthPercent?: number; status: string; currentRound: number; hasResume: boolean; roundResults: Record<string, ResultValue>;
  offerStatus?: string; offerId?: string; appliedAt: string;
}
export interface Scorecard {
  id: string; roundId: string; applicationId: string; userId: string; interviewerName: string; technical?: number; communication?: number;
  attitude?: number; result: ResultValue; remarks?: string; updatedAt: string; mine: boolean;
}
export interface ScorecardInput { technical?: number; communication?: number; attitude?: number; result: ResultValue; remarks?: string; }
export interface BulkResult { updated: number; skipped: string[]; }
export interface OfferBatchResult { created: number; errors: string[]; }

const C = '/api/v1/college';
const R = '/api/v1/recruiter/campus/drives';

/** Drive Room: TPO manages company access; company HR / panel works on the drive. */
@Injectable({ providedIn: 'root' })
export class DriveRoomService {
  private http = inject(HttpClient);
  private files = inject(FileService);
  private auth = inject(AuthService);

  // ---------------------------------------------------------------- TPO
  access(driveId: string): Observable<RecruiterAccess[]> {
    return this.http.get<ApiResponse<RecruiterAccess[]>>(api(`${C}/drives/${driveId}/recruiters`)).pipe(map(r => r.data));
  }
  invite(driveId: string, b: { email: string; fullName: string; designation?: string; shareDetails: boolean }): Observable<RecruiterAccess> {
    return this.http.post<ApiResponse<RecruiterAccess>>(api(`${C}/drives/${driveId}/recruiters`), b).pipe(map(r => r.data));
  }
  resend(driveId: string, id: string): Observable<RecruiterAccess> {
    return this.http.post<ApiResponse<RecruiterAccess>>(api(`${C}/drives/${driveId}/recruiters/${id}/resend`), {}).pipe(map(r => r.data));
  }
  setShare(driveId: string, id: string, shareDetails: boolean): Observable<RecruiterAccess> {
    return this.http.patch<ApiResponse<RecruiterAccess>>(api(`${C}/drives/${driveId}/recruiters/${id}`), { shareDetails }).pipe(map(r => r.data));
  }
  revoke(driveId: string, id: string): Observable<RecruiterAccess> {
    return this.http.post<ApiResponse<RecruiterAccess>>(api(`${C}/drives/${driveId}/recruiters/${id}/revoke`), {}).pipe(map(r => r.data));
  }
  collegeScorecards(roundId: string): Observable<Scorecard[]> {
    return this.http.get<ApiResponse<Scorecard[]>>(api(`${C}/rounds/${roundId}/scorecards`)).pipe(map(r => r.data));
  }

  // ---------------------------------------------------------------- invitation (public)
  inviteInfo(token: string): Observable<RecruiterInviteInfo> {
    return this.http.get<ApiResponse<RecruiterInviteInfo>>(api(`${C}/auth/recruiter-invite/${encodeURIComponent(token)}`)).pipe(map(r => r.data));
  }
  accept(token: string, password: string, fullName?: string): Observable<AuthSession> {
    return this.http.post<ApiResponse<AuthSession>>(api(`${C}/auth/recruiter-accept`), { token, password, fullName })
      .pipe(map(r => r.data), tap(s => this.auth.startSession(s, true)));
  }

  // ---------------------------------------------------------------- company
  drives(): Observable<RoomDriveCard[]> { return this.http.get<ApiResponse<RoomDriveCard[]>>(api(R)).pipe(map(r => r.data)); }
  summary(d: string): Observable<RoomSummary> { return this.http.get<ApiResponse<RoomSummary>>(api(`${R}/${d}`)).pipe(map(r => r.data)); }
  applicants(d: string): Observable<RoomApplicant[]> { return this.http.get<ApiResponse<RoomApplicant[]>>(api(`${R}/${d}/applicants`)).pipe(map(r => r.data)); }
  openResume(d: string, appId: string): void { this.files.open(`${R}/${d}/applicants/${appId}/resume`); }
  downloadResumes(d: string, applicationIds: string[]): Observable<void> {
    return this.http.post(api(`${R}/${d}/resumes.zip`), applicationIds, { responseType: 'blob', observe: 'response' })
      .pipe(map((res: HttpResponse<Blob>) => this.files.save(res.body!, 'resumes.zip')));
  }
  setStatus(d: string, applicationIds: string[], status: string, remarks?: string): Observable<BulkResult> {
    return this.http.patch<ApiResponse<BulkResult>>(api(`${R}/${d}/applications/status`), { applicationIds, status, remarks }).pipe(map(r => r.data));
  }
  startRound(d: string, roundId: string): Observable<unknown> { return this.http.post(api(`${R}/${d}/rounds/${roundId}/start`), {}); }
  completeRound(d: string, roundId: string): Observable<unknown> { return this.http.post(api(`${R}/${d}/rounds/${roundId}/complete`), {}); }
  results(d: string, roundId: string): Observable<RoundResultRow[]> {
    return this.http.get<ApiResponse<RoundResultRow[]>>(api(`${R}/${d}/rounds/${roundId}/results`)).pipe(map(r => r.data));
  }
  saveResults(d: string, roundId: string, results: { applicationId: string; result: ResultValue; score?: number | null; remarks?: string }[]): Observable<RoundResultRow[]> {
    return this.http.put<ApiResponse<RoundResultRow[]>>(api(`${R}/${d}/rounds/${roundId}/results`), { results }).pipe(map(r => r.data));
  }
  scorecards(d: string, roundId: string): Observable<Scorecard[]> {
    return this.http.get<ApiResponse<Scorecard[]>>(api(`${R}/${d}/rounds/${roundId}/scorecards`)).pipe(map(r => r.data));
  }
  saveScorecard(d: string, roundId: string, appId: string, s: ScorecardInput): Observable<Scorecard> {
    return this.http.put<ApiResponse<Scorecard>>(api(`${R}/${d}/rounds/${roundId}/scorecards/${appId}`), s).pipe(map(r => r.data));
  }
  offers(d: string): Observable<Offer[]> { return this.http.get<ApiResponse<Offer[]>>(api(`${R}/${d}/offers`)).pipe(map(r => r.data)); }
  makeOffers(d: string, b: { applicationIds: string[]; ctcLpa?: number | null; roleTitle?: string; joiningDate?: string | null }): Observable<OfferBatchResult> {
    return this.http.post<ApiResponse<OfferBatchResult>>(api(`${R}/${d}/offers`), b).pipe(map(r => r.data));
  }
  uploadLetter(d: string, offerId: string, file: File): Observable<Offer> {
    return this.http.post<ApiResponse<Offer>>(api(`${R}/${d}/offers/${offerId}/letter`), FileService.form(file)).pipe(map(r => r.data));
  }
  downloadReport(d: string, format: 'xlsx' | 'pdf'): Observable<void> { return this.files.download(`${R}/${d}/report`, { format }, `drive-report.${format}`); }
}
