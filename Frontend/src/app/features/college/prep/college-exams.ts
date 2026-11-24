import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminTest, ExamMonitor, ExamSettings, MonitorRow, PrepService, ReviewView, TestMode } from '../../student/services/prep.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { Modal } from '../../../shared/ui/modal';
import { AppError } from '../../../core/api';
import { Pager, pageOf } from '../../../shared/ui/pager';

/** datetime-local value (local time) <-> ISO instant. */
const toLocal = (iso?: string | null): string => {
  if (!iso) return '';
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};
const toIso = (local: string): string | null => (local ? new Date(local).toISOString() : null);

/**
 * Exam settings of one college test: practice (any time, retakes) or exam (one attempt in a time window, for chosen branches and
 * batch years, optional shuffling, answers and results hidden until released).
 */
@Component({
  selector: 'pm-exam-settings',
  imports: [FormsModule, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="!!test()" [title]="'Settings · ' + (test()?.title ?? '')" size="lg" (closed)="closed.emit()">
      <div class="btn-group w-100 mb-3" role="radiogroup" aria-label="Test type">
        <button type="button" class="btn" [class.btn-primary]="mode() === 'PRACTICE'" [class.btn-outline-primary]="mode() !== 'PRACTICE'"
          role="radio" [attr.aria-checked]="mode() === 'PRACTICE'" (click)="mode.set('PRACTICE')"><i class="bi bi-journal-code me-1"></i>Practice test</button>
        <button type="button" class="btn" [class.btn-primary]="mode() === 'EXAM'" [class.btn-outline-primary]="mode() !== 'EXAM'"
          role="radio" [attr.aria-checked]="mode() === 'EXAM'" (click)="mode.set('EXAM')"><i class="bi bi-pencil-square me-1"></i>Exam (sessional)</button>
      </div>
      <p class="small text-body-secondary">
        @if (mode() === 'EXAM') { One attempt per student, only inside the time window, only for the branches and batches you choose. }
        @else { Open to all your students any time; they can retake it. }
      </p>

      @if (mode() === 'EXAM') {
        <div class="row g-2 mb-3">
          <div class="col-sm-6"><label class="form-label small fw-semibold" for="ex-start">Starts</label>
            <input id="ex-start" type="datetime-local" class="form-control form-control-sm" [ngModel]="start()" (ngModelChange)="start.set($event)" /></div>
          <div class="col-sm-6"><label class="form-label small fw-semibold" for="ex-end">Ends (everyone's test is submitted by then)</label>
            <input id="ex-end" type="datetime-local" class="form-control form-control-sm" [ngModel]="end()" (ngModelChange)="end.set($event)" /></div>
          @if (windowNote()) { <div class="col-12 small text-warning-emphasis">{{ windowNote() }}</div> }
        </div>
        <fieldset class="mb-3">
          <legend class="form-label small fw-semibold mb-1">Branches <span class="fw-normal text-body-secondary">(none ticked = all)</span></legend>
          <div class="ex-checks">
            @for (b of college.branches(); track b.id) {
              <label class="form-check small"><input class="form-check-input" type="checkbox" [checked]="branches().has(b.id)" (change)="toggle(branches, b.id)" />
                {{ b.code }} · {{ b.name }}</label>
            }
          </div>
        </fieldset>
        <fieldset class="mb-3">
          <legend class="form-label small fw-semibold mb-1">Batch (passing year) <span class="fw-normal text-body-secondary">(none ticked = all)</span></legend>
          <div class="ex-checks">
            @for (y of years(); track y) {
              <label class="form-check small"><input class="form-check-input" type="checkbox" [checked]="batches().has(y)" (change)="toggle(batches, y)" /> {{ y }}</label>
            }
          </div>
        </fieldset>
      }

      <fieldset class="mb-2">
        <legend class="form-label small fw-semibold mb-1">Questions</legend>
        <label class="form-check form-switch small"><input class="form-check-input" type="checkbox" [(ngModel)]="shuffleQuestions" />
          Shuffle question order for each student (inside each section)</label>
        <label class="form-check form-switch small"><input class="form-check-input" type="checkbox" [(ngModel)]="shuffleOptions" />
          Shuffle MCQ options for each student</label>
        <p class="small text-body-secondary mb-0 mt-1">A question pool is set in the test file: <code>"pick": 10</code> on a section gives each student 10 random
          questions of it.</p>
      </fieldset>
      <fieldset>
        <legend class="form-label small fw-semibold mb-1">What students see after submitting</legend>
        <label class="form-check form-switch small"><input class="form-check-input" type="checkbox" [(ngModel)]="showAnswers" />
          Correct answers and explanations</label>
        @if (mode() === 'EXAM') {
          <label class="form-check form-switch small"><input class="form-check-input" type="checkbox" [(ngModel)]="resultsReleased" />
            Release results (score) to students</label>
          <p class="small text-body-secondary mb-0">Keep this off during the exam and switch it on after marking.</p>
        }
      </fieldset>
      @if (error()) { <div class="alert alert-danger small mt-3 mb-0" role="alert">{{ error() }}</div> }
      <div pmFooter class="d-flex w-100 gap-2 justify-content-end">
        <button class="btn btn-sm btn-outline-secondary" (click)="closed.emit()">Cancel</button>
        <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="save()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Save</button>
      </div>
    </pm-modal>`,
  styles: [`.ex-checks { display: flex; flex-wrap: wrap; gap: .25rem 1rem; max-height: 160px; overflow: auto; }`],
})
export class ExamSettingsDialog {
  private prep = inject(PrepService);
  readonly college = inject(CollegeService);
  private toast = inject(ToastService);
  test = input<AdminTest | null>(null);
  saved = output<AdminTest>();
  closed = output<void>();

  mode = signal<TestMode>('PRACTICE');
  start = signal('');
  end = signal('');
  branches = signal(new Set<string>());
  batches = signal(new Set<number>());
  shuffleQuestions = false;
  shuffleOptions = false;
  showAnswers = true;
  resultsReleased = false;
  busy = signal(false);
  error = signal('');

  years = computed(() => {
    const ys = new Set(this.college.batches().map(b => b.gradYear));
    const now = new Date().getFullYear();
    for (let y = now - 1; y <= now + 4; y++) ys.add(y);
    return [...ys].sort();
  });
  windowNote = computed(() => {
    const t = this.test(), s = this.start(), e = this.end();
    if (!t || !s || !e) return '';
    const mins = (new Date(e).getTime() - new Date(s).getTime()) / 60000;
    return mins < t.durationMinutes ? `The window (${Math.round(mins)} min) is shorter than the test (${t.durationMinutes} min): late starters get less time.` : '';
  });

  constructor() {
    if (!this.college.branches().length) this.college.loadReference();
    effect(() => {
      const t = this.test();
      if (!t) return;
      const e: ExamSettings | undefined = t.exam;
      this.mode.set(e?.mode ?? 'PRACTICE');
      this.start.set(toLocal(e?.startsAt));
      this.end.set(toLocal(e?.endsAt));
      this.branches.set(new Set(e?.branchIds ?? []));
      this.batches.set(new Set(e?.batchYears ?? []));
      this.shuffleQuestions = e?.shuffleQuestions ?? false;
      this.shuffleOptions = e?.shuffleOptions ?? false;
      this.showAnswers = e?.showAnswers ?? true;
      this.resultsReleased = e?.mode === 'EXAM' ? !!e.resultsReleased : false;
      this.error.set('');
    });
  }

  toggle<T>(set: ReturnType<typeof signal<Set<T>>>, v: T): void {
    set.update(s => { const n = new Set(s); if (n.has(v)) n.delete(v); else n.add(v); return n; });
  }

  save(): void {
    const t = this.test();
    if (!t) return;
    const exam = this.mode() === 'EXAM';
    if (exam && (!this.start() || !this.end())) { this.error.set('Choose when the exam starts and ends.'); return; }
    const body: ExamSettings = {
      mode: this.mode(), startsAt: exam ? toIso(this.start()) : null, endsAt: exam ? toIso(this.end()) : null,
      branchIds: exam ? [...this.branches()] : [], batchYears: exam ? [...this.batches()] : [],
      shuffleQuestions: this.shuffleQuestions, shuffleOptions: this.shuffleOptions, showAnswers: this.showAnswers,
      resultsReleased: exam ? this.resultsReleased : true,
    };
    this.busy.set(true);
    this.error.set('');
    this.prep.examSettings(t.id, body).subscribe({
      next: r => { this.busy.set(false); this.toast.success('Settings saved'); this.saved.emit(r); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}

/**
 * Live invigilator view of one test: every eligible student with status, progress and proctoring counts (refreshes every 15 s),
 * extra time for one student, marking descriptive answers, and the marks register (CSV).
 */
@Component({
  selector: 'pm-exam-monitor',
  imports: [DatePipe, DecimalPipe, FormsModule, Modal, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (m(); as mon) {
      <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
        <button class="btn btn-sm btn-link px-0" (click)="back.emit()"><i class="bi bi-arrow-left me-1"></i>All tests</button>
        <h2 class="h5 fw-bold mb-0 ms-2 me-auto">{{ mon.title }}</h2>
        <button class="btn btn-sm btn-outline-secondary" (click)="load()"><i class="bi bi-arrow-clockwise me-1"></i>Refresh</button>
        <button class="btn btn-sm btn-outline-primary" (click)="exportCsv()"><i class="bi bi-file-earmark-spreadsheet me-1"></i>Marks (CSV)</button>
      </div>
      @if (mon.mode === 'EXAM') {
        <p class="small text-body-secondary mb-2"><i class="bi bi-calendar-event me-1"></i>{{ mon.startsAt | date: 'd MMM, h:mm a' }} – {{ mon.endsAt | date: 'd MMM, h:mm a' }}
          · refreshes every 15 seconds</p>
      }
      <div class="row g-2 mb-3">
        @for (k of kpis(); track k.label) {
          <div class="col-6 col-md"><div class="ex-kpi"><span class="ex-kpi-n" [style.color]="k.color">{{ k.n }}</span><span class="small text-body-secondary">{{ k.label }}</span></div></div>
        }
      </div>
      <div class="d-flex flex-wrap gap-2 mb-2">
        <input type="search" class="form-control form-control-sm" style="max-width: 260px" placeholder="Search name or roll number" aria-label="Search students"
          [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" />
        <select class="form-select form-select-sm" style="max-width: 200px" aria-label="Filter by status" [ngModel]="status()" (ngModelChange)="status.set($event); page.set(0)">
          <option value="">All students</option><option value="IN_PROGRESS">Writing now</option><option value="NOT_STARTED">Not started</option>
          <option value="SUBMITTED">Submitted</option><option value="FLAGGED">Flagged</option><option value="PENDING">Marking pending</option>
        </select>
      </div>
      <div class="table-responsive bg-body border rounded-3">
        <table class="table table-sm align-middle small mb-0">
          <thead><tr><th>Student</th><th>Status</th><th class="d-none d-md-table-cell">Progress</th><th class="d-none d-lg-table-cell">Time left</th>
            <th>Proctoring</th><th>Score</th><th></th></tr></thead>
          <tbody>
            @for (r of pageRows(); track r.studentId) {
              <tr>
                <td><strong>{{ r.studentName }}</strong><br><span class="text-body-secondary">{{ r.rollNumber }}{{ r.branchName ? ' · ' + r.branchName : '' }}</span></td>
                <td><span class="badge ex-st-{{ r.status.toLowerCase() }}">{{ statusLabel[r.status] }}</span>
                  @if (r.extraMinutes) { <span class="badge text-bg-light border ms-1" title="Extra time given">+{{ r.extraMinutes }}m</span> }</td>
                <td class="d-none d-md-table-cell">{{ r.answered }}/{{ r.questions }} answered
                  @if (r.lastActivityAt && r.status === 'IN_PROGRESS') { <br><span class="text-body-secondary">saved {{ r.lastActivityAt | date: 'h:mm:ss a' }}</span> }</td>
                <td class="d-none d-lg-table-cell">@if (r.status === 'IN_PROGRESS') { {{ left(r) }} } @else { - }</td>
                <td>
                  @if (r.tabSwitches) { <span class="badge text-bg-warning" title="Left the test">{{ r.tabSwitches }}× left</span> }
                  @if (r.aiWarnings) { <span class="badge ex-ai ms-1" title="Camera / microphone events"><i class="bi bi-camera-video me-1"></i>{{ r.aiWarnings }}</span> }
                  @if (r.autoSubmitReason === 'TAB_SWITCH') { <span class="badge text-bg-danger ms-1">auto-submitted</span> }
                  @if (!r.tabSwitches && !r.aiWarnings && r.status !== 'NOT_STARTED') { <span class="text-success"><i class="bi bi-shield-check"></i></span> }
                </td>
                <td>@if (r.score !== null && r.score !== undefined) { <strong>{{ r.score | number: '1.0-2' }}</strong>/{{ r.maxScore }} }
                  @if (r.reviewStatus === 'PENDING') { <br><span class="badge text-bg-info">marking pending</span> }</td>
                <td class="text-end text-nowrap">
                  @if (college.canWrite() && (r.status === 'IN_PROGRESS' || r.status === 'TIME_UP') && r.attemptId) {
                    <button class="btn btn-sm btn-outline-secondary me-1" (click)="extendOpen.set(r)" title="Give extra time"><i class="bi bi-clock-history"></i> Time</button>
                  }
                  @if (r.status === 'SUBMITTED' && r.attemptId) {
                    <button class="btn btn-sm" [class.btn-primary]="r.reviewStatus === 'PENDING'" [class.btn-outline-secondary]="r.reviewStatus !== 'PENDING'" (click)="openReview(r)">
                      {{ r.reviewStatus === 'PENDING' ? 'Mark' : 'Answers' }}</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="text-center text-body-secondary py-4">No students match.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <pm-pager [total]="shown().length" [(page)]="page" [(size)]="size" />
    } @else { <p class="small text-body-secondary">Loading…</p> }

    <pm-modal [open]="!!extendOpen()" [title]="'Extra time · ' + (extendOpen()?.studentName ?? '')" (closed)="extendOpen.set(null)">
      <label class="form-label small fw-semibold" for="ex-min">Minutes to add</label>
      <select id="ex-min" class="form-select form-select-sm" [(ngModel)]="extendBy">
        @for (n of [5, 10, 15, 20, 30, 45, 60]; track n) { <option [ngValue]="n">{{ n }} minutes</option> }
      </select>
      <p class="small text-body-secondary mt-2 mb-0">Their timer moves on by this much, also past the end of the exam window.</p>
      <div pmFooter class="d-flex w-100 gap-2 justify-content-end">
        <button class="btn btn-sm btn-outline-secondary" (click)="extendOpen.set(null)">Cancel</button>
        <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="extend()">Add time</button>
      </div>
    </pm-modal>

    <pm-modal [open]="!!review()" [title]="'Answers · ' + (review()?.studentName ?? '')" variant="drawer" size="lg" (closed)="review.set(null)">
      @if (review(); as rv) {
        <p class="small mb-3"><strong>{{ rv.result.score | number: '1.0-2' }}</strong> / {{ rv.result.maxScore }} · {{ rv.rollNumber }}
          @if (rv.result.pendingReview) { <span class="badge text-bg-info ms-1">marking pending</span> }</p>
        @for (q of rv.result.questions; track q.id; let i = $index) {
          <div class="ex-q mb-2" [class.ex-q-text]="q.type === 'TEXT'">
            <div class="d-flex gap-2 align-items-baseline mb-1"><strong class="small">Q{{ i + 1 }}</strong><span class="badge text-bg-light border">{{ q.section }}</span>
              <span class="badge text-bg-light border">{{ q.type === 'TEXT' ? 'Written' : q.type }}</span>
              <span class="ms-auto small">{{ q.marksAwarded | number: '1.0-2' }}/{{ q.marks }}</span></div>
            <div class="small ex-pre mb-1">{{ q.title ? q.title + ': ' : '' }}{{ q.text }}</div>
            @if (q.type === 'TEXT') {
              <div class="ex-ans small mb-2">{{ q.answerText || '(no answer)' }}</div>
              @if (q.explanation) { <div class="small text-body-secondary mb-2"><i class="bi bi-lightbulb me-1"></i>Model answer: {{ q.explanation }}</div> }
              @if (college.canWrite() && q.answerText) {
                <div class="d-flex align-items-center gap-2"><label class="small fw-semibold" [for]="'mk-' + q.id">Marks</label>
                  <input type="number" class="form-control form-control-sm" style="width: 90px" min="0" [max]="q.marks" step="0.5" [id]="'mk-' + q.id"
                    [ngModel]="marks()[q.id]" (ngModelChange)="setMark(q.id, $event)" />
                  <span class="small text-body-secondary">of {{ q.marks }}</span></div>
              }
            } @else if (q.type === 'MCQ') {
              <div class="small">Answer: {{ q.yourOption !== null && q.yourOption !== undefined ? q.options?.[q.yourOption] : '(not answered)' }}
                · correct: {{ q.options?.[q.correctOption ?? -1] }}</div>
            } @else {
              <div class="small">{{ q.testsPassed ?? 0 }}/{{ q.testsTotal }} hidden tests passed{{ q.language ? ' · ' + q.language : '' }}</div>
            }
          </div>
        }
      }
      <div pmFooter class="d-flex w-100 gap-2 justify-content-end">
        <button class="btn btn-sm btn-outline-secondary" (click)="review.set(null)">Close</button>
        @if (college.canWrite() && hasText()) {
          <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="saveMarks()">Save marks</button>
        }
      </div>
    </pm-modal>`,
  styles: [`
    .ex-kpi { background: var(--bs-body-bg); border: 1px solid var(--bs-border-color); border-radius: 10px; padding: .55rem .75rem; display: flex; flex-direction: column; }
    .ex-kpi-n { font-size: 1.35rem; font-weight: 800; line-height: 1.1; }
    .ex-st-not_started { background: var(--bs-tertiary-bg); color: var(--bs-secondary-color); }
    .ex-st-in_progress { background: rgba(22,163,74,.14); color: #15803d; }
    .ex-st-time_up { background: rgba(217,119,6,.15); color: #b45309; }
    .ex-st-submitted { background: rgba(37,99,235,.12); color: #1d4ed8; }
    .ex-ai { background: #ede9fe; color: #6d28d9; }
    .ex-q { border: 1px solid var(--bs-border-color); border-radius: 10px; padding: .6rem .75rem; }
    .ex-q-text { border-left: 3px solid #0ea5e9; }
    .ex-pre, .ex-ans { white-space: pre-wrap; }
    .ex-ans { background: var(--bs-tertiary-bg); border-radius: 8px; padding: .5rem .65rem; }
  `],
})
export class ExamMonitorView implements OnInit {
  private prep = inject(PrepService);
  readonly college = inject(CollegeService);
  private toast = inject(ToastService);
  test = input.required<AdminTest>();
  back = output<void>();

  m = signal<ExamMonitor | null>(null);
  q = signal('');
  status = signal('');
  busy = signal(false);
  extendOpen = signal<MonitorRow | null>(null);
  extendBy = 10;
  review = signal<ReviewView | null>(null);
  marks = signal<Record<string, number>>({});
  private now = signal(Date.now());
  private offset = 0;
  readonly statusLabel: Record<string, string> = { NOT_STARTED: 'Not started', IN_PROGRESS: 'Writing', TIME_UP: 'Time up', SUBMITTED: 'Submitted' };

  kpis = computed(() => {
    const m = this.m();
    if (!m) return [];
    const flagged = m.rows.filter(r => r.tabSwitches || r.aiWarnings).length;
    return [
      { label: 'Eligible', n: m.eligible, color: 'inherit' }, { label: 'Writing now', n: m.inProgress, color: '#15803d' },
      { label: 'Submitted', n: m.submitted, color: '#1d4ed8' }, { label: 'Not started', n: m.notStarted, color: 'inherit' },
      { label: 'Flagged', n: flagged, color: '#b45309' }, { label: 'Marking pending', n: m.pendingReview, color: '#0369a1' },
    ];
  });
  shown = computed(() => {
    const q = this.q().trim().toLowerCase(), st = this.status();
    return (this.m()?.rows ?? []).filter(r =>
      (!q || r.studentName.toLowerCase().includes(q) || (r.rollNumber ?? '').toLowerCase().includes(q))
      && (!st || (st === 'FLAGGED' ? (r.tabSwitches > 0 || r.aiWarnings > 0) : st === 'PENDING' ? r.reviewStatus === 'PENDING'
        : st === 'IN_PROGRESS' ? (r.status === 'IN_PROGRESS' || r.status === 'TIME_UP') : r.status === st)));
  });
  page = signal(0);
  size = signal(25);
  pageRows = computed(() => pageOf(this.shown(), this.page(), this.size()));
  hasText = computed(() => !!this.review()?.result.questions.some(q => q.type === 'TEXT' && q.answerText));

  constructor() {
    const poll = setInterval(() => this.load(), 15_000);
    const tick = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => { clearInterval(poll); clearInterval(tick); });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.prep.examMonitor(this.test().id).subscribe(m => {
      this.offset = new Date(m.serverNow).getTime() - Date.now();
      this.m.set(m);
    });
  }

  left(r: MonitorRow): string {
    if (!r.deadlineAt) return '-';
    const s = Math.max(0, Math.round((new Date(r.deadlineAt).getTime() - this.now() - this.offset) / 1000));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  extend(): void {
    const r = this.extendOpen();
    if (!r?.attemptId) return;
    this.busy.set(true);
    this.prep.examExtend(this.test().id, r.attemptId, this.extendBy).subscribe({
      next: () => { this.busy.set(false); this.toast.success(`${this.extendBy} minutes added for ${r.studentName}`); this.extendOpen.set(null); this.load(); },
      error: () => this.busy.set(false),
    });
  }

  openReview(r: MonitorRow): void {
    if (!r.attemptId) return;
    this.prep.examReview(this.test().id, r.attemptId).subscribe(v => {
      const m: Record<string, number> = {};
      v.result.questions.filter(q => q.type === 'TEXT' && q.answerText && !q.pendingReview).forEach(q => m[q.id] = Number(q.marksAwarded));
      this.marks.set(m);
      this.review.set(v);
    });
  }

  setMark(qid: string, v: number | string): void { this.marks.update(m => ({ ...m, [qid]: Number(v) })); }

  saveMarks(): void {
    const v = this.review();
    if (!v) return;
    const marks = Object.fromEntries(Object.entries(this.marks()).filter(([, n]) => n !== null && !Number.isNaN(n)));
    if (!Object.keys(marks).length) { this.toast.error('Enter marks first'); return; }
    this.busy.set(true);
    this.prep.examMark(this.test().id, v.attemptId, marks).subscribe({
      next: nv => { this.busy.set(false); this.toast.success(nv.result.pendingReview ? 'Marks saved (some answers still to mark)' : 'Marks saved'); this.review.set(nv); this.load(); },
      error: () => this.busy.set(false),
    });
  }

  exportCsv(): void { this.prep.examExport(this.test()).subscribe(); }
}
