import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../../services/token-storage.service';
import { AuthService } from '../../services/auth.service';

/** Keeps signed-in users away from login / register pages by sending them to their home. */
export const guestGuard: CanActivateFn = () => {
  const storage = inject(TokenStorageService);
  if (!storage.token || storage.isExpired()) return true;
  return inject(Router).createUrlTree([AuthService.homeFor(storage.role, storage.get('collegeApprovalStatus'))]);
};
