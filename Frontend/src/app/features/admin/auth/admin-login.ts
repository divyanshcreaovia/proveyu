import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AppError } from '../../../core/api';
import { AuthShell } from '../../college/auth/auth-shell';

/** Platform admin sign-in. There is no admin sign-up: new admins are created by an admin inside the portal. */
@Component({
  selector: 'pm-admin-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Admin sign in" sub="For the ProveYu team. Admin accounts are created by another admin." variant="admin">
      @if (reason() === 'expired') { <div class="alert alert-warning small py-2">Your session expired. Please sign in again.</div> }
      @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="mb-3">
          <label for="al-email" class="form-label small fw-semibold">E-mail</label>
          <input id="al-email" type="email" class="form-control" formControlName="email" autocomplete="username"
                 [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          <div class="invalid-feedback">Enter a valid e-mail.</div>
        </div>
        <div class="mb-4">
          <label for="al-pass" class="form-label small fw-semibold">Password</label>
          <div class="input-group">
            <input id="al-pass" [type]="show() ? 'text' : 'password'" class="form-control" formControlName="password" autocomplete="current-password"
                   [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
            <button class="btn btn-outline-secondary" type="button" (click)="show.set(!show())" [attr.aria-label]="show() ? 'Hide password' : 'Show password'">
              <i class="bi" [class.bi-eye]="!show()" [class.bi-eye-slash]="show()"></i></button>
          </div>
        </div>
        <button type="submit" class="btn btn-primary w-100" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Sign in
        </button>
      </form>
      <p class="text-center small mt-4 mb-0 text-body-secondary">Not on the ProveYu team? <a routerLink="/login">Choose your portal</a></p>
    </pm-auth-shell>`,
})
export class AdminLogin {
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
  });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password, true).subscribe({
      next: s => {
        if (s.role !== 'ADMIN') {
          // Students, colleges and companies have their own portals: do not keep their session here.
          this.auth.logout('/admin/login');
          this.busy.set(false);
          this.error.set('This sign-in is for the ProveYu team only. Use your own portal from the Sign in page.');
          return;
        }
        const r = this.returnUrl();
        this.router.navigateByUrl(r && r.startsWith('/admin/') ? r : '/admin/dashboard');
      },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
