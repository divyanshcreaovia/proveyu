import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { StudentPortalService } from '../../../services/student-portal.service';
import { ToastService } from '../../../services/toast.service';
import { AppError } from '../../../core/api';
import { AuthShell } from '../../college/auth/auth-shell';

const samePasswords = (g: AbstractControl): ValidationErrors | null =>
  g.get('password')?.value === g.get('confirm')?.value ? null : { mismatch: true };

/**
 * Student sign-up = account activation. The placement cell adds the student; the student proves the e-mail is theirs
 * with a one-time code, then sets a password and is signed in.
 */
@Component({
  selector: 'pm-student-signup',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell variant="student" heading="Create your student account"
      [sub]="step() === 0 ? 'Step 1 of 2 · Confirm who you are' : 'Step 2 of 2 · Verify your e-mail and set a password'">
      <div class="pm-su-steps mb-4" aria-hidden="true"><span class="active"></span><span [class.active]="step() === 1"></span></div>
      @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
      @if (step() === 0) {
        <form [formGroup]="idForm" (ngSubmit)="start()" novalidate>
          <div class="mb-3">
            <label for="su-email" class="form-label small fw-semibold">College e-mail</label>
            <input id="su-email" type="email" class="form-control" formControlName="email" autocomplete="email"
                   [class.is-invalid]="idForm.controls.email.touched && idForm.controls.email.invalid" />
            <div class="invalid-feedback">Enter a valid e-mail.</div>
          </div>
          <div class="mb-4">
            <label for="su-roll" class="form-label small fw-semibold">Roll / enrolment number</label>
            <input id="su-roll" class="form-control" formControlName="rollNumber" autocomplete="off"
                   [class.is-invalid]="idForm.controls.rollNumber.touched && idForm.controls.rollNumber.invalid" />
            <div class="form-text">Exactly as on your college ID card.</div>
          </div>
          <button class="btn btn-primary w-100" [disabled]="busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Send verification code</button>
        </form>
      } @else {
        <div class="alert alert-info small">{{ info() }}@if (devOtp()) { <br><strong>Dev mode code:</strong> {{ devOtp() }} }</div>
        <form [formGroup]="pwForm" (ngSubmit)="finish()" novalidate>
          <div class="mb-3">
            <label for="su-otp" class="form-label small fw-semibold">6-digit code</label>
            <input id="su-otp" class="form-control" formControlName="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                   [class.is-invalid]="pwForm.controls.otp.touched && pwForm.controls.otp.invalid" />
          </div>
          <div class="mb-3">
            <label for="su-pass" class="form-label small fw-semibold">Password</label>
            <input id="su-pass" type="password" class="form-control" formControlName="password" autocomplete="new-password"
                   [class.is-invalid]="pwForm.controls.password.touched && pwForm.controls.password.invalid" />
            <div class="form-text">At least 8 characters.</div>
          </div>
          <div class="mb-4">
            <label for="su-confirm" class="form-label small fw-semibold">Confirm password</label>
            <input id="su-confirm" type="password" class="form-control" formControlName="confirm" autocomplete="new-password"
                   [class.is-invalid]="pwForm.controls.confirm.touched && pwForm.hasError('mismatch')" />
            <div class="invalid-feedback">Passwords do not match.</div>
          </div>
          <button class="btn btn-primary w-100" [disabled]="busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Create account & sign in</button>
          <button type="button" class="btn btn-link btn-sm w-100 mt-2" (click)="start()">Resend code</button>
        </form>
      }
      <p class="text-center small mt-4 mb-0">Already activated? <a routerLink="/student/login" class="fw-semibold">Sign in</a></p>
    </pm-auth-shell>`,
  styles: [`
    .pm-su-steps { display: flex; gap: .4rem; } .pm-su-steps span { flex: 1; height: 4px; border-radius: 4px; background: var(--bs-border-color); }
    .pm-su-steps span.active { background: #2563eb; }
  `],
})
export class StudentSignup {
  private auth = inject(AuthService);
  private portal = inject(StudentPortalService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder).nonNullable;
  step = signal(0);
  busy = signal(false);
  error = signal('');
  info = signal('');
  devOtp = signal<string | null>(null);
  idForm = this.fb.group({ email: ['', [Validators.required, Validators.email]], rollNumber: ['', [Validators.required, Validators.maxLength(50)]] });
  pwForm = this.fb.group({
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required],
  }, { validators: samePasswords });

  start(): void {
    this.idForm.markAllAsTouched();
    if (this.idForm.invalid) return;
    const { email, rollNumber } = this.idForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.portal.signup(email.trim(), rollNumber.trim()).subscribe({
      next: r => { this.busy.set(false); this.info.set(r.message); this.devOtp.set(r.devOtp ?? null); this.step.set(1); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  finish(): void {
    this.pwForm.markAllAsTouched();
    if (this.pwForm.invalid) return;
    const email = this.idForm.getRawValue().email.trim();
    const { otp, password } = this.pwForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.auth.resetPassword(email, otp, password).pipe(switchMap(() => this.auth.login(email, password, true))).subscribe({
      next: () => { this.toast.success('Welcome! Your account is ready.'); this.router.navigateByUrl('/student/dashboard'); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
