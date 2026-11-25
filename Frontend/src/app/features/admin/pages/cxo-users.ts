import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminService, CxoUserRow } from '../services/admin.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { AppError } from '../../../core/api';
import { CollegeSummary } from '../../../core/models/college.models';

/**
 * CXO (college leadership) accounts. There is no CXO sign-up: the admin creates an account for one approved college here and
 * shares the password; the CXO signs in at /cxo/login and sees only that college's data.
 */
@Component({
  selector: 'pm-admin-cxo-users',
  imports: [DatePipe, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-xl-8">
        <div class="d-flex flex-wrap gap-2 align-items-center mb-2">
          <input type="search" class="form-control form-control-sm" style="max-width: 280px" placeholder="Search name, e-mail or college"
            aria-label="Search CXO accounts" [value]="q()" (input)="q.set($any($event.target).value)" />
          <span class="small text-body-secondary ms-auto">{{ shown().length }} account{{ shown().length === 1 ? '' : 's' }}</span>
        </div>
        <div class="table-responsive bg-body border rounded-3">
          <table class="table align-middle small mb-0">
            <thead><tr><th>Leader</th><th>College</th><th class="d-none d-md-table-cell">Last sign-in</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (c of shown(); track c.memberId) {
                <tr>
                  <td><strong>{{ c.fullName }}</strong><br><span class="text-body-secondary">{{ c.email }}@if (c.designation) { · {{ c.designation }} }</span></td>
                  <td>{{ c.collegeName ?? '-' }}</td>
                  <td class="d-none d-md-table-cell">{{ c.lastLoginAt ? (c.lastLoginAt | date: 'd MMM, h:mm a') : 'Never' }}</td>
                  <td>@if (c.status === 'ACTIVE' && c.active) { <span class="badge text-bg-success">Active</span> } @else { <span class="badge text-bg-danger">Revoked</span> }</td>
                  <td class="text-end text-nowrap">
                    <button class="btn btn-sm btn-outline-secondary me-1" (click)="reset(c)" title="E-mail a password reset code"><i class="bi bi-key"></i></button>
                    @if (c.status === 'ACTIVE') { <button class="btn btn-sm btn-outline-danger" (click)="toggle(c, false)">Revoke</button> }
                    @else { <button class="btn btn-sm btn-outline-success" (click)="toggle(c, true)">Restore</button> }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="text-center text-body-secondary py-4">No CXO accounts yet. Create the first one on the right.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <p class="small text-body-secondary mt-2 mb-0"><i class="bi bi-info-circle me-1"></i>A CXO sees read-only AI dashboards for their own college at
          <strong>/cxo/login</strong>. Revoking signs them out at once.</p>
      </div>
      <div class="col-xl-4">
        <form class="bg-body border rounded-3 p-3" [formGroup]="form" (ngSubmit)="create()" novalidate>
          <h2 class="h6 fw-bold mb-1">Add a college leader</h2>
          <p class="small text-body-secondary">Principal, director or management of an approved college. Share the password safely; they can change it
            with "Forgot password".</p>
          @if (error()) { <div class="alert alert-danger small py-2" role="alert">{{ error() }}</div> }
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="cx-college">College</label>
            <select id="cx-college" class="form-select form-select-sm" formControlName="collegeId"
              [class.is-invalid]="form.controls.collegeId.touched && form.controls.collegeId.invalid">
              <option value="" disabled>Choose an approved college</option>
              @for (c of colleges(); track c.collegeId) { <option [value]="c.collegeId">{{ c.collegeName }}@if (c.city) { · {{ c.city }} }</option> }
            </select>
          </div>
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="cx-name">Full name</label>
            <input id="cx-name" class="form-control form-control-sm" formControlName="fullName" [class.is-invalid]="form.controls.fullName.touched && form.controls.fullName.invalid" />
          </div>
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="cx-desig">Designation</label>
            <input id="cx-desig" class="form-control form-control-sm" formControlName="designation" placeholder="Principal, Director, Chairman…" />
          </div>
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="cx-email">E-mail</label>
            <input id="cx-email" type="email" class="form-control form-control-sm" formControlName="email" autocomplete="off"
              [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
          </div>
          <div class="mb-3">
            <label class="form-label small fw-semibold" for="cx-pass">Password</label>
            <input id="cx-pass" type="password" class="form-control form-control-sm" formControlName="password" autocomplete="new-password"
              [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" aria-describedby="cx-pass-help" />
            <div id="cx-pass-help" class="form-text">At least 8 characters, with a letter and a number.</div>
          </div>
          <button class="btn btn-primary btn-sm w-100" [disabled]="busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } <i class="bi bi-person-plus me-1"></i>Create CXO account</button>
        </form>
      </div>
    </div>`,
})
export class AdminCxoUsersPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  rows = signal<CxoUserRow[]>([]);
  colleges = signal<CollegeSummary[]>([]);
  q = signal('');
  busy = signal(false);
  error = signal('');
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    return q ? this.rows().filter(r => [r.fullName, r.email, r.collegeName ?? '', r.designation ?? ''].some(v => v.toLowerCase().includes(q))) : this.rows();
  });
  form = inject(FormBuilder).nonNullable.group({
    collegeId: ['', Validators.required],
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    designation: ['', Validators.maxLength(150)],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  ngOnInit(): void {
    this.load();
    this.svc.colleges('APPROVED').subscribe(c => this.colleges.set(c));
  }

  load(): void { this.svc.cxoUsers().subscribe(r => this.rows.set(r)); }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.error.set('Choose a college and fill in a name, a valid e-mail and a password of 8+ characters with a letter and a number.'); return; }
    this.busy.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.svc.createCxo({ ...v, designation: v.designation.trim() || undefined }).subscribe({
      next: c => { this.busy.set(false); this.form.reset(); this.toast.success(`${c.fullName} can now sign in to the CXO portal`); this.load(); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  async toggle(c: CxoUserRow, active: boolean): Promise<void> {
    const r = await this.confirm.ask({
      title: `${active ? 'Restore' : 'Revoke'} ${c.fullName}?`, confirmText: active ? 'Restore' : 'Revoke', tone: active ? 'primary' : 'danger',
      message: active ? 'They can sign in to the CXO portal again.' : 'They are signed out at once and can no longer open the CXO portal.',
    });
    if (r.ok) this.svc.setCxoActive(c.memberId, active).subscribe(() => { this.toast.success(active ? 'Access restored' : 'Access revoked'); this.load(); });
  }

  reset(c: CxoUserRow): void {
    this.svc.resetPassword(c.userId).subscribe(r => this.toast.success(r.devOtp ? `Reset code (dev): ${r.devOtp}` : `A reset code was e-mailed to ${c.email}`));
  }
}
