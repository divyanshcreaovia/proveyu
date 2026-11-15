import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { AppError } from '../../../core/api';
import { InviteInfo } from '../../../core/models/college.models';
import { AuthShell } from './auth-shell';

@Component({
  selector: 'pm-accept-invite',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Join your placement team">
      @if (loadError()) {
        <div class="alert alert-danger" role="alert">{{ loadError() }}</div>
        <a routerLink="/college/login" class="btn btn-outline-primary w-100">Go to sign in</a>
      } @else if (info(); as i) {
        <p class="small">You were invited to <strong>{{ i.collegeName }}</strong> as
          <strong>{{ i.role.replace('COLLEGE_', '').toLowerCase() }}</strong> ({{ i.email }}).</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label for="ai-name" class="form-label small fw-semibold">Full name</label>
          <input id="ai-name" class="form-control mb-3" formControlName="fullName" />
          <label for="ai-phone" class="form-label small fw-semibold">Phone (optional)</label>
          <input id="ai-phone" class="form-control mb-3" formControlName="phone" inputmode="tel" />
          <label for="ai-pass" class="form-label small fw-semibold">Create password</label>
          <input id="ai-pass" type="password" class="form-control" formControlName="password" autocomplete="new-password"
                 [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
          <div class="form-text mb-3">At least 8 characters with letters and numbers.</div>
          <label for="ai-confirm" class="form-label small fw-semibold">Confirm password</label>
          <input id="ai-confirm" type="password" class="form-control mb-3" formControlName="confirm" autocomplete="new-password"
                 [class.is-invalid]="form.touched && form.errors?.['mismatch']" />
          @if (error()) { <div class="alert alert-danger small" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary w-100" [disabled]="busy()">Accept & continue</button>
        </form>
      } @else { <div class="text-center py-4"><span class="spinner-border"></span></div> }
    </pm-auth-shell>`,
})
export class AcceptInvite implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  token = input<string>();
  info = signal<InviteInfo | null>(null);
  loadError = signal('');
  error = signal('');
  busy = signal(false);
  form = inject(FormBuilder).nonNullable.group({
    fullName: ['', Validators.required],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
    confirm: ['', Validators.required],
  }, { validators: (g: AbstractControl) => g.get('password')?.value === g.get('confirm')?.value ? null : { mismatch: true } });

  ngOnInit(): void {
    if (!this.token()) { this.loadError.set('This invitation link is incomplete.'); return; }
    this.auth.inviteInfo(this.token()!).subscribe({
      next: i => { this.info.set(i); this.form.patchValue({ fullName: i.fullName }); },
      error: (e: AppError) => this.loadError.set(e.message),
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.auth.acceptInvite({ token: this.token()!, password: v.password, fullName: v.fullName, phone: v.phone || undefined }).subscribe({
      next: s => this.router.navigateByUrl(AuthService.homeFor(s.role, s.collegeApprovalStatus)),
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
