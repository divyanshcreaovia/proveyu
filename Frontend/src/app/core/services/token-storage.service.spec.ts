import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from './token-storage.service';
import { fakeJwt } from '../../testing/jwt';

describe('TokenStorageService', () => {
  let svc: TokenStorageService;
  beforeEach(() => { localStorage.clear(); sessionStorage.clear(); svc = TestBed.inject(TokenStorageService); });

  it('remember-me stores in localStorage, otherwise sessionStorage', () => {
    const s = { token: fakeJwt(60), userId: 'u1', email: 'a@b.c', fullName: 'A', role: 'COLLEGE_ADMIN' as const, collegeId: 'c1' };
    svc.save(s, true);
    expect(localStorage.getItem('token')).toBe(s.token);
    expect(sessionStorage.getItem('token')).toBeNull();
    svc.save(s, false);
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBe(s.token);
    expect(svc.role).toBe('COLLEGE_ADMIN');
    expect(svc.get('collegeId')).toBe('c1');
  });

  it('detects expiry from the JWT payload', () => {
    expect(svc.isExpired(fakeJwt(120))).toBe(false);
    expect(svc.isExpired(fakeJwt(-5))).toBe(true);
    expect(svc.isExpired(null)).toBe(true);
    expect(svc.expiresAt('garbage')).toBeNull();
  });

  it('clear removes every session key from both storages', () => {
    svc.save({ token: fakeJwt(60), userId: 'u', email: 'e', fullName: 'f', role: 'CANDIDATE' }, true);
    sessionStorage.setItem('role', 'X');
    svc.clear();
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('role')).toBeNull();
  });
});
