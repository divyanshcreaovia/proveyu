import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AppError } from '../../../core/api';
import { AuthShell } from '../../college/auth/auth-shell';

/** Sign-in for college leadership (CXO), in the same layout as the other portals' sign-in pages. Accounts are created by ProveYu. */
@Component({
  selector: 'pm-cxo-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Leadership sign in" sub="For principals, directors and management. Accounts are set up by the ProveYu team." variant="cxo">
      @if (reason() === 'expired') { <div class="alert alert-warning small py-2">Your session expired. Please sign in again.</div> }
      @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="mb-3">
          <label for="xl-email" class="form-label small fw-semibold">E-mail</label>
          <input id="xl-email" type="email" class="form-control" formControlName="email" autocomplete="username"
                 [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          <div class="invalid-feedback">Enter a valid e-mail.</div>
        </div>
        <div class="mb-2">
          <label for="xl-pass" class="form-label small fw-semibold">Password</label>
          <div class="input-group">
            <input id="xl-pass" [type]="show() ? 'text' : 'password'" class="form-control" formControlName="password" autocomplete="current-password"
                   [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
            <button class="btn btn-outline-secondary" type="button" (click)="show.set(!show())" [attr.aria-label]="show() ? 'Hide password' : 'Show password'">
              <i class="bi" [class.bi-eye]="!show()" [class.bi-eye-slash]="show()"></i></button>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center mb-4">
          <div class="form-check">
            <input id="xl-remember" type="checkbox" class="form-check-input" formControlName="remember" />
            <label for="xl-remember" class="form-check-label small">Remember me</label>
          </div>
          <a routerLink="/cxo/forgot-password" class="small">Forgot password?</a>
        </div>
        <button type="submit" class="btn btn-primary w-100" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Sign in
        </button>
      </form>
      <p class="text-center small mt-4 mb-0 text-body-secondary">Placement cell staff? <a routerLink="/college/login">College sign in</a></p>
    </pm-auth-shell>`,
})
export class CxoLogin {
  private auth = inject(AuthService);
  private router = inject(Router);
  returnUrl = input<string>();
  reason = input<string>();
  busy = signal(false);
  show = signal(false);
  error = signal('');
  form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
    remember: [true],
  });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const { email, password, remember } = this.form.getRawValue();
    this.auth.login(email.trim(), password, remember).subscribe({
      next: s => {
        if (s.role !== 'CXO') {
          // other roles have their own portals: do not keep their session here
          this.auth.logout('/cxo/login');
          this.busy.set(false);
          this.error.set('This sign-in is for college leadership accounts. Placement staff, students and recruiters use their own portal.');
          return;
        }
        const back = this.returnUrl();
        this.router.navigateByUrl(back && back.startsWith('/cxo') ? back : AuthService.homeFor(s.role));
      },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
