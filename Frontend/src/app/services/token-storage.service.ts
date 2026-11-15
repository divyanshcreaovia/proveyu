import { Injectable } from '@angular/core';
import { AuthSession, Role } from '../core/models/college.models';

const KEYS = ['token', 'role', 'userId', 'email', 'candidateFullName', 'collegeId', 'collegeName', 'collegeApprovalStatus'] as const;

/**
 * Stores the session. "Remember me" -> localStorage (survives restarts), otherwise sessionStorage (tab only).
 * Key names are kept compatible with the existing candidate / recruiter pages.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  save(session: AuthSession, remember: boolean): void {
    this.clear();
    const store = remember ? localStorage : sessionStorage;
    const values: Record<(typeof KEYS)[number], string | undefined> = {
      token: session.token, role: session.role, userId: session.userId, email: session.email, candidateFullName: session.fullName,
      collegeId: session.collegeId, collegeName: session.collegeName, collegeApprovalStatus: session.collegeApprovalStatus,
    };
    for (const k of KEYS) {
      const v = values[k];
      if (v) store.setItem(k, v);
    }
  }

  update(key: (typeof KEYS)[number], value: string): void {
    const store = localStorage.getItem('token') ? localStorage : sessionStorage;
    store.setItem(key, value);
  }

  get(key: (typeof KEYS)[number]): string | null {
    return sessionStorage.getItem(key) ?? localStorage.getItem(key);
  }

  get token(): string | null { return this.get('token'); }
  get role(): Role | null { return this.get('role') as Role | null; }

  clear(): void {
    for (const k of KEYS) { localStorage.removeItem(k); sessionStorage.removeItem(k); }
  }

  /** Expiry (ms since epoch) read from the JWT payload; null if unreadable. */
  expiresAt(token = this.token): number | null {
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }

  isExpired(token = this.token): boolean {
    const exp = this.expiresAt(token);
    return !token || (exp !== null && exp <= Date.now());
  }
}
