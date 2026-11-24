import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AttemptHistory, PrepService, TestCard } from '../services/prep.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { enterFullscreen, exitFullscreen } from '../../../shared/fullscreen';
import { ScreenProctor, SHARE_PROBLEM_TEXT, ShareProblem } from './screen-proctor';
import { ToastService } from '../../../core/services/toast.service';
import { CameraProctor } from './camera-proctor';
import { ProctorPrecheck } from './proctor-precheck';

/**
 * Preparation tab: the mock test ladder (easy service-company patterns up to hard product-company ones; each opens after
 * scoring more than the unlock percentage in the one before), other standalone tests, and past attempts.
 */
@Component({
  selector: 'pm-prep-home',
  imports: [DatePipe, DecimalPipe, RouterLink, EmptyState, Skeleton, ProctorPrecheck],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <section class="prep-hero st-card mb-3">
      <div class="min-w-0">
        @if (tab() === 'college') {
          <h2 class="h5 st-h mb-1">Tests and exams from your college</h2>
          <p class="small st-muted mb-0">Sessional exams run in a fixed time window and can be taken once; practice tests from your placement cell are open any time.</p>
        } @else {
          <h2 class="h5 st-h mb-1">Practise like the real test</h2>
          <p class="small st-muted mb-0">Timed mock tests with aptitude, reasoning, verbal, technical MCQs and real coding questions. Take one a day or two before your drive.</p>
        }
      </div>
      <div class="prep-hero-stats">
        <div><span class="prep-num">{{ tabTests().length }}</span><span class="st-muted small">tests</span></div>
        <div><span class="prep-num">{{ myHistory().length }}</span><span class="st-muted small">attempts</span></div>
        <div><span class="prep-num">{{ avg() === null ? '-' : avg() + '%' }}</span><span class="st-muted small">average</span></div>
      </div>
    </section>
    @if (!desktop) {
      <div class="alert alert-warning small d-flex gap-2 align-items-start mb-3" role="status">
        <i class="bi bi-laptop mt-1" aria-hidden="true"></i>
        <span>Mock tests need a <strong>laptop or desktop</strong> (Chrome, Edge or Firefox): you share your screen during the test, and phones and tablets cannot do that. You can still see your past results here.</span>
      </div>
    } @else {
      <p class="small st-muted mb-3"><i class="bi bi-shield-check me-1" aria-hidden="true"></i>Tests are proctored: you share your entire screen, and every time you leave the
        test a screenshot is saved with the time. Two warnings, then the test is submitted automatically.</p>
    }


    @if (loading()) { <pm-skeleton [rows]="6" /> } @else {
      @if (tab() === 'mock' && ladder().length) {
        <section class="mb-4" aria-labelledby="lad-title">
          <div class="d-flex align-items-end justify-content-between gap-2 flex-wrap mb-2">
            <div>
              <h2 id="lad-title" class="h6 st-h mb-0">Placement test path</h2>
              <p class="small st-muted mb-0">From mass-hiring patterns to product companies. Score more than {{ ladder()[0].unlockPercent }}% in a test to open the next one.</p>
            </div>
            <div class="lad-progress small" aria-label="Progress">
              <strong class="st-h">{{ unlockedCount() }}</strong><span class="st-muted"> of {{ ladder().length }} open</span>
              <div class="lad-bar" aria-hidden="true"><span [style.width.%]="unlockedCount() * 100 / ladder().length"></span></div>
            </div>
          </div>
          @for (g of levels(); track g.level) {
            <h3 class="lad-level" [attr.data-level]="g.level">{{ levelLabel[g.level] }}</h3>
            <ol class="lad-list">
              @for (t of g.tests; track t.id) {
                <li class="lad-step" [class.locked]="t.locked" [class.done]="passed(t)" [class.current]="!t.locked && !passed(t)" [attr.data-order]="t.order">
                  <span class="lad-num" aria-hidden="true">
                    @if (t.locked) { <i class="bi bi-lock-fill"></i> } @else if (passed(t)) { <i class="bi bi-check-lg"></i> } @else { {{ t.order }} }
                  </span>
                  <div class="lad-body min-w-0">
                    <div class="d-flex align-items-center gap-2 flex-wrap">
                      <span class="fw-semibold st-h">{{ t.order }}. {{ t.title }}</span>
                      @if (t.companyTag) { <span class="st-chip">{{ t.companyTag }}</span> }
                    </div>
                    <div class="prep-meta mt-1">
                      <span><i class="bi bi-clock"></i> {{ t.durationMinutes }} min</span>
                      @if (t.questionCount - t.codingCount) { <span><i class="bi bi-ui-checks"></i> {{ t.questionCount - t.codingCount }} MCQ</span> }
                      @if (t.codingCount) { <span><i class="bi bi-code-slash"></i> {{ t.codingCount }} coding</span> }
                      <span class="d-none d-sm-inline">{{ t.sections.join(' · ') }}</span>
                    </div>
                    @if (t.locked) {
                      <div class="small lad-hint mt-1"><i class="bi bi-lock"></i> Score more than {{ t.unlockPercent }}% in "{{ t.unlockAfter }}" to unlock</div>
                    }
                  </div>
                  <div class="lad-actions">
                    @if (t.bestPercent !== null && t.bestPercent !== undefined) {
                      <span class="lad-best" [class.good]="t.passed">{{ t.bestPercent }}%</span>
                    }
                    @if (t.lastAttemptId) { <a class="btn btn-sm btn-outline-secondary d-none d-md-inline-block" [routerLink]="['/student/prep/result', t.lastAttemptId]">Result</a> }
                    @if (t.inProgressAttemptId) {
                      <button class="btn btn-sm btn-primary" (click)="open(t, true)"><i class="bi bi-play-fill"></i> Resume</button>
                    } @else if (t.locked) {
                      <button class="btn btn-sm btn-outline-secondary" disabled [attr.aria-label]="'Locked: ' + t.title"><i class="bi bi-lock-fill"></i> Locked</button>
                    } @else {
                      <button class="btn btn-sm btn-primary" (click)="open(t, false)">{{ t.attempts ? 'Retake' : 'Start' }}</button>
                    }
                  </div>
                </li>
              }
            </ol>
          }
        </section>
        @if (others().length) {
          <h2 class="h6 st-h mb-0">More practice tests</h2>
          <p class="small st-muted mb-2">Standalone tests, open to take any time.</p>
        }
      }
      @if (tab() === 'college' && others().length) {
        <h2 class="h6 st-h mb-0">From your college</h2>
        <p class="small st-muted mb-2">Exams run at a fixed time and can be taken once; practice tests are open any time.</p>
      }
      <div class="row g-3">
        @for (t of others(); track t.id) {
          <div class="col-md-6 col-xl-4"><article class="st-card h-100 d-flex flex-column prep-card" [class.prep-exam]="t.mode === 'EXAM'">
            <div class="d-flex justify-content-between gap-2 align-items-start">
              <h3 class="h6 st-h mb-1">{{ t.title }}</h3>
              @if (t.mode === 'EXAM') {
                <span class="st-chip prep-st prep-st-{{ statusOf(t).toLowerCase() }}">{{ examLabel[statusOf(t)] }}</span>
              } @else if (t.companyTag) { <span class="st-chip">{{ t.companyTag }}</span> }
            </div>
            @if (t.mode === 'EXAM') {
              <p class="small mb-2 prep-window"><i class="bi bi-calendar-event me-1"></i>{{ t.startsAt | date: 'd MMM, h:mm a' }} – {{ t.endsAt | date: 'd MMM, h:mm a' }}
                <span class="st-muted d-block">One attempt only{{ t.resultsReleased ? '' : ' · results published later by your college' }}</span></p>
            }
            @if (t.description) { <p class="small st-muted mb-2 prep-desc">{{ t.description }}</p> }
            <div class="prep-meta">
              <span><i class="bi bi-clock"></i> {{ t.durationMinutes }} min</span>
              <span><i class="bi bi-ui-checks"></i> {{ t.questionCount - t.codingCount }} question{{ t.questionCount - t.codingCount === 1 ? '' : 's' }}</span>
              @if (t.codingCount) { <span><i class="bi bi-code-slash"></i> {{ t.codingCount }} coding</span> }
              <span><i class="bi bi-award"></i> {{ t.totalMarks }} marks</span>
            </div>
            <div class="prep-sections">@for (s of t.sections; track s) { <span>{{ s }}</span> }</div>
            <div class="d-flex align-items-center gap-2 mt-auto pt-3 flex-wrap">
              @if (t.bestScore !== null && t.bestScore !== undefined) {
                <span class="small"><span class="st-muted">Best&nbsp;</span><strong>{{ t.bestScore | number: '1.0-1' }}/{{ t.totalMarks }}</strong></span>
              }
              @if (t.lastAttemptId) {
                <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/student/prep/result', t.lastAttemptId]">{{ t.resultsReleased === false ? 'Submitted' : (t.mode === 'EXAM' ? 'Result' : 'Last result') }}</a>
              }
              @if (t.inProgressAttemptId) {
                <button class="btn btn-sm btn-primary ms-auto" (click)="open(t, true)"><i class="bi bi-play-fill"></i> Resume</button>
              } @else if (t.mode === 'EXAM') {
                @if (t.attempts) { <span class="small st-muted ms-auto"><i class="bi bi-check2-circle me-1"></i>{{ t.resultsReleased ? 'Taken' : 'Taken · result pending' }}</span> }
                @else if (statusOf(t) === 'UPCOMING') {
                  <button class="btn btn-sm btn-outline-secondary ms-auto" disabled><i class="bi bi-lock me-1"></i>{{ opensIn(t) }}</button>
                }
                @else if (statusOf(t) === 'CLOSED') { <span class="small text-danger ms-auto">Closed · not taken</span> }
                @else { <button class="btn btn-sm btn-primary ms-auto" (click)="open(t, false)"><i class="bi bi-pencil-square me-1"></i>Start exam</button> }
              } @else {
                <button class="btn btn-sm btn-primary ms-auto" (click)="open(t, false)">{{ t.attempts ? 'Retake' : 'Start test' }}</button>
              }
            </div>
          </article></div>
        } @empty {
          @if (tab() === 'college') {
            <div class="col-12"><pm-empty-state icon="bi-building" title="No college tests yet" text="Exams and practice tests from your college appear here as soon as they are scheduled." /></div>
          } @else if (!ladder().length) {
            <div class="col-12"><pm-empty-state icon="bi-journal-code" title="No mock tests yet" text="New practice tests will appear here as soon as they are published." /></div>
          }
        }
      </div>

      @if (myHistory().length) {
        <h2 class="h6 st-h mt-4 mb-2">My attempts</h2>
        @for (h of myHistory(); track h.attemptId) {
          <a class="st-row text-decoration-none prep-hist" [routerLink]="['/student/prep/result', h.attemptId]">
            <span class="flex-grow-1 small"><strong class="st-h">{{ h.title }}</strong><br><span class="st-muted">{{ h.submittedAt | date: 'medium' }}</span>
              @if (h.autoSubmitReason === 'TAB_SWITCH') { <span class="st-chip text-danger ms-1"><i class="bi bi-exclamation-octagon"></i> Auto-submitted: left the test</span> }
              @else if (h.tabSwitches) { <span class="st-chip ms-1" style="color:#b45309"><i class="bi bi-exclamation-triangle"></i> {{ h.tabSwitches }} warning{{ h.tabSwitches === 1 ? '' : 's' }}</span> }</span>
            @if (h.released === false) { <span class="small st-muted">Result pending</span> }
            @else {
              <span class="prep-score" [class.good]="pct(h) >= 70" [class.mid]="pct(h) >= 40 && pct(h) < 70">{{ pct(h) }}%</span>
              <span class="small st-muted">{{ h.score | number: '1.0-1' }}/{{ h.maxScore }}</span>
            }
            <i class="bi bi-chevron-right st-muted"></i>
          </a>
        }
      }
    }
    @if (precheck()) {
      <pm-proctor-precheck startLabel="Share screen and start" (confirmed)="precheckDone(true)" (cancelled)="precheckDone(false)" />
    }`,
  styles: [`
    .prep-hero { display: flex; gap: 1.25rem; align-items: center; flex-wrap: wrap;
      background: linear-gradient(120deg, rgba(11,92,255,.10), rgba(0,180,255,.06)), var(--pm-card-bg); }
    .prep-hero > div:first-child { flex: 1 1 320px; }
    .prep-hero-stats { display: flex; gap: 1.5rem; }
    .prep-hero-stats > div { display: grid; text-align: center; }
    .prep-num { font-size: 1.5rem; font-weight: 800; color: var(--pm-heading); line-height: 1.1; font-variant-numeric: tabular-nums; }
    .prep-card { transition: transform .15s, box-shadow .15s; }
    .prep-exam { border-left: 3px solid #4f46e5; }
    .prep-window { color: var(--pm-heading); }
    .prep-st { font-weight: 700; }
    .prep-st-live { background: rgba(22,163,74,.12); color: #15803d; }
    .prep-st-upcoming { background: rgba(79,70,229,.12); color: #4338ca; }
    .prep-st-closed { background: var(--pm-soft); color: var(--pm-muted); }
    .prep-card:hover { transform: translateY(-2px); box-shadow: var(--pm-card-shadow-hover); }
    .prep-desc { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .prep-meta { display: flex; flex-wrap: wrap; gap: .4rem .9rem; font-size: .8rem; color: var(--pm-label); }
    .prep-meta i { color: var(--pm-link); margin-right: .2rem; }
    .prep-sections { display: flex; flex-wrap: wrap; gap: .3rem; margin-top: .6rem; }
    .prep-sections span { font-size: .7rem; padding: .15rem .5rem; border-radius: 6px; border: 1px solid var(--pm-card-border); color: var(--pm-muted); }
    .prep-hist:hover { border-color: var(--pm-link); }
    .prep-score { font-weight: 800; font-variant-numeric: tabular-nums; color: #e11d48; min-width: 3rem; text-align: right; }
    .prep-score.mid { color: #d97706; } .prep-score.good { color: #16a34a; }
    .lad-progress { min-width: 9rem; text-align: right; }
    .lad-bar { height: 6px; border-radius: 99px; background: var(--pm-soft); margin-top: .25rem; overflow: hidden; }
    .lad-bar span { display: block; height: 100%; background: linear-gradient(90deg, #16a34a, #0b5cff); border-radius: 99px; }
    .lad-level { font-size: .72rem; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; margin: 1rem 0 .4rem; }
    .lad-level[data-level='EASY'] { color: #16a34a; } .lad-level[data-level='MEDIUM'] { color: #d97706; } .lad-level[data-level='HARD'] { color: #e11d48; }
    .lad-list { list-style: none; padding: 0; margin: 0; display: grid; gap: .5rem; }
    .lad-step { display: flex; gap: .85rem; align-items: center; padding: .75rem .9rem; border-radius: 12px; border: 1px solid var(--pm-card-border);
      background: var(--pm-card-bg); }
    .lad-step.current { border-color: var(--pm-link); box-shadow: 0 0 0 3px rgba(11,92,255,.08); }
    .lad-step.locked { opacity: .72; }
    .lad-num { width: 2.1rem; height: 2.1rem; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; font-weight: 800; font-size: .85rem;
      background: var(--pm-soft); color: var(--pm-label); }
    .lad-step.current .lad-num { background: #0b5cff; color: #fff; }
    .lad-step.done .lad-num { background: #16a34a; color: #fff; }
    .lad-body { flex: 1 1 auto; }
    .lad-hint { color: var(--pm-muted); }
    .lad-actions { display: flex; align-items: center; gap: .5rem; flex-shrink: 0; }
    .lad-best { font-weight: 800; font-variant-numeric: tabular-nums; color: #d97706; }
    .lad-best.good { color: #16a34a; }
    @media (max-width: 575px) { .lad-step { flex-wrap: wrap; } .lad-actions { width: 100%; justify-content: flex-end; } }
  `],
})
export class PrepHome implements OnInit {
  private prep = inject(PrepService);
  private router = inject(Router);
  private confirm = inject(ConfirmService);
  private screen = inject(ScreenProctor);
  private toast = inject(ToastService);
  private camera = inject(CameraProctor);
  private destroyRef = inject(DestroyRef);
  /** Camera + microphone check shown before a proctored test starts (resolves true when the student continues). */
  precheck = signal(false);
  private precheckAnswer: ((ok: boolean) => void) | null = null;
  /** Screen sharing (and so a mock test) needs a laptop or desktop browser. */
  readonly desktop = ScreenProctor.supported();
  loading = signal(true);
  tests = signal<TestCard[]>([]);
  history = signal<AttemptHistory[]>([]);
  readonly levelLabel: Record<string, string> = { EASY: 'Easy · mass hiring', MEDIUM: 'Medium', HARD: 'Hard · product companies' };
  ladder = computed(() => this.tests().filter(t => t.order != null));
  /** Mock tests (platform) or College tests (the college's exams and practice tests), from the route. */
  tab = signal<'mock' | 'college'>((inject(ActivatedRoute).snapshot.data['tab'] as 'college' | undefined) ?? 'mock');
  others = computed(() => this.tests().filter(t => t.order == null && !!t.collegeTest === (this.tab() === 'college')));
  tabTests = computed(() => this.tests().filter(t => !!t.collegeTest === (this.tab() === 'college')));
  liveExams = computed(() => this.tests().filter(t => t.mode === 'EXAM' && this.statusOf(t) === 'LIVE' && !t.attempts && !t.inProgressAttemptId).length);
  /** Ticks every second, so an exam opens (and closes) on time without a reload. */
  private now = signal(Date.now());
  /** Attempts of the tests on this tab. */
  myHistory = computed(() => {
    const college = new Set(this.tests().filter(t => t.collegeTest).map(t => t.id));
    return this.history().filter(h => college.has(h.testId) === (this.tab() === 'college'));
  });
  readonly examLabel: Record<string, string> = { UPCOMING: 'Upcoming', LIVE: 'Live now', CLOSED: 'Closed' };
  unlockedCount = computed(() => this.ladder().filter(t => !t.locked).length);
  /** Ladder tests grouped by level, keeping the path order. */
  levels = computed(() => {
    const out: { level: string; tests: TestCard[] }[] = [];
    for (const t of this.ladder()) {
      const level = t.level ?? 'MEDIUM';
      if (out.at(-1)?.level !== level) out.push({ level, tests: [] });
      out.at(-1)!.tests.push(t);
    }
    return out;
  });
  avg = computed(() => {
    const h = this.myHistory().filter(x => x.released !== false);
    return h.length ? Math.round(h.reduce((s, x) => s + this.pct(x), 0) / h.length) : null;
  });

  ngOnInit(): void {
    this.reload();
    let lastSync = Date.now();
    const timer = setInterval(() => {
      this.now.set(Date.now());
      // the server decides: sync when an exam's start or end has just passed on this clock, and every minute anyway
      const crossed = this.tests().some(t => t.mode === 'EXAM' && t.examStatus && this.statusOf(t) !== t.examStatus);
      if ((crossed && Date.now() - lastSync > 5000) || Date.now() - lastSync > 60000) { lastSync = Date.now(); this.reload(); }
    }, 1000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private reload(): void {
    forkJoin({ t: this.prep.tests(), h: this.prep.history() }).subscribe({
      next: r => { this.tests.set(r.t); this.history.set(r.h); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  /** UPCOMING / LIVE / CLOSED on this computer's clock (the server checks again when the student starts). */
  statusOf(t: TestCard): 'UPCOMING' | 'LIVE' | 'CLOSED' {
    const now = this.now();
    if (t.startsAt && now < new Date(t.startsAt).getTime()) return 'UPCOMING';
    if (t.endsAt && now >= new Date(t.endsAt).getTime()) return 'CLOSED';
    return 'LIVE';
  }

  /** "Opens in 4m 05s" in the last hour, else the date and time. */
  opensIn(t: TestCard): string {
    const ms = new Date(t.startsAt!).getTime() - this.now();
    if (ms > 3600_000) return 'Opens ' + new Date(t.startsAt!).toLocaleString([], { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
    const s = Math.max(0, Math.ceil(ms / 1000));
    return `Opens in ${Math.floor(s / 60)}m ${String(s % 60).padStart(2, '0')}s`;
  }

  passed(t: TestCard): boolean { return t.passed; }

  pct(h: AttemptHistory): number { return h.maxScore && h.score !== null && h.score !== undefined ? Math.round(h.score * 100 / h.maxScore) : 0; }

  async open(t: TestCard, resume: boolean): Promise<void> {
    if (!this.desktop) { this.toast.error(SHARE_PROBLEM_TEXT.UNSUPPORTED); return; }
    if (!resume) {
      const r = await this.confirm.ask({
        title: `Start "${t.title}"?`,
        message: `You will share your ENTIRE screen, then the test opens in full screen and the ${t.durationMinutes}-minute timer starts. `
          + `Do not leave the test: each time you switch tab or app a screenshot is saved with the time. Two warnings, and the third time `
          + `the test is submitted automatically. Answers save automatically, and the test submits itself when time is up.`
          + (t.aiProctoring ? ` Your camera and microphone also stay on: you turn them on and agree on the next screen.` : '')
          + (t.mode === 'EXAM' ? ` This is an exam: you can take it only once, and it closes at ${new Date(t.endsAt!).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}.` : ''),
        confirmText: t.aiProctoring ? 'Continue' : 'Share screen and start',
      });
      if (!r.ok) return;
    }
    // Camera + microphone before the screen share and the timer: their permission dialogs must not appear during the test.
    if (t.aiProctoring && !(await this.askCamera())) { this.camera.stop(); return; }
    // Share before the attempt starts, so a refused share does not start the timer. Must run inside the click.
    try {
      await this.screen.start();
    } catch (p) {
      this.camera.stop();
      this.toast.error(SHARE_PROBLEM_TEXT[p as ShareProblem] ?? SHARE_PROBLEM_TEXT.DENIED);
      return;
    }
    void enterFullscreen();
    this.prep.start(t.id).subscribe({
      next: a => {
        if (!t.aiProctoring) { this.router.navigate(['/student/test', a.attemptId]); return; }
        // consent is kept on the attempt; if this call fails the test page asks again
        this.prep.consent(a.attemptId).subscribe({
          next: () => this.router.navigate(['/student/test', a.attemptId]),
          error: () => this.router.navigate(['/student/test', a.attemptId]),
        });
      },
      error: () => { exitFullscreen(); this.screen.stop(); this.camera.stop(); },
    });
  }

  private askCamera(): Promise<boolean> {
    this.precheck.set(true);
    return new Promise(res => this.precheckAnswer = res);
  }

  /** From the check screen: Continue is a click, so the screen share that follows is still allowed by the browser. */
  precheckDone(ok: boolean): void {
    this.precheck.set(false);
    this.precheckAnswer?.(ok);
    this.precheckAnswer = null;
  }
}
