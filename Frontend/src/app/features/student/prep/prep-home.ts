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
      <div class="prep-hero-main min-w-0">
        <span class="prep-eyebrow"><i class="bi" [class.bi-building-check]="tab() === 'college'" [class.bi-journal-code]="tab() !== 'college'" aria-hidden="true"></i>{{ tab() === 'college' ? 'College tests' : 'Mock tests' }}</span>
        @if (tab() === 'college') {
          <h2 class="h5 st-h mb-1">Tests and exams from your college</h2>
          <p class="small st-muted mb-0">Sessional exams run in a fixed time window and can be taken once; practice tests from your placement cell are open any time.</p>
        } @else {
          <h2 class="h5 st-h mb-1">Practise like the real test</h2>
          <p class="small st-muted mb-0">Timed mock tests with aptitude, reasoning, verbal, technical MCQs and real coding questions. Take one a day or two before your drive.</p>
        }
        @if (desktop) {
          <p class="prep-proctor"><i class="bi bi-shield-check" aria-hidden="true"></i><span>Tests are proctored: you share your entire screen, and every time you leave the
            test a screenshot is saved with the time. Two warnings, then the test is submitted automatically.</span></p>
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
    }

    @if (loading()) { <pm-skeleton [rows]="6" /> } @else {
      <!-- My attempts: collapsed until the student opens it (native <details>, no extra state) -->
      @if (myHistory().length) {
        <details class="prep-attempts mb-3">
          <summary>
            <span class="prep-attempts-icon" aria-hidden="true"><i class="bi bi-clock-history"></i></span>
            <span class="flex-grow-1 min-w-0">
              <strong class="st-h d-block">My attempts</strong>
              <span class="small st-muted">{{ myHistory().length }} attempt{{ myHistory().length === 1 ? '' : 's' }}@if (avg() !== null) { · average {{ avg() }}% }</span>
            </span>
            <span class="prep-attempts-btn"><span class="prep-when-closed">View attempts</span><span class="prep-when-open">Hide</span><i class="bi bi-chevron-down" aria-hidden="true"></i></span>
          </summary>
          <div class="prep-attempts-list">
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
          </div>
        </details>
      }

      @if (tab() === 'mock' && ladder().length) {
        <section class="prep-path st-card mb-4" aria-labelledby="lad-title">
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
    }
    @if (precheck()) {
      <pm-proctor-precheck startLabel="Share screen and start" (confirmed)="precheckDone(true)" (cancelled)="precheckDone(false)" />
    }`,
  styles: [`
    :host { display: block; }

    /* ---------- hero ---------- */
    .st-card.prep-hero { position: relative; overflow: hidden; display: flex; gap: 1.5rem; align-items: center; flex-wrap: wrap; padding: 1.6rem 1.75rem;
      border-color: var(--pm-accent-ring, #e2e8f0);
      background: radial-gradient(120% 140% at 100% 0%, rgba(99,102,241,.13), transparent 55%), linear-gradient(120deg, rgba(79,70,229,.05), transparent 60%), var(--pm-card-bg); }
    .prep-hero-main { flex: 1 1 360px; }
    .prep-eyebrow { display: inline-flex; align-items: center; gap: .4rem; margin-bottom: .65rem; padding: .25rem .65rem; border-radius: 999px;
      font-size: .68rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--pm-accent-600, #4338ca); background: var(--pm-accent-soft, rgba(79,70,229,.08)); }
    .prep-hero h2 { font-size: 1.35rem; letter-spacing: -.02em; }
    .prep-proctor { display: flex; gap: .55rem; align-items: flex-start; max-width: 640px; margin: 1rem 0 0; padding: .6rem .8rem; border-radius: 12px;
      font-size: .8rem; line-height: 1.5; color: var(--pm-label); background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); }
    .prep-proctor i { color: #16a34a; font-size: .95rem; margin-top: .05rem; }
    .prep-hero-stats { display: grid; grid-template-columns: repeat(3, minmax(88px, 1fr)); gap: .6rem; }
    .prep-hero-stats > div { display: grid; gap: .1rem; text-align: center; padding: .8rem .9rem; border-radius: 14px; background: var(--pm-card-bg);
      border: 1px solid var(--pm-card-border); box-shadow: var(--pm-card-shadow); }
    .prep-hero-stats .small { font-size: .66rem; font-weight: 700; letter-spacing: .07em; text-transform: uppercase; }
    .prep-num { font-size: 1.6rem; font-weight: 800; line-height: 1.1; letter-spacing: -.02em; color: var(--pm-heading); font-variant-numeric: tabular-nums; }

    /* ---------- my attempts (collapsible) ---------- */
    .prep-attempts { background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); border-radius: 16px; box-shadow: var(--pm-card-shadow); overflow: hidden;
      transition: box-shadow .25s ease; }
    .prep-attempts[open] { box-shadow: var(--pm-card-shadow-hover); }
    .prep-attempts > summary { list-style: none; display: flex; align-items: center; gap: .85rem; padding: .85rem 1.1rem; cursor: pointer; user-select: none;
      transition: background-color .16s ease; }
    .prep-attempts > summary::-webkit-details-marker { display: none; }
    .prep-attempts > summary:hover { background: var(--pm-surface-2, #fafbfd); }
    .prep-attempts > summary:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: -2px; }
    .prep-attempts-icon { width: 2.5rem; height: 2.5rem; border-radius: 12px; display: grid; place-items: center; flex-shrink: 0; font-size: 1.1rem;
      color: var(--pm-accent, #4f46e5); background: var(--pm-accent-soft, rgba(79,70,229,.08)); }
    .prep-attempts-btn { display: inline-flex; align-items: center; gap: .45rem; padding: .45rem .95rem; border-radius: 999px; flex-shrink: 0;
      font-size: .8rem; font-weight: 600; color: var(--pm-heading); background: var(--pm-card-bg); border: 1px solid var(--pm-card-border);
      box-shadow: 0 1px 2px rgba(16,24,40,.05); transition: background-color .16s ease, border-color .16s ease; }
    .prep-attempts > summary:hover .prep-attempts-btn { border-color: var(--pm-input-hover); background: var(--pm-soft); }
    .prep-attempts-btn i { transition: transform .25s ease; }
    .prep-attempts[open] .prep-attempts-btn i { transform: rotate(180deg); }
    .prep-when-open { display: none; }
    .prep-attempts[open] .prep-when-open { display: inline; }
    .prep-attempts[open] .prep-when-closed { display: none; }
    .prep-attempts-list { display: grid; gap: .5rem; padding: 1rem; border-top: 1px solid var(--pm-divider); background: var(--pm-surface-2, #fafbfd); }
    .prep-attempts[open] .prep-attempts-list { animation: prepOpen .28s cubic-bezier(.2,.8,.2,1); }
    .prep-attempts-list .st-row { margin-bottom: 0; }
    @keyframes prepOpen { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: none; } }

    /* ---------- placement test path ---------- */
    .st-card.prep-path { padding: 1.4rem 1.5rem; }
    .lad-progress { min-width: 10rem; text-align: right; }
    .lad-bar { height: 7px; border-radius: 99px; background: var(--pm-soft); margin-top: .35rem; overflow: hidden; }
    .lad-bar span { display: block; height: 100%; background: linear-gradient(90deg, #22c55e, #6366f1); border-radius: 99px; }
    .lad-level { display: flex; align-items: center; gap: .5rem; font-size: .7rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; margin: 1.35rem 0 .65rem; }
    .lad-level::before { content: ''; width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
    .lad-level::after { content: ''; flex: 1; height: 1px; background: var(--pm-divider); }
    .lad-level[data-level='EASY'] { color: #16a34a; } .lad-level[data-level='MEDIUM'] { color: #d97706; } .lad-level[data-level='HARD'] { color: #e11d48; }
    .lad-list { list-style: none; padding: 0; margin: 0; display: grid; gap: .6rem; }
    .lad-step { position: relative; display: flex; gap: .9rem; align-items: center; padding: .85rem 1rem; border-radius: 14px; border: 1px solid var(--pm-card-border);
      background: var(--pm-card-bg); transition: border-color .16s ease, box-shadow .25s ease; }
    /* connector between consecutive steps */
    .lad-step:not(:last-child)::after { content: ''; position: absolute; left: calc(1rem + 1.1rem - 1px); top: 100%; width: 2px; height: .6rem; background: var(--pm-divider); }
    .lad-step.done:not(:last-child)::after { background: rgba(22,163,74,.45); }
    .lad-step:hover:not(.locked) { border-color: var(--pm-input-border); box-shadow: var(--pm-card-shadow-hover); }
    .lad-step.current { border-color: var(--pm-accent, #4f46e5); box-shadow: 0 0 0 3px var(--pm-accent-soft, rgba(79,70,229,.08));
      background: linear-gradient(90deg, var(--pm-accent-soft, rgba(79,70,229,.08)), transparent 55%), var(--pm-card-bg); }
    .lad-step.locked { background: var(--pm-surface-2, #fafbfd); }
    .lad-step.locked .lad-body, .lad-step.locked .lad-num { opacity: .6; }
    .lad-num { width: 2.2rem; height: 2.2rem; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; font-weight: 800; font-size: .85rem;
      background: var(--pm-soft); color: var(--pm-label); }
    .lad-step.current .lad-num { background: var(--pm-accent-grad, #4f46e5); color: #fff; box-shadow: 0 4px 10px -3px rgba(79,70,229,.55); }
    .lad-step.done .lad-num { background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; }
    .lad-body { flex: 1 1 auto; }
    .lad-hint { color: var(--pm-muted); }
    .lad-actions { display: flex; align-items: center; gap: .5rem; flex-shrink: 0; }
    .lad-best { padding: .2rem .6rem; border-radius: 999px; font-weight: 800; font-size: .8rem; font-variant-numeric: tabular-nums;
      color: var(--pm-warn, #b45309); background: var(--pm-warn-soft, rgba(217,119,6,.12)); }
    .lad-best.good { color: var(--pm-ok, #15803d); background: var(--pm-ok-soft, rgba(22,163,74,.1)); }
    @media (max-width: 575px) { .lad-step { flex-wrap: wrap; } .lad-actions { width: 100%; justify-content: flex-end; } .lad-step:not(:last-child)::after { display: none; } }

    /* ---------- test cards ---------- */
    .prep-card { position: relative; overflow: hidden; transition: box-shadow .25s ease, border-color .16s ease; }
    .prep-card::before { content: ''; position: absolute; inset: 0 0 auto 0; height: 3px; background: var(--pm-accent-grad, #4f46e5); opacity: 0; transition: opacity .25s ease; }
    .prep-card:hover { box-shadow: var(--pm-card-shadow-hover); border-color: var(--pm-accent-ring, #c7d2fe); }
    .prep-card:hover::before { opacity: 1; }
    .prep-exam { border-left: 3px solid var(--pm-accent, #4f46e5); }
    .prep-window { color: var(--pm-heading); padding: .5rem .65rem; border-radius: 10px; background: var(--pm-surface-2, #fafbfd); border: 1px solid var(--pm-divider); }
    .prep-st { font-weight: 700; }
    .prep-st-live { position: relative; padding-left: 1.35rem !important; background: rgba(22,163,74,.12); color: #15803d; }
    .prep-st-live::before { content: ''; position: absolute; left: .6rem; top: 50%; width: 6px; height: 6px; margin-top: -3px; border-radius: 50%; background: currentColor;
      animation: prepLive 1.6s ease-in-out infinite; }
    .prep-st-upcoming { background: rgba(79,70,229,.12); color: #4338ca; }
    .prep-st-closed { background: var(--pm-soft); color: var(--pm-muted); }
    @keyframes prepLive { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
    .prep-desc { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .prep-meta { display: flex; flex-wrap: wrap; gap: .4rem .9rem; font-size: .8rem; color: var(--pm-label); }
    .prep-meta i { color: var(--pm-accent, var(--pm-link)); margin-right: .2rem; }
    .prep-sections { display: flex; flex-wrap: wrap; gap: .3rem; margin-top: .65rem; }
    .prep-sections span { font-size: .7rem; padding: .15rem .5rem; border-radius: 6px; border: 1px solid var(--pm-divider); background: var(--pm-surface-2, #fafbfd); color: var(--pm-muted); }
    .prep-card .mt-auto.pt-3 { border-top: 1px dashed var(--pm-divider); margin-top: 1rem !important; }

    /* ---------- attempt rows ---------- */
    .prep-hist { background: var(--pm-card-bg); }
    .prep-hist:hover { border-color: var(--pm-accent, var(--pm-link)) !important; }
    .prep-score { padding: .2rem .6rem; border-radius: 999px; min-width: 3.6rem; text-align: center; font-weight: 800; font-variant-numeric: tabular-nums;
      color: #e11d48; background: rgba(225,29,72,.09); }
    .prep-score.mid { color: #d97706; background: rgba(217,119,6,.12); } .prep-score.good { color: #16a34a; background: rgba(22,163,74,.1); }

    @media (prefers-reduced-motion: reduce) { .prep-attempts[open] .prep-attempts-list, .prep-st-live::before { animation: none; } }
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
