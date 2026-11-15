import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { AppError } from '../../../core/api';
import { AuthShell } from './auth-shell';

@Component({
  selector: 'pm-verify-email',
  imports: [FormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Verify your e-mail">
      @switch (state()) {
        @case ('verifying') { <div class="text-center py-4"><span class="spinner-border"></span><p class="mt-2">Verifying…</p></div> }
        @case ('ok') {
          <div class="alert alert-success"><i class="bi bi-check-circle me-1"></i> E-mail verified. You can now sign in.</div>
          <a routerLink="/college/login" class="btn btn-primary w-100">Sign in</a>
        }
        @default {
          @if (error()) { <div class="alert alert-danger small" role="alert">{{ error() }}</div> }
          <p class="small text-body-secondary">Didn't get the e-mail or the link expired? Request a new one.</p>
          <form (ngSubmit)="resend()" class="d-flex gap-2">
            <label for="ve-email" class="visually-hidden">E-mail</label>
            <input id="ve-email" type="email" name="email" class="form-control" placeholder="you@college.edu.in" [(ngModel)]="email" required />
            <button class="btn btn-primary text-nowrap" [disabled]="!email">Resend link</button>
          </form>
          @if (sent()) { <div class="alert alert-info small mt-3">If the account exists and is unverified, a new link was sent.
            @if (devToken()) { <a [routerLink]="[]" [queryParams]="{ token: devToken() }" (click)="verify(devToken()!)" class="alert-link">Dev: verify now</a> }</div> }
        }
      }
    </pm-auth-shell>`,
})
export class VerifyEmail implements OnInit {
  private auth = inject(AuthService);
  token = input<string>();
  emailParam = input<string>(undefined, { alias: 'email' });
  state = signal<'idle' | 'verifying' | 'ok'>('idle');
  error = signal('');
  sent = signal(false);
  devToken = signal<string | null>(null);
  email = '';

  ngOnInit(): void {
    this.email = this.emailParam() ?? '';
    if (this.token()) this.verify(this.token()!);
  }

  verify(token: string): void {
    this.state.set('verifying');
    this.auth.verifyEmail(token).subscribe({
      next: () => this.state.set('ok'),
      error: (e: AppError) => { this.state.set('idle'); this.error.set(e.message); },
    });
  }

  resend(): void {
    this.auth.resendVerification(this.email.trim()).subscribe(r => { this.sent.set(true); this.devToken.set(r.data?.devToken ?? null); });
  }
}
