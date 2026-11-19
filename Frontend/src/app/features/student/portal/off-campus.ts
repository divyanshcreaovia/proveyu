import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { Modal } from '../../../shared/ui/modal';
import { OffCampusPolicy } from '../../../core/models/college.models';
import {
  APP_STATUSES, AppStatus, JOB_FIELDS, JobField, OffCampusService, StudentJob, appStatusLabel, fieldLabel, jobTypeLabel, payLabel, workModeLabel,
} from '../../offcampus/off-campus.service';

type View = 'forMe' | 'saved' | 'mine' | 'all';
const DAY = 86_400_000;
const APPLIED: AppStatus[] = ['APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED'];

/**
 * Off-campus jobs posted by the ProveYu team. The student applies on the company's site; ProveYu remembers the click, asks whether the
 * application went through, and keeps a small tracker (applied, interviewing, offer) that the student's placement cell can see.
 */
@Component({
  selector: 'pm-student-off-campus',
  imports: [DatePipe, FormsModule, StatusBadge, EmptyState, Skeleton, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    @if (loading()) { <pm-skeleton [rows]="6" /> }
    @else if (policy() === 'DISABLED') {
      <pm-empty-state icon="bi-globe2" title="Off-campus jobs are turned off" text="Your placement cell has turned off off-campus jobs for your college. Ask them if you have questions." />
    } @else {
      <p class="small st-muted mb-3">Jobs and internships from companies outside campus drives, picked by the ProveYu team. You apply on the
        company's site; keep your status here so your placement cell knows how you're doing.</p>

      <section class="st-card oc-interests mb-3" aria-labelledby="oc-int-h">
        <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
          <h2 id="oc-int-h" class="h6 st-h mb-0">{{ interests().length ? 'Your fields' : 'Which fields do you want jobs in?' }}</h2>
          <span class="small st-muted">{{ interests().length ? '"For me" shows jobs in these fields. Tap to change.' : 'Pick one or more; we show those jobs first and only notify you about them.' }}</span>
          @if (savingInterests()) { <span class="spinner-border spinner-border-sm ms-auto" aria-label="Saving"></span> }
        </div>
        <div class="d-flex flex-wrap gap-2">
          @for (f of fieldOptions; track f.v) {
            <button type="button" class="oc-pick" [class.oc-pick-on]="interests().includes(f.v)" [attr.aria-pressed]="interests().includes(f.v)" (click)="toggleInterest(f.v)">
              <i class="bi {{ f.icon }}"></i> {{ f.l }}@if (countByField()[f.v]; as n) { <span class="oc-pick-n">{{ n }}</span> }</button>
          }
        </div>
      </section>

      @if (pending(); as p) {
        <div class="st-row oc-ask" role="status">
          <i class="bi bi-question-circle fs-5"></i>
          <div class="flex-grow-1 small"><strong>Did you finish applying to {{ p.companyName }} · {{ p.roleTitle }}?</strong></div>
          <button class="btn btn-sm btn-primary" (click)="setStatus(p, 'APPLIED')">Yes, I applied</button>
          <button class="btn btn-sm btn-outline-secondary" (click)="dismissed.set(p.id)">Not yet</button>
        </div>
      }

      <div class="st-filters">
        <div class="btn-group btn-group-sm" role="group" aria-label="Filter jobs">
          @for (v of views; track v.key) {
            <button type="button" class="btn" [class.btn-primary]="view() === v.key" [class.btn-outline-secondary]="view() !== v.key" (click)="view.set(v.key)">
              {{ v.label }}@if (count(v.key); as n) { <span class="ms-1 opacity-75">{{ n }}</span> }</button>
          }
        </div>
        @if (view() !== 'mine') {
          <select class="form-select form-select-sm" aria-label="Filter by field" [ngModel]="field()" (ngModelChange)="field.set($event || null)">
            <option [ngValue]="null">All fields</option>
            @for (f of fieldOptions; track f.v) { <option [ngValue]="f.v">{{ f.l }}</option> }
          </select>
        }
        <input type="search" class="form-control form-control-sm" placeholder="Search company, role, city or skill…" aria-label="Search jobs" [ngModel]="q()" (ngModelChange)="q.set($event)" />
      </div>

      @if (view() === 'mine') {
        @for (j of shown(); track j.id) {
          <div class="st-row">
            <div class="flex-grow-1 min-w-0"><strong class="st-h">{{ j.companyName }}</strong> <span class="st-muted">· {{ j.roleTitle }}</span>
              <div class="small st-muted">{{ type(j.jobType) }} · {{ pay(j) }}@if (j.note) { · {{ j.note }} }</div></div>
            @if (j.status === 'OFFER' && j.offerCtcLpa) { <span class="st-chip oc-offer">{{ j.offerCtcLpa }} LPA</span> }
            @if (j.status === 'OFFER') {
              @if (j.noc?.status === 'APPROVED') {
                <button class="btn btn-sm btn-success" (click)="svc.openStudentNocFile(j.noc!.id, 'noc')"><i class="bi bi-file-earmark-check me-1"></i>Download NOC</button>
              } @else if (j.noc?.status === 'PENDING') {
                <span class="st-chip oc-pending" title="Your placement cell is reviewing it"><i class="bi bi-hourglass-split"></i> NOC requested</span>
              } @else {
                <button class="btn btn-sm btn-outline-primary" (click)="openNoc(j)"><i class="bi bi-file-earmark-arrow-up me-1"></i>{{ j.noc ? 'Request NOC again' : 'Request NOC' }}</button>
              }
            }
            @if (j.noc?.status === 'REJECTED') { <div class="w-100 small text-danger"><i class="bi bi-x-circle"></i> NOC not given: {{ j.noc!.decisionNote }}</div> }
            @if (j.noc?.status === 'APPROVED' && j.noc!.decisionNote) { <div class="w-100 small st-muted"><i class="bi bi-chat-left-text"></i> {{ j.noc!.decisionNote }}</div> }
            <select class="form-select form-select-sm oc-sel" [attr.aria-label]="'Status for ' + j.companyName" [ngModel]="j.status" (ngModelChange)="setStatus(j, $event)">
              @for (s of options(j); track s.v) { <option [value]="s.v">{{ s.l }}</option> }
            </select>
          </div>
        } @empty {
          <pm-empty-state icon="bi-list-check" title="Nothing tracked yet" text="Apply to a job and tell us when you're done; it shows up here." />
        }
      } @else {
        <div class="row g-3">
          @for (j of shown(); track j.id) {
            <div class="col-md-6 col-xl-4"><article class="st-card h-100 d-flex flex-column">
              <div class="d-flex justify-content-between gap-2">
                <div class="min-w-0"><h2 class="h6 st-h mb-0 text-truncate">{{ j.companyName }}</h2><div class="small st-muted">{{ j.roleTitle }}</div></div>
                <button type="button" class="btn btn-sm oc-save" (click)="save(j)" [attr.aria-pressed]="j.saved" [attr.aria-label]="(j.saved ? 'Remove from saved: ' : 'Save ') + j.companyName">
                  <i class="bi" [class.bi-bookmark-fill]="j.saved" [class.bi-bookmark]="!j.saved"></i></button>
              </div>
              <div class="d-flex flex-wrap gap-1 my-2">
                @if (isNew(j)) { <span class="st-chip oc-new">New</span> }
                @if (j.field && j.field !== 'OTHER') { <span class="st-chip oc-fieldchip">{{ fLabel(j.field) }}</span> }
                <span class="st-chip">{{ type(j.jobType) }}</span><span class="st-chip">{{ mode(j.workMode) }}{{ j.location ? ' · ' + j.location : '' }}</span>
              </div>
              <div class="small"><i class="bi bi-cash"></i> {{ pay(j) }}</div>
              @if (j.source === 'CAREERJET') { <div class="small st-muted mt-1"><i class="bi bi-link-45deg"></i> via Careerjet</div> }
              @if (j.applyDeadline) {
                <div class="small mt-1" [class.text-danger]="closingSoon(j)" [class.st-muted]="!closingSoon(j)"><i class="bi bi-clock"></i> {{ closes(j) }}</div>
              }
              @if (j.reasons.length) { <div class="small text-danger mt-2"><i class="bi bi-x-circle"></i> {{ j.reasons.join('; ') }}</div> }
              <div class="d-flex gap-2 mt-auto pt-3 align-items-center flex-wrap">
                <button class="btn btn-sm btn-outline-secondary" (click)="detail.set(j)">Details</button>
                @if (j.status && j.status !== 'CLICKED') { <pm-status [value]="badge(j.status)" /> }
                @if (j.canApply && !applied(j)) { <button class="btn btn-sm btn-primary ms-auto" (click)="apply(j)">Apply <i class="bi bi-box-arrow-up-right ms-1"></i></button> }
              </div>
            </article></div>
          } @empty {
            <div class="col-12"><pm-empty-state icon="bi-globe2" [title]="view() === 'saved' ? 'No saved jobs' : view() === 'forMe' && interests().length ? 'No open jobs in your fields right now' : 'No jobs here yet'"
              [text]="view() === 'saved' ? 'Tap the bookmark on a job to keep it here.' : 'You get a notification when the ProveYu team posts a job you can apply to.'" /></div>
          }
        </div>
      }
    }

    <pm-modal [open]="!!detail()" [title]="(detail()?.companyName ?? '') + ' · ' + (detail()?.roleTitle ?? '')" size="lg" (closed)="detail.set(null)">
      @if (detail(); as j) {
        <p class="small st-pre">{{ j.description || 'No description provided.' }}</p>
        @if (j.skills.length) { <p>@for (s of j.skills; track s) { <span class="st-chip me-1 mb-1">{{ s }}</span> }</p> }
        <dl class="row small mb-0">
          <dt class="col-4">Type</dt><dd class="col-8">{{ type(j.jobType) }} · {{ mode(j.workMode) }}{{ j.location ? ' · ' + j.location : '' }}</dd>
          <dt class="col-4">Pay</dt><dd class="col-8">{{ pay(j) }}</dd>
          <dt class="col-4">Apply by</dt><dd class="col-8">{{ j.applyDeadline ? (j.applyDeadline | date: 'medium') : 'No deadline given' }}</dd>
          <dt class="col-4">Eligibility</dt><dd class="col-8">@if (j.canApply) { <span class="text-success">You can apply</span> } @else { <span class="text-danger">{{ j.reasons.join('; ') }}</span> }</dd>
          @if (j.status) { <dt class="col-4">Your status</dt><dd class="col-8">{{ label(j.status) }}</dd> }
        </dl>
        <p class="small st-muted mt-3 mb-0"><i class="bi bi-info-circle"></i> You apply on the company's own site. ProveYu never asks you to pay for a job.</p>
      }
      <div pmFooter>@if (detail()?.canApply) { <button class="btn btn-primary" (click)="apply(detail()!)">Apply on company site <i class="bi bi-box-arrow-up-right ms-1"></i></button> }</div>
    </pm-modal>

    <pm-modal [open]="!!nocFor()" [title]="'Request NOC · ' + (nocFor()?.companyName ?? '')" (closed)="nocFor.set(null)">
      @if (nocFor(); as j) {
        <p class="small st-muted">Your placement cell reviews the offer letter and issues a No Objection Certificate for {{ j.companyName }} · {{ j.roleTitle }}.
          You get a notification when they decide.</p>
        <label class="form-label small fw-semibold" for="noc-file">Offer letter (PDF or image, up to 10 MB)</label>
        <input id="noc-file" type="file" class="form-control form-control-sm mb-3" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" (change)="pick($event)" />
        <label class="form-label small fw-semibold" for="noc-note">Note for the placement cell (optional)</label>
        <textarea id="noc-note" rows="3" maxlength="1000" class="form-control form-control-sm" [ngModel]="nocNote()" (ngModelChange)="nocNote.set($event)"
          placeholder="e.g. Joining date 1 July; the company needs the NOC by 20 June"></textarea>
      }
      <div pmFooter class="d-flex gap-2 justify-content-end w-100">
        <button class="btn btn-sm btn-outline-secondary" (click)="nocFor.set(null)">Cancel</button>
        <button class="btn btn-sm btn-primary" [disabled]="!nocFile() || sending()" (click)="sendNoc()">
          @if (sending()) { <span class="spinner-border spinner-border-sm me-1"></span> }Send request</button>
      </div>
    </pm-modal>`,
  styles: [`
    .oc-ask { border-left: 4px solid #2563eb; }
    .oc-save { color: var(--pm-link); border: 0; padding: .1rem .35rem; align-self: flex-start; }
    .oc-new { background: rgba(124,58,237,.14); color: #6d28d9; } .oc-offer { background: rgba(22,163,74,.14); color: #15803d; }
    .oc-pending { background: rgba(217,119,6,.16); color: #b45309; }
    .oc-fieldchip { background: rgba(37,99,235,.1); color: #1d4ed8; }
    .oc-interests { padding: 1rem 1.25rem; }
    .oc-pick { border: 1px solid var(--pm-card-border); background: var(--pm-card-bg); color: var(--pm-label); border-radius: 999px; padding: .3rem .75rem;
      font-size: .8rem; font-weight: 600; display: inline-flex; align-items: center; gap: .35rem; }
    .oc-pick:hover { border-color: var(--pm-link); }
    .oc-pick-on { background: var(--pm-primary); border-color: var(--pm-primary); color: var(--pm-primary-text); }
    .oc-pick-n { font-size: .7rem; opacity: .75; }
    .oc-pick:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 2px; }
    .oc-sel { width: auto; min-width: 150px; }
    .min-w-0 { min-width: 0; }
  `],
})
export class StudentOffCampus implements OnInit {
  svc = inject(OffCampusService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly type = jobTypeLabel;
  readonly mode = workModeLabel;
  readonly pay = payLabel;
  readonly label = appStatusLabel;
  readonly fLabel = fieldLabel;
  readonly fieldOptions = JOB_FIELDS.filter(f => f.v !== 'OTHER');
  interests = signal<JobField[]>([]);
  field = signal<JobField | null>(null);
  savingInterests = signal(false);
  /** Open jobs the student can apply to, per field (shown on the picker). */
  countByField = computed(() => {
    const out: Partial<Record<JobField, number>> = {};
    for (const j of this.jobs()) if (j.field && j.canApply && !this.applied(j)) out[j.field] = (out[j.field] ?? 0) + 1;
    return out;
  });
  private saveTimer?: ReturnType<typeof setTimeout>;
  views: { key: View; label: string }[] = [
    { key: 'forMe', label: 'For me' }, { key: 'saved', label: 'Saved' }, { key: 'mine', label: 'My applications' }, { key: 'all', label: 'All' },
  ];
  view = signal<View>('forMe');
  q = signal('');
  loading = signal(true);
  policy = signal<OffCampusPolicy>('ALLOW');
  jobs = signal<StudentJob[]>([]);
  detail = signal<StudentJob | null>(null);
  dismissed = signal<string | null>(null);
  nocFor = signal<StudentJob | null>(null);
  nocFile = signal<File | null>(null);
  nocNote = signal('');
  sending = signal(false);
  /** The latest job whose link the student opened but has not confirmed. */
  pending = computed(() => this.jobs().find(j => j.status === 'CLICKED' && j.canApply && j.id !== this.dismissed()) ?? null);
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    const field = this.view() === 'mine' ? null : this.field();
    return this.jobs().filter(j => this.inView(j, this.view())).filter(j => !field || j.field === field)
      .filter(j => !q || j.companyName.toLowerCase().includes(q) || j.roleTitle.toLowerCase().includes(q)
        || (j.location ?? '').toLowerCase().includes(q) || j.skills.some(s => s.toLowerCase().includes(q)));
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.svc.studentJobs().subscribe({
      next: r => {
        this.policy.set(r.policy);
        this.interests.set(r.interests);
        this.jobs.set(r.jobs);
        if (this.loading() && !r.jobs.some(j => this.inView(j, 'forMe'))) this.view.set(r.jobs.some(j => this.inView(j, 'mine')) ? 'mine' : 'all');
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  count(v: View): number { return v === 'all' ? 0 : this.jobs().filter(j => this.inView(j, v)).length; }
  applied(j: StudentJob): boolean { return !!j.status && APPLIED.includes(j.status); }
  isNew(j: StudentJob): boolean { return !!j.publishedAt && Date.now() - new Date(j.publishedAt).getTime() < 3 * DAY; }
  closingSoon(j: StudentJob): boolean { return !!j.applyDeadline && new Date(j.applyDeadline).getTime() - Date.now() < 3 * DAY; }
  closes(j: StudentJob): string {
    const ms = new Date(j.applyDeadline!).getTime() - Date.now();
    if (ms <= 0) return 'Applications closed';
    const days = Math.floor(ms / DAY);
    if (days === 0) return `Closes in ${Math.max(1, Math.round(ms / 3_600_000))} h`;
    return days === 1 ? 'Closes tomorrow' : `Closes in ${days} days`;
  }
  /** Status badge values the shared badge knows how to colour. */
  badge(s: AppStatus): string { return s === 'OFFER' ? 'OFFERED' : s === 'INTERVIEWING' ? 'IN_PROCESS' : s === 'NOT_INTERESTED' ? 'WITHDRAWN' : s; }

  /** Statuses the student may pick now (the server enforces the same rule). */
  options(j: StudentJob): { v: AppStatus; l: string }[] {
    const started = this.applied(j);
    return APP_STATUSES.filter(s => s.v !== 'CLICKED' && (started || s.v === 'APPLIED' || s.v === 'NOT_INTERESTED' || s.v === j.status));
  }

  apply(j: StudentJob): void {
    // open the tab synchronously (inside the click) so pop-up blockers allow it, then point it at the link the server returns
    const tab = window.open('about:blank', '_blank');
    this.svc.click(j.id).subscribe({
      next: r => {
        if (tab) { tab.opener = null; tab.location.href = r.url; } else { window.open(r.url, '_blank', 'noopener'); }
        this.detail.set(null);
        this.dismissed.set(null);
        this.load();
      },
      error: () => tab?.close(),
    });
  }

  save(j: StudentJob): void {
    this.svc.toggleSave(j.id).subscribe(saved => {
      this.jobs.update(list => list.map(x => x.id === j.id ? { ...x, saved } : x));
      this.toast.success(saved ? 'Saved' : 'Removed from saved');
    });
  }

  async setStatus(j: StudentJob, status: AppStatus): Promise<void> {
    let offerCtcLpa: number | null = null;
    if (status === 'OFFER') {
      const r = await this.confirm.ask({ title: `Congratulations on the ${j.companyName} offer!`, confirmText: 'Save',
        message: 'Your placement cell will see it. Add the CTC if you know it.', inputLabel: 'CTC in LPA (optional)' });
      if (!r.ok) { this.jobs.update(l => [...l]); return; }
      const n = Number(r.value.trim());
      offerCtcLpa = r.value.trim() && Number.isFinite(n) && n >= 0 ? n : null;
    }
    this.svc.updateStatus(j.id, { status, offerCtcLpa, note: j.note ?? null }).subscribe({
      next: u => {
        this.jobs.update(list => list.map(x => x.id === u.id ? u : x));
        this.toast.success(status === 'APPLIED' && !this.applied(j) ? 'Marked as applied. Good luck!' : 'Status updated');
      },
      error: () => this.jobs.update(l => [...l]),
    });
  }

  toggleInterest(f: JobField): void {
    const cur = this.interests();
    this.interests.set(cur.includes(f) ? cur.filter(x => x !== f) : [...cur, f]);
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => {
      this.savingInterests.set(true);
      this.svc.setInterests(this.interests()).subscribe({
        next: saved => { this.interests.set(saved); this.savingInterests.set(false); },
        error: () => this.savingInterests.set(false),
      });
    }, 600);
  }

  openNoc(j: StudentJob): void { this.nocFor.set(j); this.nocFile.set(null); this.nocNote.set(''); }
  pick(e: Event): void { this.nocFile.set((e.target as HTMLInputElement).files?.[0] ?? null); }
  sendNoc(): void {
    const j = this.nocFor()!;
    this.sending.set(true);
    this.svc.requestNoc(j.id, this.nocFile()!, this.nocNote()).subscribe({
      next: noc => {
        this.sending.set(false);
        this.nocFor.set(null);
        this.jobs.update(list => list.map(x => x.id === j.id ? { ...x, noc } : x));
        this.toast.success('NOC request sent to your placement cell');
      },
      error: () => this.sending.set(false),
    });
  }

  private inView(j: StudentJob, v: View): boolean {
    switch (v) {
      case 'forMe': return j.canApply && !this.applied(j) && j.status !== 'NOT_INTERESTED'
        && (!this.interests().length || (!!j.field && this.interests().includes(j.field)));
      case 'saved': return j.saved;
      case 'mine': return this.applied(j);
      default: return true;
    }
  }
}
