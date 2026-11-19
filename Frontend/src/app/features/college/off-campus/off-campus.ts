import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { ToastService } from '../../../core/services/toast.service';
import { CollegeService } from '../services/college.service';
import {
  APP_STATUSES, AppStatus, CollegeNoc, CollegeView, NocStatus, OffCampusService, appStatusLabel, jobTypeLabel,
} from '../../offcampus/off-campus.service';

type Tab = 'applications' | 'nocs';

/**
 * The placement cell's view of its students' off-campus activity (jobs posted or imported by ProveYu): applications and offers the
 * students report, and their NOC requests, which the TPO or a coordinator approves (NOC generated or signed copy uploaded) or rejects.
 */
@Component({
  selector: 'pm-college-off-campus',
  imports: [DatePipe, FormsModule, RouterLink, EmptyState, Skeleton, Modal, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      @if (d.policy !== 'ALLOW') {
        <div class="alert alert-secondary small d-flex align-items-center gap-2 py-2">
          <i class="bi bi-info-circle"></i>
          <span class="flex-grow-1">{{ d.policy === 'DISABLED' ? 'Off-campus jobs are turned off for your students.' : 'Placed students cannot apply to off-campus jobs.' }}</span>
          @if (isAdmin()) { <a routerLink="/college/settings" class="small">Change in Settings</a> }
        </div>
      }
      <div class="row g-3 mb-3">
        <div class="col-6 col-md-3"><div class="oc-kpi"><div class="oc-n">{{ d.students }}</div><div class="oc-l">Students who applied</div></div></div>
        <div class="col-6 col-md-3"><div class="oc-kpi"><div class="oc-n">{{ d.applied }}</div><div class="oc-l">Applications</div></div></div>
        <div class="col-6 col-md-3"><div class="oc-kpi oc-kpi-ok"><div class="oc-n">{{ d.offers }}</div><div class="oc-l">Off-campus offers</div></div></div>
        <div class="col-6 col-md-3"><button type="button" class="oc-kpi oc-kpi-btn w-100 text-start" [class.oc-kpi-warn]="d.pendingNocs" (click)="setTab('nocs')">
          <div class="oc-n">{{ d.pendingNocs }}</div><div class="oc-l">NOC requests waiting</div></button></div>
      </div>
    }

    <ul class="nav nav-tabs small mb-3" role="tablist">
      <li class="nav-item"><button class="nav-link" [class.active]="tab() === 'applications'" (click)="setTab('applications')">Applications & offers</button></li>
      <li class="nav-item"><button class="nav-link" [class.active]="tab() === 'nocs'" (click)="setTab('nocs')">NOC requests
        @if (data()?.pendingNocs) { <span class="badge rounded-pill text-bg-danger ms-1">{{ data()!.pendingNocs }}</span> }</button></li>
    </ul>

    @if (tab() === 'applications') {
      <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
        <select class="form-select form-select-sm" style="max-width: 200px" aria-label="Filter by status" [ngModel]="status()" (ngModelChange)="status.set($event); load()">
          <option [ngValue]="null">All statuses</option>
          @for (s of statuses; track s.v) { <option [ngValue]="s.v">{{ s.l }}</option> }
        </select>
        <input type="search" class="form-control form-control-sm" style="max-width: 280px" placeholder="Search student, roll no, company"
          aria-label="Search off-campus applications" [ngModel]="q()" (ngModelChange)="search($event)" />
        <button class="btn btn-sm btn-outline-secondary ms-auto" (click)="export()" [disabled]="exporting()"><i class="bi bi-download me-1"></i>Export</button>
      </div>

      @if (!data()) { <pm-skeleton [rows]="6" /> } @else {
        <div class="table-responsive bg-body border rounded-3">
          <table class="table table-hover align-middle small mb-0">
            <thead><tr><th>Student</th><th>Company & role</th><th>Status</th><th class="d-none d-md-table-cell">Applied</th><th class="d-none d-lg-table-cell">Updated</th></tr></thead>
            <tbody>
              @for (r of data()!.rows; track r.id) {
                <tr>
                  <td><strong>{{ r.studentName }}</strong><br><span class="text-body-secondary">{{ r.rollNumber }} · {{ r.branch ?? '-' }} · {{ r.batchYear }}</span></td>
                  <td>{{ r.companyName }}<br><span class="text-body-secondary">{{ r.roleTitle }} · {{ type(r.jobType) }}</span></td>
                  <td><span class="oc-st oc-{{ r.status.toLowerCase() }}">{{ label(r.status) }}</span>
                    @if (r.offerCtcLpa) { <span class="text-success fw-semibold ms-1">{{ r.offerCtcLpa }} LPA</span> }
                    @if (r.note) { <br><span class="text-body-secondary">{{ r.note }}</span> }</td>
                  <td class="d-none d-md-table-cell text-nowrap">{{ r.appliedAt ? (r.appliedAt | date: 'd MMM y') : '-' }}</td>
                  <td class="d-none d-lg-table-cell text-nowrap">{{ r.updatedAt | date: 'd MMM, h:mm a' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5"><pm-empty-state icon="bi-globe2" title="No off-campus activity yet"
                  text="When your students apply to off-campus jobs posted by ProveYu and update their status, it shows up here." /></td></tr>
              }
            </tbody>
          </table>
        </div>
        <p class="small text-body-secondary mt-2 mb-0"><i class="bi bi-info-circle"></i> Students report these statuses themselves. Mark a student as placed
          from Students after you verify the offer letter.</p>
      }
    } @else {
      <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
        <ul class="nav nav-pills small">
          @for (f of nocFilters; track f.label) {
            <li class="nav-item"><button class="nav-link py-1 px-3" [class.active]="nocStatus() === f.key" (click)="nocStatus.set(f.key); loadNocs()">{{ f.label }}</button></li>
          }
        </ul>
      </div>
      @if (!nocs()) { <pm-skeleton [rows]="5" /> } @else {
        <div class="table-responsive bg-body border rounded-3">
          <table class="table align-middle small mb-0">
            <thead><tr><th>Student</th><th>Offer</th><th>Offer letter</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (n of nocs()!; track n.id) {
                <tr>
                  <td><strong>{{ n.studentName }}</strong><br><span class="text-body-secondary">{{ n.rollNumber }} · {{ n.branch ?? '-' }} · {{ n.batchYear }}</span></td>
                  <td>{{ n.companyName }}<br><span class="text-body-secondary">{{ n.roleTitle }} · {{ type(n.jobType) }}{{ n.offerCtcLpa ? ' · ' + n.offerCtcLpa + ' LPA' : '' }}</span>
                    @if (n.studentNote) { <br><span class="text-body-secondary fst-italic">"{{ n.studentNote }}"</span> }</td>
                  <td><button class="btn btn-link btn-sm p-0" (click)="svc.openCollegeNocFile(n.id, 'offer')"><i class="bi bi-file-earmark-text me-1"></i>View</button></td>
                  <td><pm-status [value]="n.status" /><br><span class="text-body-secondary">{{ n.createdAt | date: 'd MMM' }}
                    @if (n.decidedByName) { · {{ n.decidedByName }} }</span>
                    @if (n.decisionNote) { <br><span class="text-body-secondary">{{ n.decisionNote }}</span> }
                    @if (n.placementRecorded) { <br><span class="text-success small"><i class="bi bi-award"></i> Placement recorded</span> }</td>
                  <td class="text-end text-nowrap">
                    @if (n.status === 'PENDING' && canWrite()) {
                      <button class="btn btn-sm btn-success me-1" (click)="openDecision(n, 'APPROVED')">Approve</button>
                      <button class="btn btn-sm btn-outline-danger" (click)="openDecision(n, 'REJECTED')">Reject</button>
                    }
                    @if (n.hasNoc) { <button class="btn btn-sm btn-outline-secondary" (click)="svc.openCollegeNocFile(n.id, 'noc')"><i class="bi bi-file-earmark-check me-1"></i>NOC</button> }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="5"><pm-empty-state icon="bi-file-earmark-check" title="No NOC requests"
                  text="Students with an off-campus offer can attach the offer letter and ask you for a NOC. Requests appear here." /></td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    }

    <pm-modal [open]="!!deciding()" [title]="(decision() === 'APPROVED' ? 'Issue NOC · ' : 'Reject NOC request · ') + (deciding()?.studentName ?? '')" (closed)="deciding.set(null)">
      @if (deciding(); as n) {
        <p class="small mb-2">{{ n.companyName }} · {{ n.roleTitle }}{{ n.offerCtcLpa ? ' · ' + n.offerCtcLpa + ' LPA' : '' }}
          · <button class="btn btn-link btn-sm p-0 align-baseline" (click)="svc.openCollegeNocFile(n.id, 'offer')">view offer letter</button></p>
        @if (decision() === 'APPROVED') {
          @if (n.jobType !== 'INTERNSHIP') {
            <div class="alert alert-success small py-2 mb-2"><i class="bi bi-award me-1"></i>Approving records this as an <strong>accepted off-campus offer</strong>:
              {{ n.studentName }} becomes <strong>Placed</strong>, and the offer counts in your dashboard, reports and placement policy.</div>
            <label class="form-label small fw-semibold" for="noc-ctc">CTC from the offer letter (LPA)</label>
            <input id="noc-ctc" type="number" min="0" max="500" step="0.01" class="form-control form-control-sm mb-3" [ngModel]="ctc()" (ngModelChange)="ctc.set($event)" />
          } @else {
            <div class="alert alert-secondary small py-2 mb-2">An internship without a pre-placement offer does not change the student's placement status.</div>
          }
          <p class="small text-body-secondary">ProveYu creates the NOC on your college letterhead (name, logo, TPO name) and the student downloads it.
            If you prefer your own signed letter, upload it instead.</p>
          <label class="form-label small fw-semibold" for="noc-signed">Signed NOC (PDF, optional)</label>
          <input id="noc-signed" type="file" accept=".pdf" class="form-control form-control-sm mb-3" (change)="pick($event)" />
          <label class="form-label small fw-semibold" for="noc-note">Remarks (optional, printed on the NOC and shown to the student)</label>
        } @else {
          <label class="form-label small fw-semibold" for="noc-note">Reason (the student sees it)</label>
        }
        <textarea id="noc-note" rows="3" maxlength="1000" class="form-control form-control-sm" [ngModel]="note()" (ngModelChange)="note.set($event)"
          [placeholder]="decision() === 'APPROVED' ? 'e.g. The student must complete the final semester exams before joining.' : 'e.g. The offer letter is not signed by the company.'"></textarea>
      }
      <div pmFooter class="d-flex gap-2 justify-content-end w-100">
        <button class="btn btn-sm btn-outline-secondary" (click)="deciding.set(null)">Cancel</button>
        <button class="btn btn-sm" [class.btn-success]="decision() === 'APPROVED'" [class.btn-danger]="decision() === 'REJECTED'"
          [disabled]="busy() || (decision() === 'REJECTED' && !note().trim()) || (decision() === 'APPROVED' && deciding()?.jobType !== 'INTERNSHIP' && !(ctc()! > 0))" (click)="decide()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> }
          {{ decision() === 'APPROVED' ? (file() ? 'Approve with this NOC' : 'Approve and create NOC') : 'Reject request' }}</button>
      </div>
    </pm-modal>`,
  styles: [`
    .oc-kpi { background: var(--pm-card-bg, var(--bs-body-bg)); border: 1px solid var(--pm-card-border, var(--bs-border-color)); border-radius: 14px; padding: .9rem 1rem; }
    .oc-kpi-btn { cursor: pointer; } .oc-kpi-btn:hover { box-shadow: 0 6px 16px rgba(15,23,42,.08); }
    .oc-n { font-size: 1.5rem; font-weight: 800; color: var(--pm-heading, inherit); line-height: 1.1; }
    .oc-l { font-size: .78rem; color: var(--pm-muted, var(--bs-secondary-color)); }
    .oc-kpi-ok .oc-n { color: #15803d; } .oc-kpi-warn .oc-n { color: #b45309; }
    .oc-st { display: inline-block; padding: .15rem .5rem; border-radius: 999px; font-size: .72rem; font-weight: 600; background: rgba(100,116,139,.16); color: #475569; }
    .oc-applied { background: rgba(37,99,235,.12); color: #1d4ed8; } .oc-interviewing { background: rgba(217,119,6,.16); color: #b45309; }
    .oc-offer { background: rgba(22,163,74,.14); color: #15803d; } .oc-rejected { background: rgba(225,29,72,.13); color: #be123c; }
    :host-context([data-bs-theme=dark]) .oc-st { filter: brightness(1.45); }
  `],
})
export class CollegeOffCampus implements OnInit {
  svc = inject(OffCampusService);
  private college = inject(CollegeService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  readonly statuses = APP_STATUSES;
  readonly label = appStatusLabel;
  readonly type = jobTypeLabel;
  readonly isAdmin = () => this.college.isAdmin();
  readonly canWrite = () => this.college.canWrite();
  readonly nocFilters: { key: NocStatus | null; label: string }[] = [
    { key: 'PENDING', label: 'Waiting' }, { key: 'APPROVED', label: 'Issued' }, { key: 'REJECTED', label: 'Rejected' }, { key: null, label: 'All' },
  ];
  tab = signal<Tab>('applications');
  status = signal<AppStatus | null>(null);
  q = signal('');
  data = signal<CollegeView | null>(null);
  exporting = signal(false);
  nocStatus = signal<NocStatus | null>('PENDING');
  nocs = signal<CollegeNoc[] | null>(null);
  deciding = signal<CollegeNoc | null>(null);
  decision = signal<'APPROVED' | 'REJECTED'>('APPROVED');
  note = signal('');
  file = signal<File | null>(null);
  ctc = signal<number | null>(null);
  busy = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.load();
    if (this.route.snapshot.queryParamMap.get('tab') === 'nocs') this.setTab('nocs');
  }

  setTab(t: Tab): void { this.tab.set(t); if (t === 'nocs') this.loadNocs(); }
  search(v: string): void { this.q.set(v); clearTimeout(this.timer); this.timer = setTimeout(() => this.load(), 300); }
  load(): void { this.svc.college({ status: this.status(), q: this.q().trim() || undefined }).subscribe(d => this.data.set(d)); }
  loadNocs(): void { this.nocs.set(null); this.svc.nocs(this.nocStatus()).subscribe(n => this.nocs.set(n)); }
  export(): void {
    this.exporting.set(true);
    this.svc.exportCollege(this.status()).subscribe({ next: () => this.exporting.set(false), error: () => this.exporting.set(false) });
  }

  openDecision(n: CollegeNoc, d: 'APPROVED' | 'REJECTED'): void {
    this.deciding.set(n); this.decision.set(d); this.note.set(''); this.file.set(null); this.ctc.set(n.offerCtcLpa ?? null);
  }
  pick(e: Event): void { this.file.set((e.target as HTMLInputElement).files?.[0] ?? null); }
  decide(): void {
    const n = this.deciding()!;
    this.busy.set(true);
    const approve = this.decision() === 'APPROVED';
    this.svc.decideNoc(n.id, this.decision(), this.note(), approve ? this.file() : null, approve && n.jobType !== 'INTERNSHIP' ? this.ctc() : null).subscribe({
      next: r => {
        this.busy.set(false);
        this.deciding.set(null);
        this.toast.success(r.status !== 'APPROVED' ? 'Request rejected; the student is notified'
          : r.placementRecorded ? `NOC issued; ${r.studentName} is now marked Placed` : `NOC issued to ${r.studentName}`);
        this.loadNocs();
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }
}
