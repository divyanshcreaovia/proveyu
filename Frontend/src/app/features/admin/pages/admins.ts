import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminRow, AdminService } from '../services/admin.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { AppError } from '../../../core/api';

/** Platform admins. There is no admin sign-up: an admin creates the next one here. */
@Component({
  selector: 'pm-admin-admins',
  imports: [DatePipe, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-lg-7">
        <div class="table-responsive bg-body border rounded-3">
          <table class="table align-middle small mb-0">
            <thead><tr><th>Admin</th><th class="d-none d-sm-table-cell">Since</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (a of rows(); track a.id) {
                <tr>
                  <td><strong>{{ a.fullName }}</strong>@if (a.id === me) { <span class="badge text-bg-light border ms-1">You</span> }<br>
                    <span class="text-body-secondary">{{ a.email }}</span></td>
                  <td class="d-none d-sm-table-cell">{{ a.createdAt | date: 'd MMM y' }}</td>
                  <td>@if (a.active) { <span class="badge text-bg-success">Active</span> } @else { <span class="badge text-bg-danger">Blocked</span> }</td>
                  <td class="text-end">
                    @if (a.id !== me) {
                      <button class="btn btn-sm" [class.btn-outline-danger]="a.active" [class.btn-outline-success]="!a.active" (click)="toggle(a)">
                        {{ a.active ? 'Block' : 'Unblock' }}</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="small text-body-secondary mt-2 mb-0"><i class="bi bi-info-circle me-1"></i>At least one admin always stays active, and you cannot block yourself.</p>
      </div>
      <div class="col-lg-5">
        <form class="bg-body border rounded-3 p-3" [formGroup]="form" (ngSubmit)="create()" novalidate>
          <h2 class="h6 fw-bold mb-1">Add a platform admin</h2>
          <p class="small text-body-secondary">They get full access to this portal. Share the password with them safely; they can change it with "Forgot password".</p>
          @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="na-name">Full name</label>
            <input id="na-name" class="form-control form-control-sm" formControlName="fullName" [class.is-invalid]="form.controls.fullName.touched && form.controls.fullName.invalid" />
          </div>
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="na-email">E-mail</label>
            <input id="na-email" type="email" class="form-control form-control-sm" formControlName="email" autocomplete="off"
              [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          </div>
          <div class="mb-3">
            <label class="form-label small fw-semibold" for="na-pass">Password</label>
            <input id="na-pass" type="password" class="form-control form-control-sm" formControlName="password" autocomplete="new-password"
              [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" aria-describedby="na-pass-help" />
            <div id="na-pass-help" class="form-text">At least 8 characters, with a letter and a number.</div>
          </div>
          <button class="btn btn-primary btn-sm w-100" [disabled]="busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } <i class="bi bi-person-plus me-1"></i>Create admin</button>
        </form>
      </div>
    </div>`,
})
export class AdminAdminsPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly me = inject(TokenStorageService).get('userId');
  rows = signal<AdminRow[]>([]);
  busy = signal(false);
  error = signal('');
  form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  ngOnInit(): void { this.load(); }
  load(): void { this.svc.admins().subscribe(r => this.rows.set(r)); }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.error.set('Fill in a name, a valid e-mail and a password of 8+ characters with a letter and a number.'); return; }
    this.busy.set(true);
    this.error.set('');
    this.svc.createAdmin(this.form.getRawValue()).subscribe({
      next: a => { this.busy.set(false); this.form.reset(); this.toast.success(`${a.fullName} is now a platform admin`); this.load(); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  async toggle(a: AdminRow): Promise<void> {
    const r = await this.confirm.ask({ title: `${a.active ? 'Block' : 'Unblock'} ${a.fullName}?`, confirmText: a.active ? 'Block' : 'Unblock',
      message: a.active ? 'They are signed out at once and lose access to the admin portal.' : 'They get access to the admin portal again.',
      tone: a.active ? 'danger' : 'primary' });
    if (r.ok) this.svc.setBlocked(a.id, a.active).subscribe(() => { this.toast.success('Done'); this.load(); });
  }
}
