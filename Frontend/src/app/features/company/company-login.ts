import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AppError } from '../../core/api';
import { AuthShell } from '../college/auth/auth-shell';

/** Sign in for company HR / panel members invited to a campus drive. */
@Component({
  selector: 'pm-company-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell variant="company" heading="Recruiter sign in" sub="For company HR and interview panels invited to a campus drive">
      @if (reason() === 'expired') { <div class="alert alert-warning small py-2">Your session expired. Please sign in again.</div> }
      @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="mb-3">
          <label for="rl-email" class="form-label small fw-semibold">Work e-mail</label>
          <input id="rl-email" type="email" class="form-control" formControlName="email" autocomplete="username"
                 [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          <div class="invalid-feedback">Enter a valid e-mail.</div>
        </div>
        <div class="mb-2">
          <label for="rl-pass" class="form-label small fw-semibold">Password</label>
          <input id="rl-pass" type="password" class="form-control" formControlName="password" autocomplete="current-password"
                 [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
        </div>
        <div class="d-flex justify-content-end mb-4"><a routerLink="/company/forgot-password" class="small">Forgot password?</a></div>
        <button type="submit" class="btn btn-primary w-100" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Sign in
        </button>
      </form>
      <p class="text-center small mt-4 mb-0 text-body-secondary">No account yet? Open the invitation link the placement cell sent you.</p>
    </pm-auth-shell>`,
})
export class CompanyLogin {
  private auth = inject(AuthService);
  private router = inject(Router);
  returnUrl = input<string>();
  reason = input<string>();
  busy = signal(false);
  error = signal('');
  form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password, true).subscribe({
      next: s => {
        if (s.role !== 'RECRUITER') {
          this.auth.clearLocal();
          this.busy.set(false);
          this.error.set('This sign-in is for company recruiters. Students and placement staff have their own portals.');
          return;
        }
        const r = this.returnUrl();
        this.router.navigateByUrl(r && r.startsWith('/company/') ? r : '/company/drives');
      },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
