import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../../services/token-storage.service';
import { AuthService } from '../../services/auth.service';

/** Requires a valid, unexpired session. Redirects to the matching login page with returnUrl. */
export const authGuard: CanActivateFn = (_route, state) => {
  const storage = inject(TokenStorageService);
  const router = inject(Router);
  const auth = inject(AuthService);
  if (storage.token && !storage.isExpired()) return true;
  auth.clearLocal();
  const login = AuthService.loginFor(state.url);
  return router.createUrlTree([login], { queryParams: { returnUrl: state.url } });
};
