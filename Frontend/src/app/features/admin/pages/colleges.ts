import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminService, CollegeDetail, ROLE_LABELS } from '../services/admin.service';
import { ApprovalStatus, CollegeSummary } from '../../../core/models/college.models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState } from '../../../shared/ui/states';
import { Modal } from '../../../shared/ui/modal';

type Filter = ApprovalStatus | 'ALL';

/** Colleges: approve or reject registrations, suspend or reactivate approved colleges, and see each college's numbers and team. */
@Component({
  selector: 'pm-admin-colleges',
  imports: [DatePipe, DecimalPipe, RouterLink, StatusBadge, EmptyState, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <ul class="nav nav-pills small" role="tablist">
        @for (f of filters; track f.key) {
          <li class="nav-item"><button class="nav-link py-1 px-3" [class.active]="filter() === f.key" (click)="setFilter(f.key)">{{ f.label }}</button></li>
        }
      </ul>
      <input type="search" class="form-control form-control-sm ms-auto" style="max-width: 280px" placeholder="Search name, city, TPO or e-mail"
        aria-label="Search colleges" [value]="q()" (input)="q.set($any($event.target).value)" />
    </div>
    <div class="table-responsive bg-body border rounded-3">
      <table class="table table-hover align-middle small mb-0">
        <thead><tr><th>College</th><th class="d-none d-md-table-cell">TPO</th><th class="d-none d-lg-table-cell">Registered</th><th>Status</th></tr></thead>
        <tbody>
          @for (c of shown(); track c.collegeId) {
            <tr class="ac-row" tabindex="0" (click)="open(c.collegeId)" (keydown.enter)="open(c.collegeId)">
              <td><strong>{{ c.collegeName }}</strong><br><span class="text-body-secondary">{{ c.aicteCode }} · {{ c.city }}{{ c.state ? ', ' + c.state : '' }}</span></td>
              <td class="d-none d-md-table-cell">{{ c.tpoName }}<br><span class="text-body-secondary">{{ c.tpoEmail }}</span></td>
              <td class="d-none d-lg-table-cell">{{ c.createdAt | date: 'd MMM y' }}</td>
              <td><pm-status [value]="c.approvalStatus" /></td>
            </tr>
          } @empty { <tr><td colspan="4"><pm-empty-state icon="bi-building" title="No colleges in this view" /></td></tr> }
        </tbody>
      </table>
    </div>

    <pm-modal [open]="!!detail()" [title]="detail()?.college?.collegeName ?? ''" variant="drawer" size="lg" (closed)="detail.set(null)">
      @if (detail(); as d) {
        @let c = d.college;
        <div class="d-flex align-items-center gap-2 mb-2"><pm-status [value]="c.approvalStatus" />
          @if (c.approvalNote) { <small class="text-body-secondary">{{ c.approvalNote }}</small> }</div>
        <dl class="ac-dl mb-3">
          <dt>AICTE code</dt><dd>{{ c.aicteCode }}</dd>
          <dt>University</dt><dd>{{ c.university || '-' }}</dd>
          <dt>Location</dt><dd>{{ c.city }}{{ c.state ? ', ' + c.state : '' }}</dd>
          <dt>E-mail domain</dt><dd>&#64;{{ c.emailDomain }}</dd>
          <dt>TPO</dt><dd>{{ c.tpoName }} · {{ c.tpoEmail }} · {{ c.tpoPhone }}</dd>
          <dt>Registered</dt><dd>{{ c.createdAt | date: 'd MMM y, h:mm a' }}</dd>
        </dl>
        <div class="ac-nums mb-3">
          <div><b>{{ d.students | number }}</b><span>Students</span></div>
          <div><b>{{ d.placedStudents | number }}</b><span>Placed</span></div>
          <div><b>{{ d.drives | number }}</b><span>Drives</span></div>
          <div><b>{{ d.offers | number }}</b><span>Offers</span></div>
          <div><b>{{ d.mockAttempts | number }}</b><span>Mock tests</span></div>
          <div><b>{{ d.interviews | number }}</b><span>AI interviews</span></div>
        </div>
        <h3 class="h6 fw-bold">Placement team</h3>
        <ul class="list-unstyled small mb-3">
          @for (m of d.team; track m.userId) {
            <li class="d-flex gap-2 py-1 border-bottom"><span class="flex-grow-1">{{ m.fullName }} <span class="text-body-secondary">· {{ m.email }}</span></span>
              <span class="badge text-bg-light border">{{ roleLabel(m.role) }}</span>
              @if (m.status !== 'ACTIVE') { <span class="badge text-bg-secondary">{{ m.status }}</span> }
              @if (m.blocked) { <span class="badge text-bg-danger">Blocked</span> }</li>
          } @empty { <li class="text-body-secondary">No team members.</li> }
        </ul>
        <div class="d-flex flex-wrap gap-2">
          @if (c.approvalStatus === 'PENDING' || c.approvalStatus === 'REJECTED') { <button class="btn btn-success btn-sm" (click)="decide(c, 'APPROVED')">Approve</button> }
          @if (c.approvalStatus === 'PENDING') { <button class="btn btn-outline-danger btn-sm" (click)="decide(c, 'REJECTED')">Reject</button> }
          @if (c.approvalStatus === 'APPROVED') { <button class="btn btn-outline-danger btn-sm" (click)="decide(c, 'SUSPENDED')"><i class="bi bi-pause-circle me-1"></i>Suspend</button> }
          @if (c.approvalStatus === 'SUSPENDED') { <button class="btn btn-success btn-sm" (click)="decide(c, 'APPROVED')"><i class="bi bi-play-circle me-1"></i>Reactivate</button> }
          <a class="btn btn-outline-secondary btn-sm" routerLink="/admin/broadcast" [queryParams]="{ audience: 'COLLEGE', collegeId: c.collegeId }"><i class="bi bi-megaphone me-1"></i>Message this college</a>
          <a class="btn btn-outline-secondary btn-sm" routerLink="/admin/users" [queryParams]="{ q: c.tpoEmail }"><i class="bi bi-person me-1"></i>TPO account</a>
        </div>
      }
    </pm-modal>`,
  styles: [`
    .ac-row { cursor: pointer; } .ac-row:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
    .ac-dl { display: grid; grid-template-columns: max-content 1fr; gap: .3rem 1rem; font-size: .875rem; }
    .ac-dl dt { font-weight: 400; color: var(--bs-secondary-color); } .ac-dl dd { margin: 0; overflow-wrap: anywhere; }
    .ac-nums { display: grid; grid-template-columns: repeat(3, 1fr); gap: .5rem; }
    .ac-nums div { display: grid; padding: .6rem .75rem; border-radius: 10px; background: var(--bs-tertiary-bg); }
    .ac-nums b { font-size: 1.2rem; font-variant-numeric: tabular-nums; } .ac-nums span { font-size: .75rem; color: var(--bs-secondary-color); }
  `],
})
export class AdminCollegesPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  /** ?open=<collegeId> opens that college (links from the dashboard). */
  openId = input<string>(undefined, { alias: 'open' });
  readonly filters: { key: Filter; label: string }[] = [
    { key: 'PENDING', label: 'Pending' }, { key: 'APPROVED', label: 'Approved' }, { key: 'SUSPENDED', label: 'Suspended' },
    { key: 'REJECTED', label: 'Rejected' }, { key: 'ALL', label: 'All' },
  ];
  rows = signal<CollegeSummary[]>([]);
  filter = signal<Filter>('PENDING');
  q = signal('');
  detail = signal<CollegeDetail | null>(null);
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    return q ? this.rows().filter(c => [c.collegeName, c.city, c.state, c.tpoName, c.tpoEmail, c.aicteCode].join(' ').toLowerCase().includes(q)) : this.rows();
  });

  ngOnInit(): void {
    if (this.openId()) { this.filter.set('ALL'); this.open(this.openId()!); }
    this.load();
  }
  setFilter(f: Filter): void { this.filter.set(f); this.load(); }
  load(): void { this.svc.colleges(this.filter() === 'ALL' ? null : this.filter() as ApprovalStatus).subscribe(r => this.rows.set(r)); }
  open(id: string): void { this.svc.college(id).subscribe(d => this.detail.set(d)); }
  roleLabel(r: string): string { return ROLE_LABELS[r] ?? r; }

  async decide(c: CollegeSummary, s: ApprovalStatus): Promise<void> {
    const verb = s === 'SUSPENDED' ? 'Suspend' : s === 'REJECTED' ? 'Reject' : c.approvalStatus === 'SUSPENDED' ? 'Reactivate' : 'Approve';
    const message = s === 'SUSPENDED' ? 'The placement team cannot use the portal until you reactivate it. The TPO is told by e-mail.'
      : 'The TPO is told by e-mail.';
    const r = await this.confirm.ask({ title: `${verb} ${c.collegeName}?`, message, confirmText: verb,
      inputLabel: s === 'APPROVED' ? 'Note (optional)' : 'Reason', inputRequired: s !== 'APPROVED', tone: s === 'APPROVED' ? 'primary' : 'danger' });
    if (!r.ok) return;
    this.svc.decide(c.collegeId, s, r.value || undefined).subscribe(() => {
      const done: Record<string, string> = { Approve: 'approved', Reject: 'rejected', Suspend: 'suspended', Reactivate: 'reactivated' };
      this.toast.success(`${c.collegeName} ${done[verb]}`);
      this.load();
      this.open(c.collegeId);
    });
  }
}
