import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap, catchError, of } from 'rxjs';
import { api, ApiResponse } from '../api';
import {
  AuthSession, CollegeRegisterRequest, ForgotPasswordResponse, InviteInfo, RegisterResponse, Role,
} from '../models/college.models';
import { TokenStorageService } from './token-storage.service';
import { ToastService } from './toast.service';

export const COLLEGE_ROLES: Role[] = ['COLLEGE_ADMIN', 'COLLEGE_COORDINATOR', 'COLLEGE_VIEWER'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storage = inject(TokenStorageService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly role = signal<Role | null>(this.storage.isExpired() ? null : this.storage.role);
  readonly isLoggedIn = computed(() => this.role() !== null);

  constructor() {
    if (this.storage.isExpired()) this.storage.clear();
    else this.scheduleExpiry();
  }

  /** Landing route for a role after login. */
  static homeFor(role: Role | null, approval?: string | null): string {
    switch (role) {
      case 'COLLEGE_ADMIN': case 'COLLEGE_COORDINATOR': case 'COLLEGE_VIEWER':
        return approval && approval !== 'APPROVED' ? '/college/pending-approval' : '/college/dashboard';
      case 'ORGANIZATION': return '/college/dashboard';
      case 'RECRUITER': return '/company/drives'; // company HR / panels work in the Drive Room
      case 'ADMIN': return '/admin/colleges';
      case 'CANDIDATE': return '/student/dashboard'; // students use the student portal (the old candidate portal has no sign-up)
      default: return '/login';
    }
  }

  /** Login page for the portal a URL belongs to. */
  static loginFor(url: string, wasCollege = false): string {
    if (url.startsWith('/student')) return '/student/login';
    if (url.startsWith('/company')) return '/company/login';
    return wasCollege || url.startsWith('/college') ? '/college/login' : '/login';
  }

  login(email: string, password: string, remember: boolean): Observable<AuthSession> {
    return this.http.post<ApiResponse<AuthSession>>(api('/api/v1/auth/login'), { email, password })
      .pipe(map(r => r.data), tap(s => this.startSession(s, remember)));
  }

  startSession(s: AuthSession, remember = true): void {
    this.storage.save(s, remember);
    this.role.set(s.role);
    this.scheduleExpiry();
  }

  registerCollege(req: CollegeRegisterRequest): Observable<ApiResponse<RegisterResponse>> {
    return this.http.post<ApiResponse<RegisterResponse>>(api('/api/v1/college/auth/register'), req);
  }

  verifyEmail(token: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(api('/api/v1/auth/verify-email'), { token });
  }

  resendVerification(email: string): Observable<ApiResponse<{ devToken?: string } | null>> {
    return this.http.post<ApiResponse<{ devToken?: string } | null>>(api('/api/v1/auth/resend-verification'), { email });
  }

  forgotPassword(email: string): Observable<ForgotPasswordResponse> {
    return this.http.post<ApiResponse<ForgotPasswordResponse>>(api('/api/v1/auth/forgot-password'), { email }).pipe(map(r => r.data));
  }

  resetPassword(email: string, otp: string, newPassword: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(api('/api/v1/auth/reset-password'), { email, resetToken: otp, newPassword });
  }

  inviteInfo(token: string): Observable<InviteInfo> {
    return this.http.get<ApiResponse<InviteInfo>>(api(`/api/v1/college/auth/invite/${encodeURIComponent(token)}`)).pipe(map(r => r.data));
  }

  acceptInvite(body: { token: string; password: string; fullName?: string; phone?: string }): Observable<AuthSession> {
    return this.http.post<ApiResponse<AuthSession>>(api('/api/v1/college/auth/accept-invite'), body)
      .pipe(map(r => r.data), tap(s => this.startSession(s, false)));
  }

  /** Blacklists the token server-side (best effort) and clears local state. */
  logout(redirect = '/login', message?: string): void {
    const hadToken = !!this.storage.token;
    const done = () => {
      this.clearLocal();
      if (message) this.toast.info(message);
      this.router.navigateByUrl(redirect);
    };
    if (!hadToken) { done(); return; }
    this.http.post(api('/api/v1/auth/logout'), {}).pipe(catchError(() => of(null))).subscribe(done);
  }

  clearLocal(): void {
    clearTimeout(this.expiryTimer);
    this.storage.clear();
    this.role.set(null);
  }

  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    const exp = this.storage.expiresAt();
    if (!exp) return;
    const ms = exp - Date.now();
    if (ms <= 0) return;
    // setTimeout max is ~24.8 days
    this.expiryTimer = setTimeout(() => {
      const isCollege = COLLEGE_ROLES.includes(this.role() as Role);
      this.clearLocal();
      this.toast.warning('Your session has expired. Please sign in again.');
      this.router.navigateByUrl(AuthService.loginFor(this.router.url, isCollege));
    }, Math.min(ms, 2_000_000_000));
  }
}
