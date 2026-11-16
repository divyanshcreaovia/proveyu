import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../../services/token-storage.service';
import { AuthService } from '../../services/auth.service';

/** Keeps signed-in users away from login / register pages by sending them to their home. */
export const guestGuard: CanActivateFn = (_route, state) => {
  const storage = inject(TokenStorageService);
  if (!storage.token || storage.isExpired()) return true;
  if (storage.role === 'CANDIDATE' && state.url.startsWith('/student')) return inject(Router).createUrlTree(['/student/dashboard']);
  if (storage.role === 'RECRUITER' && state.url.startsWith('/company')) return inject(Router).createUrlTree(['/company/drives']);
  return inject(Router).createUrlTree([AuthService.homeFor(storage.role, storage.get('collegeApprovalStatus'))]);
};
