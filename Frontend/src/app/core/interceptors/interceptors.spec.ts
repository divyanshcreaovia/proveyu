import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { loadingInterceptor } from './loading.interceptor';
import { TokenStorageService } from '../../services/token-storage.service';
import { ToastService } from '../../services/toast.service';
import { LoadingService } from '../../services/loading.service';
import { environment } from '../environments/environment';
import { AppError } from '../api';
import { fakeJwt } from '../../testing/jwt';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let storage: TokenStorageService;
  const url = `${environment.apiBaseUrl}/api/v1/college/students`;

  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([loadingInterceptor, errorInterceptor, authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    storage.save({ token: fakeJwt(3600), userId: 'u', email: 'e', fullName: 'f', role: 'COLLEGE_ADMIN' }, true);
  });
  afterEach(() => ctrl.verify());

  it('attaches the bearer token to API calls but not to public auth endpoints', () => {
    http.get(url).subscribe();
    expect(ctrl.expectOne(url).request.headers.get('Authorization')).toBe(`Bearer ${storage.token}`);
    http.post(`${environment.apiBaseUrl}/api/v1/auth/login`, {}).subscribe();
    expect(ctrl.expectOne(r => r.url.endsWith('/auth/login')).request.headers.has('Authorization')).toBe(false);
  });

  it('401 clears the session and redirects to login', () => {
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.get(url).subscribe({ error: () => undefined });
    ctrl.expectOne(url).flush({ success: false, errorCode: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
    expect(storage.token).toBeNull();
    expect(nav).toHaveBeenCalled();
    expect(nav.mock.calls[0][0]).toEqual(['/college/login']);
  });

  it('403 on GET goes to /forbidden; pending approval goes to the pending page', () => {
    const nav = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http.get(url).subscribe({ error: () => undefined });
    ctrl.expectOne(url).flush({ errorCode: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    expect(nav).toHaveBeenLastCalledWith('/forbidden');
    http.get(url).subscribe({ error: () => undefined });
    ctrl.expectOne(url).flush({ errorCode: 'COLLEGE_PENDING_APPROVAL' }, { status: 403, statusText: 'Forbidden' });
    expect(nav).toHaveBeenLastCalledWith('/college/pending-approval');
  });

  it('normalizes errors and shows a toast (but not for field validation errors)', () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'error');
    let err: AppError | undefined;
    http.post(url, {}).subscribe({ error: e => (err = e) });
    ctrl.expectOne(url).flush({ message: 'Roll number exists', errorCode: 'DUPLICATE_ROLL' }, { status: 409, statusText: 'Conflict' });
    expect(err).toMatchObject({ status: 409, code: 'DUPLICATE_ROLL', message: 'Roll number exists' });
    expect(toast).toHaveBeenCalledWith('Roll number exists');

    toast.mockClear();
    http.post(url, {}).subscribe({ error: e => (err = e) });
    ctrl.expectOne(url).flush({ message: 'Validation failed', errorCode: 'VALIDATION_ERROR', fieldErrors: { email: 'invalid' } }, { status: 400, statusText: 'Bad' });
    expect(err?.fieldErrors).toEqual({ email: 'invalid' });
    expect(toast).not.toHaveBeenCalled();
  });

  it('tracks in-flight requests for the global progress bar', () => {
    const loading = TestBed.inject(LoadingService);
    http.get(url).subscribe();
    expect(loading.busy()).toBe(true);
    ctrl.expectOne(url).flush({ success: true, data: [] });
    expect(loading.busy()).toBe(false);
  });
});
