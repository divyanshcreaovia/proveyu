import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DriveRoomService, RecruiterInviteInfo } from './services/drive-room.service';
import { AppError } from '../../core/api';
import { AuthShell } from '../college/auth/auth-shell';

/** Opens a Drive Room invitation: new e-mails set a password, existing recruiters confirm with theirs. */
@Component({
  selector: 'pm-company-accept',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell variant="company" heading="Join the Drive Room" [sub]="info() ? info()!.companyName + ' · ' + info()!.collegeName : ''">
      @if (loadError()) {
        <div class="alert alert-danger small" role="alert">{{ loadError() }}</div>
        <a routerLink="/company/login" class="btn btn-outline-secondary w-100">Go to sign in</a>
      } @else if (!info()) { <div class="text-center py-4"><span class="spinner-border"></span></div> } @else {
        <div class="alert alert-light border small">You are invited to <strong>{{ info()!.driveTitle }}</strong> as <strong>{{ info()!.email }}</strong>.</div>
        @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (!info()!.existingAccount) {
            <label for="ra-name" class="form-label small fw-semibold">Your name</label>
            <input id="ra-name" class="form-control mb-3" formControlName="fullName" autocomplete="name" />
            <label for="ra-pass" class="form-label small fw-semibold">Create a password</label>
            <input id="ra-pass" type="password" class="form-control" formControlName="password" autocomplete="new-password"
                   [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
            <div class="form-text mb-4">At least 8 characters.</div>
          } @else {
            <p class="small text-body-secondary">You already have a ProveYu recruiter account. Enter its password to add this drive.</p>
            <label for="ra-pass2" class="form-label small fw-semibold">Password</label>
            <input id="ra-pass2" type="password" class="form-control mb-4" formControlName="password" autocomplete="current-password" />
          }
          <button class="btn btn-primary w-100" [disabled]="busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Open Drive Room</button>
        </form>
      }
    </pm-auth-shell>`,
})
export class CompanyAccept implements OnInit {
  private room = inject(DriveRoomService);
  private router = inject(Router);
  token = input<string>();
  info = signal<RecruiterInviteInfo | null>(null);
  loadError = signal('');
  error = signal('');
  busy = signal(false);
  form = inject(FormBuilder).nonNullable.group({ fullName: [''], password: ['', [Validators.required, Validators.minLength(8)]] });

  ngOnInit(): void {
    if (!this.token()) { this.loadError.set('This link is incomplete. Open the invitation e-mail again.'); return; }
    this.room.inviteInfo(this.token()!).subscribe({
      next: i => { this.info.set(i); this.form.patchValue({ fullName: i.fullName }); if (i.existingAccount) this.form.controls.password.setValidators(Validators.required); },
      error: (e: AppError) => this.loadError.set(e.message),
    });
  }
  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.room.accept(this.token()!, v.password, v.fullName.trim() || undefined).subscribe({
      next: () => this.router.navigateByUrl('/company/drives'),
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
