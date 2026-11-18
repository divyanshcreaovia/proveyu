import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TokenStorageService } from '../../core/services/token-storage.service';

@Component({
  selector: 'pm-forbidden',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-forbid d-flex align-items-center justify-content-center min-vh-100 p-3">
      <div class="text-center">
        <div class="pm-forbid-code">403</div>
        <h1 class="h4 fw-bold">You don't have access to this page</h1>
        <p class="text-body-secondary">Your role does not allow this action. Ask your administrator if you need access.</p>
        <a class="btn btn-primary" [routerLink]="home">Go to my home</a>
      </div>
    </div>`,
  styles: [`.pm-forbid-code { font-size: 4rem; font-weight: 800; color: #2563eb; line-height: 1; }`],
})
export class Forbidden {
  private storage = inject(TokenStorageService);
  home = AuthService.homeFor(this.storage.role, this.storage.get('collegeApprovalStatus'));
}
