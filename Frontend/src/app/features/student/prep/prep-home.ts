import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AttemptHistory, PrepService, TestCard } from '../../../services/prep.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { EmptyState, Skeleton } from '../../../shared/ui/states';

/** Preparation tab: mock tests to practise before a drive, and past attempts. */
@Component({
  selector: 'pm-prep-home',
  imports: [DatePipe, DecimalPipe, RouterLink, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <section class="prep-hero st-card mb-3">
      <div class="min-w-0">
        <h2 class="h5 st-h mb-1">Practise like the real test</h2>
        <p class="small st-muted mb-0">Timed mock tests with aptitude, reasoning, verbal, technical MCQs and real coding questions. Take one a day or two before your drive.</p>
      </div>
      <div class="prep-hero-stats">
        <div><span class="prep-num">{{ tests().length }}</span><span class="st-muted small">tests</span></div>
        <div><span class="prep-num">{{ history().length }}</span><span class="st-muted small">attempts</span></div>
        <div><span class="prep-num">{{ avg() === null ? '-' : avg() + '%' }}</span><span class="st-muted small">average</span></div>
      </div>
    </section>

    @if (loading()) { <pm-skeleton [rows]="6" /> } @else {
      <div class="row g-3">
        @for (t of tests(); track t.id) {
          <div class="col-md-6 col-xl-4"><article class="st-card h-100 d-flex flex-column prep-card">
            <div class="d-flex justify-content-between gap-2 align-items-start">
              <h3 class="h6 st-h mb-1">{{ t.title }}</h3>
              @if (t.companyTag) { <span class="st-chip">{{ t.companyTag }}</span> }
            </div>
            @if (t.description) { <p class="small st-muted mb-2 prep-desc">{{ t.description }}</p> }
            <div class="prep-meta">
              <span><i class="bi bi-clock"></i> {{ t.durationMinutes }} min</span>
              <span><i class="bi bi-ui-checks"></i> {{ t.questionCount - t.codingCount }} MCQ</span>
              @if (t.codingCount) { <span><i class="bi bi-code-slash"></i> {{ t.codingCount }} coding</span> }
              <span><i class="bi bi-award"></i> {{ t.totalMarks }} marks</span>
            </div>
            <div class="prep-sections">@for (s of t.sections; track s) { <span>{{ s }}</span> }</div>
            <div class="d-flex align-items-center gap-2 mt-auto pt-3 flex-wrap">
              @if (t.bestScore !== null && t.bestScore !== undefined) {
                <span class="small"><span class="st-muted">Best&nbsp;</span><strong>{{ t.bestScore | number: '1.0-1' }}/{{ t.totalMarks }}</strong></span>
              }
              @if (t.lastAttemptId) { <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/student/prep/result', t.lastAttemptId]">Last result</a> }
              @if (t.inProgressAttemptId) {
                <button class="btn btn-sm btn-primary ms-auto" (click)="open(t, true)"><i class="bi bi-play-fill"></i> Resume</button>
              } @else {
                <button class="btn btn-sm btn-primary ms-auto" (click)="open(t, false)">{{ t.attempts ? 'Retake' : 'Start test' }}</button>
              }
            </div>
          </article></div>
        } @empty {
          <div class="col-12"><pm-empty-state icon="bi-journal-code" title="No mock tests yet" text="New practice tests will appear here as soon as they are published." /></div>
        }
      </div>

      @if (history().length) {
        <h2 class="h6 st-h mt-4 mb-2">My attempts</h2>
        @for (h of history(); track h.attemptId) {
          <a class="st-row text-decoration-none prep-hist" [routerLink]="['/student/prep/result', h.attemptId]">
            <span class="flex-grow-1 small"><strong class="st-h">{{ h.title }}</strong><br><span class="st-muted">{{ h.submittedAt | date: 'medium' }}</span></span>
            <span class="prep-score" [class.good]="pct(h) >= 70" [class.mid]="pct(h) >= 40 && pct(h) < 70">{{ pct(h) }}%</span>
            <span class="small st-muted">{{ h.score | number: '1.0-1' }}/{{ h.maxScore }}</span>
            <i class="bi bi-chevron-right st-muted"></i>
          </a>
        }
      }
    }`,
  styles: [`
    .prep-hero { display: flex; gap: 1.25rem; align-items: center; flex-wrap: wrap;
      background: linear-gradient(120deg, rgba(11,92,255,.10), rgba(0,180,255,.06)), var(--pm-card-bg); }
    .prep-hero > div:first-child { flex: 1 1 320px; }
    .prep-hero-stats { display: flex; gap: 1.5rem; }
    .prep-hero-stats > div { display: grid; text-align: center; }
    .prep-num { font-size: 1.5rem; font-weight: 800; color: var(--pm-heading); line-height: 1.1; font-variant-numeric: tabular-nums; }
    .prep-card { transition: transform .15s, box-shadow .15s; }
    .prep-card:hover { transform: translateY(-2px); box-shadow: var(--pm-card-shadow-hover); }
    .prep-desc { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .prep-meta { display: flex; flex-wrap: wrap; gap: .4rem .9rem; font-size: .8rem; color: var(--pm-label); }
    .prep-meta i { color: var(--pm-link); margin-right: .2rem; }
    .prep-sections { display: flex; flex-wrap: wrap; gap: .3rem; margin-top: .6rem; }
    .prep-sections span { font-size: .7rem; padding: .15rem .5rem; border-radius: 6px; border: 1px solid var(--pm-card-border); color: var(--pm-muted); }
    .prep-hist:hover { border-color: var(--pm-link); }
    .prep-score { font-weight: 800; font-variant-numeric: tabular-nums; color: #e11d48; min-width: 3rem; text-align: right; }
    .prep-score.mid { color: #d97706; } .prep-score.good { color: #16a34a; }
  `],
})
export class PrepHome implements OnInit {
  private prep = inject(PrepService);
  private router = inject(Router);
  private confirm = inject(ConfirmService);
  loading = signal(true);
  tests = signal<TestCard[]>([]);
  history = signal<AttemptHistory[]>([]);
  avg = computed(() => {
    const h = this.history();
    return h.length ? Math.round(h.reduce((s, x) => s + this.pct(x), 0) / h.length) : null;
  });

  ngOnInit(): void {
    forkJoin({ t: this.prep.tests(), h: this.prep.history() }).subscribe({
      next: r => { this.tests.set(r.t); this.history.set(r.h); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  pct(h: AttemptHistory): number { return h.maxScore ? Math.round(h.score * 100 / h.maxScore) : 0; }

  async open(t: TestCard, resume: boolean): Promise<void> {
    if (!resume) {
      const r = await this.confirm.ask({
        title: `Start "${t.title}"?`,
        message: `The ${t.durationMinutes}-minute timer starts now and keeps running even if you close the page. Answers save automatically, and the test submits itself when time is up.`,
        confirmText: 'Start test',
      });
      if (!r.ok) return;
    }
    this.prep.start(t.id).subscribe(a => this.router.navigate(['/student/test', a.attemptId]));
  }
}
