import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, ApiResponse, Page } from '../core/api';
import {
  DocType, ForgotPasswordResponse, Offer, PortalAnnouncement, PortalApplication, PortalContact, PortalDrive, PortalInterview, PortalProfile,
  PortalProfileUpdate, PortalRound, StudentDocument,
} from '../core/models/college.models';
import { SILENT_ERRORS } from '../core/interceptors/error.interceptor';
import { FileService } from './file.service';

const BASE = '/api/v1/college/student';

/** Student (candidate) self-service portal: drives, applications, rounds, interviews, offers, announcements, chat contacts, documents. */
@Injectable({ providedIn: 'root' })
export class StudentPortalService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  /** The signed-in student's profile, loaded once by the student shell. */
  readonly me = signal<PortalProfile | null>(null);
  readonly unreadAnnouncements = signal(0);
  readonly firstName = computed(() => this.me()?.name.split(' ')[0] ?? '');

  /** Public: matches e-mail + roll number and e-mails a 6-digit code (password is then set with AuthService.resetPassword). */
  signup(email: string, rollNumber: string): Observable<ForgotPasswordResponse> {
    return this.http.post<ApiResponse<ForgotPasswordResponse>>(api('/api/v1/college/auth/student-signup'), { email, rollNumber }).pipe(map(r => r.data));
  }

  profile(): Observable<PortalProfile> {
    return this.http.get<ApiResponse<PortalProfile>>(api(`${BASE}/profile`), { context: new HttpContext().set(SILENT_ERRORS, true) })
      .pipe(map(r => r.data), tap(p => { this.me.set(p); this.unreadAnnouncements.set(p.unreadAnnouncements); }));
  }
  updateProfile(body: PortalProfileUpdate): Observable<PortalProfile> {
    return this.http.patch<ApiResponse<PortalProfile>>(api(`${BASE}/profile`), body).pipe(map(r => r.data), tap(p => this.me.set(p)));
  }
  drives(): Observable<PortalDrive[]> { return this.http.get<ApiResponse<PortalDrive[]>>(api(`${BASE}/drives`)).pipe(map(r => r.data)); }
  apply(driveId: string): Observable<PortalApplication> {
    return this.http.post<ApiResponse<PortalApplication>>(api(`${BASE}/drives/${driveId}/apply`), {}).pipe(map(r => r.data));
  }
  applications(): Observable<PortalApplication[]> { return this.http.get<ApiResponse<PortalApplication[]>>(api(`${BASE}/applications`)).pipe(map(r => r.data)); }
  rounds(applicationId: string): Observable<PortalRound[]> {
    return this.http.get<ApiResponse<PortalRound[]>>(api(`${BASE}/applications/${applicationId}/rounds`)).pipe(map(r => r.data));
  }
  withdraw(id: string): Observable<PortalApplication> {
    return this.http.post<ApiResponse<PortalApplication>>(api(`${BASE}/applications/${id}/withdraw`), {}).pipe(map(r => r.data));
  }
  interviews(): Observable<PortalInterview[]> { return this.http.get<ApiResponse<PortalInterview[]>>(api(`${BASE}/interviews`)).pipe(map(r => r.data)); }
  offers(): Observable<Offer[]> { return this.http.get<ApiResponse<Offer[]>>(api(`${BASE}/offers`)).pipe(map(r => r.data)); }
  respond(id: string, accept: boolean): Observable<Offer> {
    return this.http.post<ApiResponse<Offer>>(api(`${BASE}/offers/${id}/respond`), { accept }).pipe(map(r => r.data));
  }
  openOfferLetter(id: string): void { this.files.open(`${BASE}/offers/${id}/letter`); }

  announcements(page = 0, size = 20): Observable<Page<PortalAnnouncement>> {
    return this.http.get<ApiResponse<Page<PortalAnnouncement>>>(api(`${BASE}/announcements`), { params: { page, size } }).pipe(map(r => r.data));
  }
  markAnnouncementRead(id: string): Observable<unknown> {
    this.unreadAnnouncements.update(n => Math.max(0, n - 1));
    return this.http.post(api(`${BASE}/announcements/${id}/read`), {});
  }
  markAllAnnouncementsRead(): Observable<unknown> {
    this.unreadAnnouncements.set(0);
    return this.http.post(api(`${BASE}/announcements/read-all`), {});
  }
  contacts(): Observable<PortalContact[]> { return this.http.get<ApiResponse<PortalContact[]>>(api(`${BASE}/chat/contacts`)).pipe(map(r => r.data)); }

  documents(): Observable<StudentDocument[]> { return this.http.get<ApiResponse<StudentDocument[]>>(api(`${BASE}/documents`)).pipe(map(r => r.data)); }
  upload(docType: DocType, file: File, label?: string): Observable<StudentDocument> {
    const params: Record<string, string> = { docType };
    if (label?.trim()) params['label'] = label.trim();
    return this.http.post<ApiResponse<StudentDocument>>(api(`${BASE}/documents`), FileService.form(file), { params }).pipe(map(r => r.data));
  }
  previewDocument(id: string): void { this.files.open(`${BASE}/documents/${id}/file`); }
  downloadDocument(d: StudentDocument): Observable<void> { return this.files.download(`${BASE}/documents/${d.id}/file`, {}, d.fileName); }
  deleteDocument(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/documents/${id}`)); }
}
