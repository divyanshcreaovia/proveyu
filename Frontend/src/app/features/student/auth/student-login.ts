import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { AppError } from '../../../core/api';
import { AuthShell } from '../../college/auth/auth-shell';

@Component({
  selector: 'pm-student-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell variant="student" heading="Student sign in" sub="Use the e-mail your placement cell registered for you">
      @if (reason() === 'expired') { <div class="alert alert-warning small py-2">Your session expired. Please sign in again.</div> }
      @if (error()) {
        <div class="alert alert-danger small py-2" role="alert">{{ error() }}
          @if (unverified()) { <a routerLink="/student/signup" class="alert-link d-block mt-1">Activate your account</a> }
        </div>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="mb-3">
          <label for="sl-email" class="form-label small fw-semibold">College e-mail</label>
          <input id="sl-email" type="email" class="form-control" formControlName="email" autocomplete="username"
                 [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          <div class="invalid-feedback">Enter a valid e-mail.</div>
        </div>
        <div class="mb-2">
          <label for="sl-pass" class="form-label small fw-semibold">Password</label>
          <div class="input-group">
            <input id="sl-pass" [type]="show() ? 'text' : 'password'" class="form-control" formControlName="password" autocomplete="current-password"
                   [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
            <button class="btn btn-outline-secondary" type="button" (click)="show.set(!show())" [attr.aria-label]="show() ? 'Hide password' : 'Show password'">
              <i class="bi" [class.bi-eye]="!show()" [class.bi-eye-slash]="show()"></i></button>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center mb-4">
          <div class="form-check">
            <input id="sl-remember" type="checkbox" class="form-check-input" formControlName="remember" />
            <label for="sl-remember" class="form-check-label small">Remember me</label>
          </div>
          <a routerLink="/student/forgot-password" class="small">Forgot password?</a>
        </div>
        <button type="submit" class="btn btn-primary w-100" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Sign in
        </button>
      </form>
      <p class="text-center small mt-4 mb-0">First time here? <a routerLink="/student/signup" class="fw-semibold">Create your student account</a></p>
      <p class="text-center small mt-2 mb-0 text-body-secondary">Placement cell staff? <a routerLink="/college/login">Sign in here</a></p>
    </pm-auth-shell>`,
})
export class StudentLogin {
  private auth = inject(AuthService);
  private router = inject(Router);
  returnUrl = input<string>();
  reason = input<string>();
  busy = signal(false);
  show = signal(false);
  error = signal('');
  unverified = signal(false);
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
        if (s.role !== 'CANDIDATE') {
          // staff accounts have their own portal; do not keep a session here
          this.auth.clearLocal();
          this.busy.set(false);
          this.error.set('This is the student portal. Placement cell staff sign in on the college portal.');
          return;
        }
        const r = this.returnUrl();
        this.router.navigateByUrl(r && r.startsWith('/student/') ? r : '/student/dashboard');
      },
      error: (e: AppError) => {
        this.busy.set(false);
        this.unverified.set(e.code === 'EMAIL_NOT_VERIFIED');
        this.error.set(e.message);
      },
    });
  }
}
