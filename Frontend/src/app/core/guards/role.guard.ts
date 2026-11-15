import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/college.models';
import { TokenStorageService } from '../../services/token-storage.service';

/** Usage: `canActivate: [authGuard, roleGuard], data: { roles: ['COLLEGE_ADMIN'] }`. */
export const roleGuard: CanActivateFn = route => {
  const roles = (route.data?.['roles'] ?? []) as Role[];
  const role = inject(TokenStorageService).role;
  if (!roles.length || (role && roles.includes(role))) return true;
  return inject(Router).createUrlTree(['/forbidden']);
};
