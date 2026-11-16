import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpContext } from '@angular/common/http';
import { AuthService } from '../../../services/auth.service';
import { AppError } from '../../../core/api';
import { AuthShell } from './auth-shell';

@Component({
  selector: 'pm-college-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="College sign in" sub="Training & Placement Officers and placement team members">
      @if (reason() === 'expired') { <div class="alert alert-warning small py-2">Your session expired. Please sign in again.</div> }
      @if (reason() === 'stale') { <div class="alert alert-info small py-2">Your role changed. Please sign in again.</div> }
      @if (error()) {
        <div class="alert alert-danger small py-2" role="alert">{{ error() }}
          @if (unverified()) { <a routerLink="/college/verify-email" [queryParams]="{ email: form.value.email }" class="alert-link d-block mt-1">Resend verification e-mail</a> }
        </div>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="mb-3">
          <label for="cl-email" class="form-label small fw-semibold">Official e-mail</label>
          <input id="cl-email" type="email" class="form-control" formControlName="email" autocomplete="username"
                 [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          <div class="invalid-feedback">Enter a valid e-mail.</div>
        </div>
        <div class="mb-2">
          <label for="cl-pass" class="form-label small fw-semibold">Password</label>
          <div class="input-group">
            <input id="cl-pass" [type]="show() ? 'text' : 'password'" class="form-control" formControlName="password" autocomplete="current-password"
                   [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
            <button class="btn btn-outline-secondary" type="button" (click)="show.set(!show())" [attr.aria-label]="show() ? 'Hide password' : 'Show password'">
              <i class="bi" [class.bi-eye]="!show()" [class.bi-eye-slash]="show()"></i></button>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center mb-4">
          <div class="form-check">
            <input id="cl-remember" type="checkbox" class="form-check-input" formControlName="remember" />
            <label for="cl-remember" class="form-check-label small">Remember me</label>
          </div>
          <a routerLink="/college/forgot-password" class="small">Forgot password?</a>
        </div>
        <button type="submit" class="btn btn-primary w-100" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Sign in
        </button>
      </form>
      <p class="text-center small mt-4 mb-0">New college? <a routerLink="/college/register" class="fw-semibold">Register your placement cell</a></p>
      <p class="text-center small mt-2 mb-0 text-body-secondary">Student? <a routerLink="/student/login">Student portal</a> · Recruiter? <a routerLink="/login">Sign in here</a></p>
    </pm-auth-shell>`,
})
export class CollegeLogin {
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
        const home = AuthService.homeFor(s.role, s.collegeApprovalStatus);
        const target = this.returnUrl() && this.returnUrl()!.startsWith('/') && home === '/college/dashboard' && this.returnUrl()!.startsWith('/college')
          ? this.returnUrl()! : home;
        this.router.navigateByUrl(target);
      },
      error: (e: AppError) => {
        this.busy.set(false);
        this.unverified.set(e.code === 'EMAIL_NOT_VERIFIED');
        this.error.set(e.message);
      },
    });
  }
}
