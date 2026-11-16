import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { guestGuard } from './guest.guard';
import { TokenStorageService } from '../../services/token-storage.service';
import { fakeJwt } from '../../testing/jwt';

const run = (guard: typeof authGuard, data: Record<string, unknown> = {}, url = '/college/students') =>
  TestBed.runInInjectionContext(() => guard({ data } as unknown as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
const path = (r: unknown) => (r instanceof UrlTree ? TestBed.inject(Router).serializeUrl(r) : r);

describe('route guards', () => {
  let storage: TokenStorageService;
  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    storage = TestBed.inject(TokenStorageService);
  });
  const login = (role: string, exp = 3600) =>
    storage.save({ token: fakeJwt(exp), userId: 'u', email: 'e', fullName: 'f', role: role as never, collegeApprovalStatus: 'APPROVED' }, true);

  it('authGuard sends anonymous users to the right login with returnUrl', () => {
    expect(path(run(authGuard))).toBe('/college/login?returnUrl=%2Fcollege%2Fstudents');
    expect(path(run(authGuard, {}, '/candidate/dashboard'))).toBe('/login?returnUrl=%2Fcandidate%2Fdashboard');
    expect(path(run(authGuard, {}, '/student/offers'))).toBe('/student/login?returnUrl=%2Fstudent%2Foffers');
  });

  it('authGuard rejects expired sessions and clears them', () => {
    login('COLLEGE_ADMIN', -10);
    expect(run(authGuard)).toBeInstanceOf(UrlTree);
    expect(storage.token).toBeNull();
    login('COLLEGE_ADMIN');
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard allows listed roles and forbids others', () => {
    login('COLLEGE_VIEWER');
    expect(run(roleGuard, { roles: ['COLLEGE_ADMIN', 'COLLEGE_VIEWER'] })).toBe(true);
    expect(path(run(roleGuard, { roles: ['COLLEGE_ADMIN'] }))).toBe('/forbidden');
    expect(run(roleGuard, {})).toBe(true);
  });

  it('guestGuard redirects signed-in users to their home', () => {
    expect(run(guestGuard)).toBe(true);
    login('COLLEGE_COORDINATOR');
    expect(path(run(guestGuard))).toBe('/college/dashboard');
    login('CANDIDATE');
    expect(path(run(guestGuard))).toBe('/candidate/dashboard');
    expect(path(run(guestGuard, {}, '/student/login'))).toBe('/student/dashboard');
  });
});
