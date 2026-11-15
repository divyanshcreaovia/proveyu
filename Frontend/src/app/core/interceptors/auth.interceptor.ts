import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../environments/environment';
import { AuthService, COLLEGE_ROLES } from '../../services/auth.service';
import { TokenStorageService } from '../../services/token-storage.service';

/**
 * Attaches the JWT to API calls, logs out + redirects on 401 and sends 403 to /forbidden.
 * Public auth endpoints are left untouched so login failures surface as normal errors.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApi = req.url.startsWith(environment.apiBaseUrl + '/api/') || req.url.startsWith('/api/');
  const isPublicAuth = /\/api\/v1\/(auth\/(login|register|forgot-password|reset-password|verify-otp|verify-email|resend-verification)|college\/auth\/)/.test(req.url);
  const token = storage.token;
  const authed = isApi && token && !isPublicAuth ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (isApi && !isPublicAuth) {
        if (err.status === 401) {
          const wasCollege = COLLEGE_ROLES.includes(auth.role() as never);
          auth.clearLocal();
          const code = err.error?.errorCode;
          const msg = code === 'SESSION_STALE' ? 'Your role changed. Please sign in again.' : undefined;
          router.navigate([wasCollege || router.url.startsWith('/college') ? '/college/login' : '/login'],
            { queryParams: { returnUrl: router.url, ...(msg ? { reason: 'stale' } : { reason: 'expired' }) } });
        } else if (err.status === 403) {
          const code = err.error?.errorCode;
          if (code === 'COLLEGE_PENDING_APPROVAL' || code === 'COLLEGE_REJECTED') router.navigateByUrl('/college/pending-approval');
          else if (code === 'FORBIDDEN' && req.method === 'GET') router.navigateByUrl('/forbidden');
        }
      }
      return throwError(() => err);
    }),
  );
};
