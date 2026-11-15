import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { AppError } from '../../../core/api';
import { AuthShell } from './auth-shell';

/** Two-step OTP reset. The OTP is only ever delivered by e-mail (or shown in dev mode when the backend dev flag is on). */
@Component({
  selector: 'pm-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Reset password" [sub]="step() === 0 ? 'We will e-mail you a 6-digit code' : 'Enter the code from your e-mail'">
      @if (error()) { <div class="alert alert-danger small" role="alert">{{ error() }}</div> }
      @if (step() === 0) {
        <form [formGroup]="emailForm" (ngSubmit)="send()" novalidate>
          <label for="fp-email" class="form-label small fw-semibold">E-mail</label>
          <input id="fp-email" type="email" class="form-control mb-3" formControlName="email" autocomplete="username" />
          <button class="btn btn-primary w-100" [disabled]="emailForm.invalid || busy()">Send code</button>
        </form>
      } @else {
        <div class="alert alert-info small">{{ info() }}@if (devOtp()) { <br><strong>Dev mode code:</strong> {{ devOtp() }} }</div>
        <form [formGroup]="resetForm" (ngSubmit)="reset()" novalidate>
          <label for="fp-otp" class="form-label small fw-semibold">6-digit code</label>
          <input id="fp-otp" class="form-control mb-3" formControlName="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
          <label for="fp-pass" class="form-label small fw-semibold">New password</label>
          <input id="fp-pass" type="password" class="form-control" formControlName="password" autocomplete="new-password" />
          <div class="form-text mb-3">At least 8 characters.</div>
          <button class="btn btn-primary w-100" [disabled]="resetForm.invalid || busy()">Update password</button>
          <button type="button" class="btn btn-link btn-sm w-100 mt-2" (click)="send()">Resend code</button>
        </form>
      }
      <p class="text-center small mt-3 mb-0"><a routerLink="/college/login">Back to sign in</a></p>
    </pm-auth-shell>`,
})
export class ForgotPassword {
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder).nonNullable;
  step = signal(0);
  busy = signal(false);
  error = signal('');
  info = signal('');
  devOtp = signal<string | null>(null);
  emailForm = this.fb.group({ email: ['', [Validators.required, Validators.email]] });
  resetForm = this.fb.group({
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  send(): void {
    this.busy.set(true);
    this.error.set('');
    this.auth.forgotPassword(this.emailForm.getRawValue().email.trim()).subscribe({
      next: r => { this.busy.set(false); this.info.set(r.message); this.devOtp.set(r.devOtp ?? null); this.step.set(1); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  reset(): void {
    const { otp, password } = this.resetForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.auth.resetPassword(this.emailForm.getRawValue().email.trim(), otp, password).subscribe({
      next: () => { this.toast.success('Password updated. Please sign in.'); this.router.navigateByUrl('/college/login'); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
