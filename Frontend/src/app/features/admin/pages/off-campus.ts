import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { CollegeSummary } from '../../../core/models/college.models';
import { ImportRun, JOB_FIELDS, JobField, JobSource, fieldLabel } from '../../offcampus/off-campus.service';
import { AdminService } from '../services/admin.service';
import {
  APP_STATUSES, Applicant, JOB_TYPES, JobPage, JobRequest, JobStatus, OffCampusJob, OffCampusService, WORK_MODES, appStatusLabel, jobTypeLabel,
  payLabel, workModeLabel,
} from '../../offcampus/off-campus.service';

/** The editable form: lists as text so the inputs stay simple. */
interface Draft {
  companyName: string; roleTitle: string; jobType: JobRequest['jobType']; workMode: JobRequest['workMode']; field: JobField | null; location: string;
  ctcMinLpa: number | null; ctcMaxLpa: number | null; stipendPerMonth: number | null; description: string; skills: string; applyUrl: string;
  applyDeadline: string; batchYears: string; degreeKeywords: string; branchKeywords: string; minCgpa: number | null; maxBacklogs: number | null;
  collegeIds: string[];
}

const EMPTY: Draft = {
  companyName: '', roleTitle: '', jobType: 'FULL_TIME', workMode: 'ONSITE', field: null, location: '', ctcMinLpa: null, ctcMaxLpa: null, stipendPerMonth: null,
  description: '', skills: '', applyUrl: '', applyDeadline: '', batchYears: '', degreeKeywords: '', branchKeywords: '', minCgpa: null, maxBacklogs: null,
  collegeIds: [],
};

/** Off-campus jobs: post a job with eligibility filters, publish it to eligible students, and see who applied and got offers. */
@Component({
  selector: 'pm-admin-off-campus',
  imports: [DatePipe, FormsModule, Modal, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="btn-group btn-group-sm mb-3" role="group" aria-label="Job source">
      <button type="button" class="btn" [class.btn-dark]="source() === 'MANUAL'" [class.btn-outline-secondary]="source() !== 'MANUAL'" (click)="setSource('MANUAL')">
        <i class="bi bi-pencil-square me-1"></i>Posted by us</button>
      <button type="button" class="btn" [class.btn-dark]="source() === 'CAREERJET'" [class.btn-outline-secondary]="source() !== 'CAREERJET'" (click)="setSource('CAREERJET')">
        <i class="bi bi-cloud-download me-1"></i>Imported (last 15 days)</button>
    </div>

    @if (source() === 'CAREERJET') {
      <div class="oc-import border rounded-3 p-2 px-3 mb-3 small d-flex flex-wrap align-items-center gap-2">
        @if (run(); as r) {
          @if (!r.configured) { <span class="text-warning-emphasis"><i class="bi bi-exclamation-triangle me-1"></i>{{ r.error || 'Job import is not set up.' }}</span> }
          @else if (r.finishedAt) {
            <span><i class="bi bi-clock-history me-1"></i>Last import {{ r.finishedAt | date: 'd MMM, h:mm a' }}: <strong>{{ r.added }}</strong> new of {{ r.fetched }} ads,
              {{ r.expired }} older than 15 days removed.</span>
            @if (r.error) { <span class="text-danger">{{ r.error }}</span> }
          } @else { <span><i class="bi bi-clock me-1"></i>Imports every night at 12 from Careerjet. No import since the server started.</span> }
        }
        <button class="btn btn-sm btn-outline-primary ms-auto" (click)="runImport()" [disabled]="importing()">
          @if (importing()) { <span class="spinner-border spinner-border-sm me-1"></span>Importing… } @else { <i class="bi bi-arrow-repeat me-1"></i>Import now }</button>
      </div>
    }

    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <ul class="nav nav-pills small" role="tablist">
        @for (f of filters; track f.label) {
          <li class="nav-item"><button class="nav-link py-1 px-3" [class.active]="status() === f.key" (click)="setStatus(f.key)">
            {{ f.key === 'DRAFT' && source() === 'CAREERJET' ? 'To review' : f.label }}</button></li>
        }
      </ul>
      @if (source() === 'CAREERJET' && selected().size) {
        <span class="small text-body-secondary">{{ selected().size }} selected</span>
        <button class="btn btn-sm btn-primary" (click)="bulk('PUBLISHED')">Publish</button>
        <button class="btn btn-sm btn-outline-secondary" (click)="bulk('CLOSED')">Close</button>
      }
      <select class="form-select form-select-sm ms-auto" style="max-width: 210px" aria-label="Filter by field" [ngModel]="field()"
        (ngModelChange)="field.set($event || null); load(0)">
        <option [ngValue]="null">All fields</option>
        @for (f of fields; track f.v) { <option [ngValue]="f.v">{{ f.l }}</option> }
      </select>
      <input type="search" class="form-control form-control-sm" style="max-width: 240px" placeholder="Search company, role or city"
        aria-label="Search jobs" [ngModel]="q()" (ngModelChange)="search($event)" />
      @if (source() === 'MANUAL') { <button class="btn btn-sm btn-primary" (click)="openForm(null)"><i class="bi bi-plus-lg me-1"></i>New job</button> }
    </div>

    @if (!page()) { <pm-skeleton [rows]="5" /> } @else {
      <div class="table-responsive bg-body border rounded-3">
        <table class="table table-hover align-middle small mb-0">
          <thead><tr>
            @if (source() === 'CAREERJET') { <th style="width: 1%"><input type="checkbox" class="form-check-input" aria-label="Select all on this page"
              [checked]="allSelected()" (change)="toggleAll()" /></th> }
            <th>Job</th><th class="d-none d-lg-table-cell">Who can apply</th><th class="d-none d-md-table-cell">{{ source() === 'CAREERJET' ? 'Posted' : 'Apply by' }}</th>
            <th class="text-center" title="Opened link / applied / offers">Responses</th><th>Status</th><th></th></tr></thead>
          <tbody>
            @for (j of page()!.rows; track j.id) {
              <tr>
                @if (source() === 'CAREERJET') { <td><input type="checkbox" class="form-check-input" [attr.aria-label]="'Select ' + j.companyName"
                  [checked]="selected().has(j.id)" (change)="toggle(j.id)" /></td> }
                <td class="oc-job"><strong class="d-block text-truncate">{{ j.companyName }} · {{ j.roleTitle }}</strong>
                  @if (j.field) { <span class="oc-field">{{ fLabel(j.field) }}</span> }
                  <span class="text-body-secondary">{{ type(j.jobType) }} · {{ mode(j.workMode) }}{{ j.location ? ' · ' + j.location : '' }} · {{ pay(j) }}</span></td>
                <td class="d-none d-lg-table-cell text-body-secondary oc-elig">{{ eligibility(j) }}</td>
                <td class="d-none d-md-table-cell text-nowrap">
                  @if (j.source === 'CAREERJET') { {{ j.sourcePostedAt | date: 'd MMM' }} · <a [href]="j.applyUrl" target="_blank" rel="noopener noreferrer">ad</a> }
                  @else { {{ j.applyDeadline ? (j.applyDeadline | date: 'd MMM, h:mm a') : 'Open' }} }</td>
                <td class="text-center text-nowrap"><button class="btn btn-link btn-sm p-0 text-decoration-none" (click)="openApplicants(j)"
                  [attr.aria-label]="'See students who responded to ' + j.companyName">
                  {{ j.counts.clicked }} / <strong>{{ j.counts.applied }}</strong> / <span class="text-success fw-bold">{{ j.counts.offers }}</span></button></td>
                <td><pm-status [value]="j.status" /></td>
                <td class="text-end text-nowrap">
                  @if (j.status !== 'PUBLISHED') { <button class="btn btn-sm btn-outline-primary me-1" (click)="publish(j)">Publish</button> }
                  @else { <button class="btn btn-sm btn-outline-secondary me-1" (click)="close(j)">Close</button> }
                  <div class="btn-group">
                    <button class="btn btn-sm btn-outline-secondary" (click)="openForm(j)" aria-label="Edit job"><i class="bi bi-pencil"></i></button>
                    <button class="btn btn-sm btn-outline-secondary" (click)="duplicate(j)" aria-label="Copy job" title="Copy as a new draft"><i class="bi bi-copy"></i></button>
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7"><pm-empty-state icon="bi-globe2" [title]="source() === 'CAREERJET' ? 'No imported jobs here' : 'No off-campus jobs'"
                [text]="source() === 'CAREERJET' ? 'Jobs from the nightly import arrive here as drafts. Review and publish the good ones.'
                  : 'Post a job from a company outside campus drives. Eligible students get a notification when you publish it.'" /></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page()!.total > page()!.size) {
        <div class="d-flex align-items-center justify-content-end gap-2 mt-2 small">
          <span class="text-body-secondary">{{ page()!.page * page()!.size + 1 }}–{{ min(page()!.total, (page()!.page + 1) * page()!.size) }} of {{ page()!.total }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="page()!.page === 0" (click)="load(page()!.page - 1)" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="(page()!.page + 1) * page()!.size >= page()!.total" (click)="load(page()!.page + 1)" aria-label="Next page"><i class="bi bi-chevron-right"></i></button>
        </div>
      }
    }

    <pm-modal [open]="!!form()" [title]="editing() ? 'Edit off-campus job' : 'New off-campus job'" size="lg" (closed)="form.set(null)">
      @if (form(); as f) {
        <form id="oc-form" class="row g-3" (ngSubmit)="save()">
          <div class="col-md-6"><label class="form-label small fw-semibold" for="oc-co">Company</label>
            <input id="oc-co" class="form-control form-control-sm" [(ngModel)]="f.companyName" name="co" required maxlength="150" /></div>
          <div class="col-md-6"><label class="form-label small fw-semibold" for="oc-role">Role</label>
            <input id="oc-role" class="form-control form-control-sm" [(ngModel)]="f.roleTitle" name="role" required maxlength="150" placeholder="e.g. Software Engineer" /></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-type">Type</label>
            <select id="oc-type" class="form-select form-select-sm" [(ngModel)]="f.jobType" name="type">@for (t of jobTypes; track t.v) { <option [value]="t.v">{{ t.l }}</option> }</select></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-mode">Work mode</label>
            <select id="oc-mode" class="form-select form-select-sm" [(ngModel)]="f.workMode" name="mode">@for (t of workModes; track t.v) { <option [value]="t.v">{{ t.l }}</option> }</select></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-field">Field</label>
            <select id="oc-field" class="form-select form-select-sm" [(ngModel)]="f.field" name="field">
              <option [ngValue]="null">Detect from the role</option>
              @for (x of fields; track x.v) { <option [ngValue]="x.v">{{ x.l }}</option> }
            </select></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-loc">Location</label>
            <input id="oc-loc" class="form-control form-control-sm" [(ngModel)]="f.location" name="loc" maxlength="150" placeholder="e.g. Bengaluru" /></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-min">CTC from (LPA)</label>
            <input id="oc-min" type="number" min="0" step="0.1" class="form-control form-control-sm" [(ngModel)]="f.ctcMinLpa" name="min" /></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-max">CTC up to (LPA)</label>
            <input id="oc-max" type="number" min="0" step="0.1" class="form-control form-control-sm" [(ngModel)]="f.ctcMaxLpa" name="max" /></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-stip">Stipend (₹ per month)</label>
            <input id="oc-stip" type="number" min="0" class="form-control form-control-sm" [(ngModel)]="f.stipendPerMonth" name="stip" /></div>
          <div class="col-md-8"><label class="form-label small fw-semibold" for="oc-url">Apply link (company's careers page)</label>
            <input id="oc-url" type="url" class="form-control form-control-sm" [(ngModel)]="f.applyUrl" name="url" required maxlength="1000" placeholder="https://" /></div>
          <div class="col-md-4"><label class="form-label small fw-semibold" for="oc-dl">Apply by</label>
            <input id="oc-dl" type="datetime-local" class="form-control form-control-sm" [(ngModel)]="f.applyDeadline" name="dl" /></div>
          <div class="col-12"><label class="form-label small fw-semibold" for="oc-desc">Description</label>
            <textarea id="oc-desc" rows="4" class="form-control form-control-sm" [(ngModel)]="f.description" name="desc" maxlength="8000"
              placeholder="What the role is, selection process, anything students should know"></textarea></div>
          <div class="col-12"><label class="form-label small fw-semibold" for="oc-sk">Skills</label>
            <input id="oc-sk" class="form-control form-control-sm" [(ngModel)]="f.skills" name="sk" placeholder="Java, SQL, React (comma-separated)" /></div>

          <fieldset class="col-12"><legend class="form-label small fw-semibold mb-1">Who can apply <span class="fw-normal text-body-secondary">(blank = no restriction)</span></legend>
            <div class="row g-2">
              <div class="col-md-4"><label class="form-label small" for="oc-batch">Batch years</label>
                <input id="oc-batch" class="form-control form-control-sm" [(ngModel)]="f.batchYears" name="batch" placeholder="2025, 2026" /></div>
              <div class="col-md-4"><label class="form-label small" for="oc-deg">Degrees</label>
                <input id="oc-deg" class="form-control form-control-sm" [(ngModel)]="f.degreeKeywords" name="deg" placeholder="B.Tech, MCA" /></div>
              <div class="col-md-4"><label class="form-label small" for="oc-br">Branches</label>
                <input id="oc-br" class="form-control form-control-sm" [(ngModel)]="f.branchKeywords" name="br" placeholder="Computer, IT, Electronics" /></div>
              <div class="col-md-4"><label class="form-label small" for="oc-cg">Min CGPA</label>
                <input id="oc-cg" type="number" min="0" max="10" step="0.1" class="form-control form-control-sm" [(ngModel)]="f.minCgpa" name="cg" /></div>
              <div class="col-md-4"><label class="form-label small" for="oc-bl">Max active backlogs</label>
                <input id="oc-bl" type="number" min="0" max="50" class="form-control form-control-sm" [(ngModel)]="f.maxBacklogs" name="bl" /></div>
            </div>
            <div class="form-text">Degrees and branches match words in the student's profile: "Computer" matches "Computer Science", "IT" matches "Information Technology".</div>
          </fieldset>

          <fieldset class="col-12"><legend class="form-label small fw-semibold mb-1">Colleges
            <span class="fw-normal text-body-secondary">({{ allColleges() ? 'all approved colleges' : f.collegeIds.length + ' of ' + colleges().length + ' selected' }})</span></legend>
            <div class="form-check oc-all mb-2">
              <input class="form-check-input" type="checkbox" id="oc-all" [checked]="allColleges()" (change)="setAllColleges($any($event.target).checked)" />
              <label class="form-check-label small" for="oc-all"><strong>All colleges</strong>
                <span class="text-body-secondary">: every approved college, including colleges that join later</span></label>
            </div>
            @if (!allColleges()) {
            <div class="d-flex gap-2 align-items-center mb-2">
              <input type="search" class="form-control form-control-sm" placeholder="Filter colleges" aria-label="Filter colleges"
                [ngModel]="collegeQ()" (ngModelChange)="collegeQ.set($event)" [ngModelOptions]="{ standalone: true }" />
              <button type="button" class="btn btn-sm btn-outline-secondary text-nowrap" (click)="selectShown(true)">Select all{{ collegeQ() ? ' shown' : '' }}</button>
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="selectShown(false)">Clear</button>
            </div>
            <div class="oc-colleges border rounded-2 p-2">
              @for (c of shownColleges(); track c.collegeId) {
                <div class="form-check small"><input class="form-check-input" type="checkbox" [id]="'oc-c-' + c.collegeId" [checked]="f.collegeIds.includes(c.collegeId)"
                  (change)="toggleCollege(c.collegeId)" />
                  <label class="form-check-label" [for]="'oc-c-' + c.collegeId">{{ c.collegeName }}<span class="text-body-secondary">{{ c.city ? ' · ' + c.city : '' }}</span></label></div>
              } @empty { <div class="small text-body-secondary">No approved colleges match.</div> }
            </div>
            }
          </fieldset>
        </form>
      }
      <div pmFooter class="d-flex w-100 gap-2 justify-content-end">
        <button class="btn btn-sm btn-outline-secondary" (click)="form.set(null)">Cancel</button>
        <button class="btn btn-sm btn-primary" type="submit" form="oc-form" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> }{{ editing() ? 'Save changes' : 'Save as draft' }}</button>
      </div>
    </pm-modal>

    <pm-modal [open]="!!applicantsOf()" [title]="(applicantsOf()?.companyName ?? '') + ' · ' + (applicantsOf()?.roleTitle ?? '')" variant="drawer" size="lg"
      (closed)="applicantsOf.set(null)">
      @if (applicantsOf(); as j) {
        <div class="d-flex flex-wrap gap-2 mb-3">
          <span class="oc-stat"><strong>{{ j.counts.clicked }}</strong> opened link</span><span class="oc-stat"><strong>{{ j.counts.applied }}</strong> applied</span>
          <span class="oc-stat"><strong>{{ j.counts.interviewing }}</strong> interviewing</span><span class="oc-stat oc-stat-ok"><strong>{{ j.counts.offers }}</strong> offers</span>
          <span class="oc-stat"><strong>{{ j.counts.saved }}</strong> saved</span>
        </div>
        @if (!applicants()) { <pm-skeleton [rows]="4" /> } @else {
          <table class="table table-sm align-middle small mb-0">
            <thead><tr><th>Student</th><th>College</th><th>Status</th></tr></thead>
            <tbody>
              @for (a of applicants()!; track a.id) {
                <tr><td><strong>{{ a.studentName }}</strong><br><span class="text-body-secondary">{{ a.rollNumber }} · {{ a.branch ?? '-' }} · {{ a.batchYear }}</span></td>
                  <td>{{ a.collegeName }}</td>
                  <td>{{ statusLabel(a.status) }}@if (a.offerCtcLpa) { <span class="text-success fw-semibold"> · {{ a.offerCtcLpa }} LPA</span> }
                    @if (a.note) { <br><span class="text-body-secondary">{{ a.note }}</span> }</td></tr>
              } @empty { <tr><td colspan="3"><pm-empty-state icon="bi-people" title="No responses yet" text="Students appear here once they open the apply link." /></td></tr> }
            </tbody>
          </table>
        }
      }
    </pm-modal>`,
  styles: [`
    .oc-job { max-width: 340px; } .oc-elig { max-width: 260px; }
    .oc-colleges { max-height: 180px; overflow: auto; }
    .oc-stat { background: var(--bs-tertiary-bg); border-radius: 8px; padding: .3rem .6rem; font-size: .8rem; }
    .oc-stat-ok { background: rgba(22,163,74,.12); color: #15803d; }
    .oc-import { background: var(--bs-tertiary-bg); }
    .oc-field { display: inline-block; font-size: .68rem; font-weight: 600; padding: .05rem .4rem; border-radius: 6px; margin-right: .3rem;
      background: rgba(37,99,235,.1); color: #1d4ed8; }
  `],
})
export class AdminOffCampusPage implements OnInit {
  private svc = inject(OffCampusService);
  private admin = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly jobTypes = JOB_TYPES;
  readonly workModes = WORK_MODES;
  readonly statuses = APP_STATUSES;
  readonly min = Math.min;
  readonly type = jobTypeLabel;
  readonly mode = workModeLabel;
  readonly pay = payLabel;
  readonly statusLabel = appStatusLabel;
  readonly filters: { key: JobStatus | null; label: string }[] = [
    { key: 'PUBLISHED', label: 'Live' }, { key: 'DRAFT', label: 'Drafts' }, { key: 'CLOSED', label: 'Closed' }, { key: null, label: 'All' },
  ];
  status = signal<JobStatus | null>(null);
  field = signal<JobField | null>(null);
  readonly fields = JOB_FIELDS;
  readonly fLabel = fieldLabel;
  source = signal<JobSource>('MANUAL');
  selected = signal<Set<string>>(new Set());
  run = signal<ImportRun | null>(null);
  importing = signal(false);
  allSelected = computed(() => !!this.page()?.rows.length && this.page()!.rows.every(r => this.selected().has(r.id)));
  q = signal('');
  page = signal<JobPage | null>(null);
  form = signal<Draft | null>(null);
  editing = signal<OffCampusJob | null>(null);
  busy = signal(false);
  colleges = signal<CollegeSummary[]>([]);
  collegeQ = signal('');
  allColleges = signal(true);
  shownColleges = computed(() => {
    const q = this.collegeQ().trim().toLowerCase();
    return this.colleges().filter(c => !q || c.collegeName.toLowerCase().includes(q) || (c.city ?? '').toLowerCase().includes(q));
  });
  applicantsOf = signal<OffCampusJob | null>(null);
  applicants = signal<Applicant[] | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.load(0);
    this.admin.colleges('APPROVED').subscribe(c => this.colleges.set(c));
  }

  setStatus(s: JobStatus | null): void { this.status.set(s); this.load(0); }
  setSource(s: JobSource): void {
    this.source.set(s);
    this.status.set(s === 'CAREERJET' ? 'DRAFT' : null);
    if (s === 'CAREERJET') this.svc.importStatus().subscribe(r => this.run.set(r));
    this.load(0);
  }
  toggle(id: string): void {
    const next = new Set(this.selected());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selected.set(next);
  }
  toggleAll(): void {
    const rows = this.page()?.rows ?? [];
    this.selected.set(this.allSelected() ? new Set() : new Set(rows.map(r => r.id)));
  }
  bulk(status: JobStatus): void {
    const ids = [...this.selected()];
    this.svc.bulkStatus(ids, status).subscribe(n => {
      this.toast.success(`${n} job${n === 1 ? '' : 's'} ${status === 'PUBLISHED' ? 'published' : 'closed'}`);
      this.load(this.page()?.page ?? 0);
    });
  }
  runImport(): void {
    this.importing.set(true);
    this.svc.runImport().subscribe({
      next: r => { this.importing.set(false); this.run.set(r); if (r.configured) this.toast.success(`${r.added} new jobs imported`); this.load(0); },
      error: () => this.importing.set(false),
    });
  }
  search(v: string): void { this.q.set(v); clearTimeout(this.timer); this.timer = setTimeout(() => this.load(0), 300); }
  load(p: number): void {
    this.selected.set(new Set());
    this.svc.list({ status: this.status(), source: this.source(), field: this.field(), q: this.q().trim() || undefined, page: p, size: 25 }).subscribe(pg => this.page.set(pg));
  }

  eligibility(j: OffCampusJob): string {
    const parts: string[] = [];
    if (j.batchYears.length) parts.push(j.batchYears.join('/') + ' batch');
    if (j.degreeKeywords) parts.push(j.degreeKeywords);
    if (j.branchKeywords) parts.push(j.branchKeywords);
    if (j.minCgpa) parts.push(`CGPA ${j.minCgpa}+`);
    if (j.maxBacklogs != null) parts.push(j.maxBacklogs === 0 ? 'no backlogs' : `≤${j.maxBacklogs} backlogs`);
    if (j.collegeIds.length) parts.push(`${j.collegeIds.length} college${j.collegeIds.length > 1 ? 's' : ''}`);
    return parts.length ? parts.join(' · ') : 'All students';
  }

  openForm(j: OffCampusJob | null): void {
    this.editing.set(j);
    this.collegeQ.set('');
    this.form.set(j ? {
      companyName: j.companyName, roleTitle: j.roleTitle, jobType: j.jobType, workMode: j.workMode, field: j.field ?? null, location: j.location ?? '',
      ctcMinLpa: j.ctcMinLpa ?? null, ctcMaxLpa: j.ctcMaxLpa ?? null, stipendPerMonth: j.stipendPerMonth ?? null, description: j.description ?? '',
      skills: j.skills.join(', '), applyUrl: j.applyUrl, applyDeadline: j.applyDeadline ? toLocalInput(j.applyDeadline) : '',
      batchYears: j.batchYears.join(', '), degreeKeywords: j.degreeKeywords ?? '', branchKeywords: j.branchKeywords ?? '', minCgpa: j.minCgpa ?? null,
      maxBacklogs: j.maxBacklogs ?? null, collegeIds: [...j.collegeIds],
    } : { ...EMPTY, collegeIds: [] });
    this.allColleges.set(!j || !j.collegeIds.length);
  }

  /** "All colleges" stores no college list, so colleges that join later see the job too. */
  setAllColleges(on: boolean): void {
    this.allColleges.set(on);
    const f = this.form()!;
    f.collegeIds = [];
    this.form.set({ ...f });
  }

  toggleCollege(id: string): void {
    const f = this.form()!;
    f.collegeIds = f.collegeIds.includes(id) ? f.collegeIds.filter(x => x !== id) : [...f.collegeIds, id];
    this.form.set({ ...f });
  }

  /** Select (or clear) every college the filter shows. */
  selectShown(on: boolean): void {
    const f = this.form()!;
    const shown = this.shownColleges().map(c => c.collegeId);
    f.collegeIds = on ? [...new Set([...f.collegeIds, ...shown])] : f.collegeIds.filter(id => !shown.includes(id));
    this.form.set({ ...f });
  }

  save(): void {
    const f = this.form()!;
    if (!this.allColleges() && !f.collegeIds.length) { this.toast.error('Pick at least one college, or tick "All colleges"'); return; }
    const years = f.batchYears.split(/[,\s]+/).map(s => s.trim()).filter(Boolean).map(Number);
    if (years.some(y => !Number.isInteger(y) || y < 2000 || y > 2100)) { this.toast.error('Batch years should look like 2025, 2026'); return; }
    const body: JobRequest = {
      companyName: f.companyName.trim(), roleTitle: f.roleTitle.trim(), jobType: f.jobType, workMode: f.workMode, field: f.field, location: f.location.trim() || null,
      ctcMinLpa: f.ctcMinLpa ?? null, ctcMaxLpa: f.ctcMaxLpa ?? null, stipendPerMonth: f.stipendPerMonth ?? null, description: f.description.trim() || null,
      skills: f.skills.split(',').map(s => s.trim()).filter(Boolean), applyUrl: f.applyUrl.trim(),
      applyDeadline: f.applyDeadline ? new Date(f.applyDeadline).toISOString() : null, batchYears: years,
      degreeKeywords: f.degreeKeywords.trim() || null, branchKeywords: f.branchKeywords.trim() || null, minCgpa: f.minCgpa ?? null,
      maxBacklogs: f.maxBacklogs ?? null, collegeIds: f.collegeIds,
    };
    this.busy.set(true);
    const id = this.editing()?.id;
    (id ? this.svc.update(id, body) : this.svc.create(body)).subscribe({
      next: () => { this.busy.set(false); this.form.set(null); this.toast.success(id ? 'Job saved' : 'Saved as a draft. Publish it when ready.'); this.load(this.page()?.page ?? 0); },
      error: () => this.busy.set(false),
    });
  }

  async publish(j: OffCampusJob): Promise<void> {
    const first = !j.publishedAt;
    const imported = j.source !== 'MANUAL';
    const r = await this.confirm.ask({
      title: `Publish ${j.companyName} · ${j.roleTitle}?`, confirmText: 'Publish',
      message: !first ? 'The job opens again for students.'
        : imported ? 'Students who match the eligibility filters see it in Off-campus jobs. Imported jobs do not send a notification.'
        : 'Students who match the eligibility filters see it now and get a notification.',
    });
    if (!r.ok) return;
    this.svc.setStatus(j.id, 'PUBLISHED', !imported).subscribe(() => { this.toast.success('Job published'); this.load(this.page()?.page ?? 0); });
  }

  async close(j: OffCampusJob): Promise<void> {
    const r = await this.confirm.ask({ title: `Close ${j.companyName} · ${j.roleTitle}?`, confirmText: 'Close job', tone: 'warning',
      message: 'Students can no longer open the apply link. Those who already applied keep it in their list.' });
    if (!r.ok) return;
    this.svc.setStatus(j.id, 'CLOSED').subscribe(() => { this.toast.success('Job closed'); this.load(this.page()?.page ?? 0); });
  }

  duplicate(j: OffCampusJob): void {
    this.svc.duplicate(j.id).subscribe(copy => { this.toast.success('Copied as a draft'); this.load(0); this.openForm(copy); });
  }

  openApplicants(j: OffCampusJob): void {
    this.applicantsOf.set(j);
    this.applicants.set(null);
    this.svc.applicants(j.id).subscribe(a => this.applicants.set(a));
  }
}

/** ISO instant -> value for <input type="datetime-local"> in the browser's time zone. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
