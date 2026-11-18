import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminService, Page, ROLE_LABELS, UserRow } from '../services/admin.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { EmptyState } from '../../../shared/ui/states';

/** Every account on the platform: search, block / unblock, password reset, and free AI interviews for a student. */
@Component({
  selector: 'pm-admin-users',
  imports: [DatePipe, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="d-flex flex-wrap gap-2 mb-3" (submit)="$event.preventDefault(); search(0)">
      <input type="search" class="form-control form-control-sm" style="max-width: 320px" placeholder="Name, e-mail, phone or roll number"
        aria-label="Search users" [value]="q()" (input)="q.set($any($event.target).value)" />
      <select class="form-select form-select-sm w-auto" aria-label="Role" [value]="role()" (change)="role.set($any($event.target).value); search(0)">
        <option value="">All roles</option>
        @for (r of roleOptions; track r.key) { <option [value]="r.key">{{ r.label }}</option> }
      </select>
      <select class="form-select form-select-sm w-auto" aria-label="Status" [value]="status()" (change)="status.set($any($event.target).value); search(0)">
        <option value="">Active and blocked</option><option value="active">Active</option><option value="blocked">Blocked</option>
      </select>
      <button class="btn btn-sm btn-primary"><i class="bi bi-search me-1"></i>Search</button>
      @if (data(); as p) { <span class="small text-body-secondary ms-auto align-self-center">{{ p.total }} user{{ p.total === 1 ? '' : 's' }}</span> }
    </form>

    <div class="table-responsive bg-body border rounded-3">
      <table class="table align-middle small mb-0">
        <thead><tr><th>User</th><th>Role</th><th class="d-none d-md-table-cell">College</th><th class="d-none d-lg-table-cell">Joined</th><th>Status</th><th></th></tr></thead>
        <tbody>
          @for (u of data()?.rows ?? []; track u.id) {
            <tr>
              <td><strong>{{ u.fullName }}</strong><br><span class="text-body-secondary">{{ u.email }}{{ u.phone ? ' · ' + u.phone : '' }}</span></td>
              <td>{{ roleLabel(u.role) }}@if (u.detail && u.role === 'CANDIDATE') { <br><span class="text-body-secondary">{{ u.detail }}</span> }</td>
              <td class="d-none d-md-table-cell">{{ u.collegeName || '-' }}</td>
              <td class="d-none d-lg-table-cell">{{ u.createdAt | date: 'd MMM y' }}</td>
              <td>@if (u.active) { <span class="badge text-bg-success">Active</span> } @else { <span class="badge text-bg-danger">Blocked</span> }
                @if (!u.verified) { <span class="badge text-bg-light border ms-1" title="E-mail not verified yet">Unverified</span> }</td>
              <td class="text-end text-nowrap">
                <div class="dropdown-center position-relative d-inline-block">
                  <button class="btn btn-sm btn-outline-secondary" (click)="menu.set(menu() === u.id ? null : u.id)" [attr.aria-expanded]="menu() === u.id"
                    [attr.aria-label]="'Actions for ' + u.fullName"><i class="bi bi-three-dots"></i></button>
                  @if (menu() === u.id) {
                    <div class="au-menu shadow" role="menu">
                      @if (u.active) { <button role="menuitem" class="text-danger" (click)="toggle(u)"><i class="bi bi-person-x"></i>Block</button> }
                      @else { <button role="menuitem" (click)="toggle(u)"><i class="bi bi-person-check"></i>Unblock</button> }
                      <button role="menuitem" (click)="reset(u)"><i class="bi bi-key"></i>Send password reset</button>
                      @if (u.role === 'CANDIDATE') { <button role="menuitem" (click)="credits(u)"><i class="bi bi-gift"></i>Give AI interviews</button> }
                    </div>
                  }
                </div>
              </td>
            </tr>
          } @empty { <tr><td colspan="6"><pm-empty-state icon="bi-people" title="No users found" text="Try another name, e-mail or role." /></td></tr> }
        </tbody>
      </table>
    </div>
    @if (data(); as p) {
      @if (p.total > p.size) {
        <nav class="d-flex justify-content-between align-items-center mt-2 small" aria-label="Pages">
          <button class="btn btn-sm btn-outline-secondary" [disabled]="p.page === 0" (click)="search(p.page - 1)">Previous</button>
          <span>Page {{ p.page + 1 }} of {{ pages(p) }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="p.page + 1 >= pages(p)" (click)="search(p.page + 1)">Next</button>
        </nav>
      }
    }`,
  styles: [`
    .au-menu { position: absolute; right: 0; top: calc(100% + .25rem); z-index: 20; min-width: 220px; padding: .3rem; border-radius: 10px;
      background: var(--bs-body-bg); border: 1px solid var(--bs-border-color); text-align: left; }
    .au-menu button { all: unset; box-sizing: border-box; display: flex; gap: .6rem; align-items: center; width: 100%; padding: .5rem .65rem; border-radius: 8px; cursor: pointer; }
    .au-menu button:hover, .au-menu button:focus-visible { background: var(--bs-tertiary-bg); }
  `],
})
export class AdminUsersPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  /** ?q= prefills the search (link from a college's TPO). */
  query = input<string>(undefined, { alias: 'q' });
  readonly roleOptions = Object.entries(ROLE_LABELS).filter(([k]) => k !== 'ORGANIZATION').map(([key, label]) => ({ key, label }));
  q = signal('');
  role = signal('');
  status = signal('');
  data = signal<Page<UserRow> | null>(null);
  menu = signal<string | null>(null);

  ngOnInit(): void {
    if (this.query()) this.q.set(this.query()!);
    this.search(0);
  }
  roleLabel(r: string): string { return ROLE_LABELS[r] ?? r; }
  pages(p: Page<UserRow>): number { return Math.max(1, Math.ceil(p.total / p.size)); }

  search(page: number): void {
    const active = this.status() === 'active' ? true : this.status() === 'blocked' ? false : null;
    this.svc.users(this.q().trim(), this.role(), active, page).subscribe(p => this.data.set(p));
  }

  async toggle(u: UserRow): Promise<void> {
    this.menu.set(null);
    const block = u.active;
    const r = await this.confirm.ask({
      title: `${block ? 'Block' : 'Unblock'} ${u.fullName}?`,
      message: block ? 'They are signed out at once and cannot sign in until you unblock them.' : 'They can sign in again.',
      confirmText: block ? 'Block' : 'Unblock', tone: block ? 'danger' : 'primary',
    });
    if (!r.ok) return;
    this.svc.setBlocked(u.id, block).subscribe(() => { this.toast.success(`${u.fullName} ${block ? 'blocked' : 'unblocked'}`); this.search(this.data()?.page ?? 0); });
  }

  async reset(u: UserRow): Promise<void> {
    this.menu.set(null);
    const r = await this.confirm.ask({ title: `Send a password reset to ${u.email}?`, message: 'They get a 6-digit code by e-mail to set a new password.', confirmText: 'Send' });
    if (!r.ok) return;
    this.svc.resetPassword(u.id).subscribe(x => this.toast.success(x.devOtp ? `Code sent (development code: ${x.devOtp})` : 'Password reset code sent'));
  }

  async credits(u: UserRow): Promise<void> {
    this.menu.set(null);
    const r = await this.confirm.ask({ title: `Give AI interviews to ${u.fullName}`, message: 'Added to the student\'s bought interviews; they never expire.',
      inputLabel: 'How many (1 to 50)', inputRequired: true, confirmText: 'Give' });
    if (!r.ok) return;
    const n = Number(r.value);
    if (!Number.isInteger(n) || n < 1 || n > 50) { this.toast.error('Enter a whole number from 1 to 50'); return; }
    this.svc.grantCredits(u.email, n).subscribe(x => this.toast.success(`${x.studentName} now has ${x.paidBalance} interview(s)`));
  }
}
