import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { fakeJwt } from '../../testing/jwt';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', children: [] }])] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('routes each role to its home (ORGANIZATION/college staff -> /college)', () => {
    expect(AuthService.homeFor('COLLEGE_ADMIN', 'APPROVED')).toBe('/college/dashboard');
    expect(AuthService.homeFor('COLLEGE_VIEWER', 'PENDING')).toBe('/college/pending-approval');
    expect(AuthService.homeFor('ORGANIZATION')).toBe('/college/dashboard');
    expect(AuthService.homeFor('CANDIDATE')).toBe('/student/dashboard');
    expect(AuthService.homeFor('RECRUITER')).toBe('/company/drives');
    expect(AuthService.homeFor('ADMIN')).toBe('/admin/dashboard');
    expect(AuthService.homeFor(null)).toBe('/login');
  });

  it('login stores the session and updates the role signal', () => {
    const token = fakeJwt(3600);
    let role = '';
    auth.login('tpo@x.edu', 'pw', true).subscribe(s => (role = s.role));
    const req = http.expectOne(r => r.url.endsWith('/api/v1/auth/login'));
    expect(req.request.body).toEqual({ email: 'tpo@x.edu', password: 'pw' });
    req.flush({ success: true, data: { token, userId: 'u1', email: 'tpo@x.edu', fullName: 'T', role: 'COLLEGE_ADMIN', collegeApprovalStatus: 'APPROVED' } });
    expect(role).toBe('COLLEGE_ADMIN');
    expect(auth.isLoggedIn()).toBe(true);
    expect(TestBed.inject(TokenStorageService).token).toBe(token);
  });

  it('logout blacklists the token server-side and clears local state', () => {
    TestBed.inject(TokenStorageService).save({ token: fakeJwt(3600), userId: 'u', email: 'e', fullName: 'f', role: 'CANDIDATE' }, true);
    auth.logout('/login');
    http.expectOne(r => r.url.endsWith('/api/v1/auth/logout')).flush({ success: true });
    expect(TestBed.inject(TokenStorageService).token).toBeNull();
    expect(auth.role()).toBeNull();
  });

  it('reset password sends e-mail + OTP (OTP alone is never enough)', () => {
    auth.resetPassword('a@b.c', '123456', 'NewPass1').subscribe();
    const req = http.expectOne(r => r.url.endsWith('/api/v1/auth/reset-password'));
    expect(req.request.body).toEqual({ email: 'a@b.c', resetToken: '123456', newPassword: 'NewPass1' });
    req.flush({ success: true });
  });
});
