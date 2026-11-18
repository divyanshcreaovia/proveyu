import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AttemptResult, LANGUAGE_LABELS, PrepService, ProctorLog, QuestionResult } from '../services/prep.service';
import { ProctorLogView } from './proctor-log';
import { Skeleton } from '../../../shared/ui/states';

type Filter = 'all' | 'wrong' | 'right';

/** Result of a mock test: score ring, section breakdown and question-by-question review with explanations. */
@Component({
  selector: 'pm-prep-result',
  imports: [DatePipe, DecimalPipe, RouterLink, Skeleton, ProctorLogView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    @if (!r()) { <pm-skeleton [rows]="8" /> } @else {
      @let res = r()!;
      <a routerLink="/student/prep" class="small d-inline-block mb-2"><i class="bi bi-arrow-left"></i> Back to Preparation</a>
      <section class="st-card rs-hero mb-3">
        <svg viewBox="0 0 120 120" class="rs-ring" role="img" [attr.aria-label]="'Score ' + res.percent + ' percent'">
          <circle cx="60" cy="60" r="52" class="rs-track" />
          <circle cx="60" cy="60" r="52" class="rs-val" [class.mid]="res.percent >= 40 && res.percent < 70" [class.good]="res.percent >= 70"
            [attr.stroke-dasharray]="circ" [attr.stroke-dashoffset]="circ * (1 - res.percent / 100)" transform="rotate(-90 60 60)" />
          <text x="60" y="58" text-anchor="middle" class="rs-pct">{{ res.percent }}%</text>
          <text x="60" y="78" text-anchor="middle" class="rs-of">{{ res.score | number: '1.0-1' }} / {{ res.maxScore }}</text>
        </svg>
        <div class="min-w-0 flex-grow-1">
          <h2 class="h4 st-h mb-1">{{ res.title }}</h2>
          <p class="st-muted small mb-2">Submitted {{ res.submittedAt | date: 'medium' }} · time taken {{ duration(res.timeTakenSeconds) }}</p>
          <p class="mb-0 rs-msg">{{ message() }}</p>
        </div>
      </section>

      @if (proctor(); as pl) {
        <section class="st-card mb-3" aria-labelledby="rs-proctor">
          <h2 id="rs-proctor" class="h6 st-h mb-2"><i class="bi bi-shield-check me-1" aria-hidden="true"></i>Proctoring
            @if (pl.events.length) { <span class="st-chip ms-1" [class.text-danger]="pl.autoSubmitReason === 'TAB_SWITCH'">{{ pl.events.length }} time{{ pl.events.length === 1 ? '' : 's' }} left the test</span> }</h2>
          <pm-proctor-log [log]="pl" [image]="screenshot" />
        </section>
      }

      <div class="row g-3 mb-3">
        @for (s of res.sections; track s.name) {
          <div class="col-sm-6 col-xl-4"><div class="st-card rs-sec">
            <div class="d-flex justify-content-between align-items-baseline gap-2"><span class="fw-semibold st-h small">{{ s.name }}</span>
              <span class="small"><strong>{{ s.score | number: '1.0-1' }}</strong><span class="st-muted">/{{ s.max }}</span></span></div>
            <div class="rs-bar"><div [style.width.%]="s.max ? s.score * 100 / s.max : 0" [class.mid]="pct(s.score, s.max) >= 40 && pct(s.score, s.max) < 70" [class.good]="pct(s.score, s.max) >= 70"></div></div>
            <div class="small st-muted">{{ s.correct }} of {{ s.total }} fully correct</div>
          </div></div>
        }
      </div>

      <div class="d-flex align-items-center gap-2 mb-2 flex-wrap">
        <h2 class="h6 st-h mb-0 me-auto">Review answers</h2>
        <div class="btn-group btn-group-sm" role="group" aria-label="Filter questions">
          @for (f of filters; track f.key) {
            <button type="button" class="btn" [class.btn-primary]="filter() === f.key" [class.btn-outline-secondary]="filter() !== f.key" (click)="filter.set(f.key)">{{ f.label }}</button>
          }
        </div>
      </div>
      @for (q of shown(); track q.id) {
        <article class="st-card rs-q mb-2" [class.rs-ok]="full(q)" [class.rs-bad]="!full(q)">
          <div class="d-flex gap-2 align-items-center flex-wrap mb-2">
            <span class="rs-num">Q{{ number(q) }}</span><span class="st-chip">{{ q.section }}</span>
            <span class="ms-auto small fw-semibold" [class.text-success]="full(q)" [class.text-danger]="!full(q)">{{ q.marksAwarded | number: '1.0-1' }}/{{ q.marks }}</span>
          </div>
          @if (q.title) { <h3 class="h6 st-h">{{ q.title }}</h3> }
          <p class="st-pre small mb-2">{{ q.text }}</p>
          @if (q.type === 'MCQ') {
            <div class="rs-opts">
              @for (o of q.options; track $index) {
                <div class="rs-opt" [class.correct]="$index === q.correctOption" [class.wrong]="$index === q.yourOption && $index !== q.correctOption">
                  <span class="rs-letter">{{ letters[$index] }}</span><span class="flex-grow-1">{{ o }}</span>
                  @if ($index === q.correctOption) { <i class="bi bi-check-circle-fill" aria-label="Correct answer"></i> }
                  @else if ($index === q.yourOption) { <i class="bi bi-x-circle-fill" aria-label="Your answer"></i> }
                </div>
              }
            </div>
            @if (q.yourOption === null || q.yourOption === undefined) { <p class="small st-muted mt-2 mb-0">You did not answer this question.</p> }
          } @else {
            <p class="small mb-2"><strong>{{ q.testsPassed ?? 0 }} of {{ q.testsTotal }}</strong> hidden test cases passed
              @if (q.language) { <span class="st-muted"> · {{ langLabels[q.language] }}</span> }</p>
            @if (q.compileError) { <pre class="rs-code rs-err">{{ q.compileError }}</pre> }
            @if (q.code) { <details><summary class="small fw-semibold">Your code</summary><pre class="rs-code">{{ q.code }}</pre></details> }
            @else { <p class="small st-muted mb-0">You did not submit code for this question.</p> }
          }
          @if (q.explanation) { <div class="rs-exp"><i class="bi bi-lightbulb"></i> {{ q.explanation }}</div> }
        </article>
      }
      <div class="d-flex justify-content-center gap-2 mt-3">
        <a routerLink="/student/prep" class="btn btn-outline-secondary">Back to Preparation</a>
      </div>
    }`,
  styles: [`
    .rs-hero { display: flex; gap: 1.5rem; align-items: center; flex-wrap: wrap; }
    .rs-ring { width: 132px; height: 132px; flex-shrink: 0; }
    .rs-track { fill: none; stroke: var(--pm-soft); stroke-width: 10; }
    .rs-val { fill: none; stroke: #e11d48; stroke-width: 10; stroke-linecap: round; transition: stroke-dashoffset .8s ease; }
    .rs-val.mid { stroke: #f59e0b; } .rs-val.good { stroke: #16a34a; }
    .rs-pct { font: 800 22px Inter, sans-serif; fill: var(--pm-heading); }
    .rs-of { font: 600 11px Inter, sans-serif; fill: var(--pm-muted); }
    .rs-msg { color: var(--pm-heading); }
    .rs-sec { padding: 1rem 1.1rem; display: grid; gap: .45rem; }
    .rs-bar { height: 8px; border-radius: 999px; background: var(--pm-soft); overflow: hidden; }
    .rs-bar > div { height: 100%; border-radius: 999px; background: #e11d48; } .rs-bar > div.mid { background: #f59e0b; } .rs-bar > div.good { background: #16a34a; }
    .rs-q { border-left: 4px solid #16a34a; } .rs-q.rs-bad { border-left-color: #e11d48; }
    .rs-num { font-weight: 800; color: var(--pm-link); }
    .rs-opts { display: grid; gap: .4rem; }
    .rs-opt { display: flex; gap: .6rem; align-items: center; padding: .5rem .75rem; border-radius: 10px; border: 1px solid var(--pm-card-border); font-size: .9rem; }
    .rs-opt.correct { border-color: rgba(22,163,74,.5); background: rgba(22,163,74,.08); } .rs-opt.correct i { color: #16a34a; }
    .rs-opt.wrong { border-color: rgba(225,29,72,.5); background: rgba(225,29,72,.07); } .rs-opt.wrong i { color: #e11d48; }
    .rs-letter { width: 1.6rem; height: 1.6rem; border-radius: 50%; display: grid; place-items: center; font-size: .75rem; font-weight: 700;
      background: var(--pm-soft); color: var(--pm-label); flex-shrink: 0; }
    .rs-code { margin: .5rem 0 0; padding: .75rem; border-radius: 10px; background: #0f172a; color: #e2e8f0; font-size: .8rem; overflow-x: auto; max-height: 320px; }
    .rs-err { background: rgba(225,29,72,.08); color: #be123c; }
    .rs-exp { margin-top: .75rem; padding: .6rem .8rem; border-radius: 10px; background: rgba(11,92,255,.07); font-size: .875rem; color: var(--pm-heading); }
    .rs-exp i { color: var(--pm-link); margin-right: .25rem; }
    details { border: 0 !important; background: transparent !important; } summary { cursor: pointer; color: var(--pm-link); }
  `],
})
export class PrepResult implements OnInit {
  readonly id = input.required<string>();
  private prep = inject(PrepService);
  readonly circ = 2 * Math.PI * 52;
  readonly letters = ['A', 'B', 'C', 'D', 'E', 'F'];
  readonly langLabels = LANGUAGE_LABELS;
  readonly filters: { key: Filter; label: string }[] = [{ key: 'all', label: 'All' }, { key: 'wrong', label: 'Mistakes' }, { key: 'right', label: 'Correct' }];
  r = signal<AttemptResult | null>(null);
  /** Times the student left the test, with screenshots (also seen by the placement cell). */
  proctor = signal<ProctorLog | null>(null);
  readonly screenshot = (eventId: string) => this.prep.screenshotUrl(eventId);
  filter = signal<Filter>('all');
  shown = computed(() => (this.r()?.questions ?? []).filter(q => this.filter() === 'all' || (this.filter() === 'right') === this.full(q)));
  message = computed(() => {
    const p = this.r()?.percent ?? 0;
    const weak = [...(this.r()?.sections ?? [])].sort((a, b) => this.pct(a.score, a.max) - this.pct(b.score, b.max))[0];
    if (p >= 80) return 'Excellent. You are well prepared for this pattern.';
    if (p >= 60) return `Good attempt. Focus a little more on ${weak?.name ?? 'your weaker sections'} before the drive.`;
    return `Keep practising. Start with ${weak?.name ?? 'the basics'}: read the explanations below and retake the test.`;
  });

  ngOnInit(): void {
    this.prep.result(this.id()).subscribe(r => this.r.set(r));
    this.prep.proctorLog(this.id()).subscribe({ next: l => this.proctor.set(l), error: () => undefined });
  }
  full(q: QuestionResult): boolean { return Number(q.marksAwarded) >= q.marks; }
  number(q: QuestionResult): number { return (this.r()?.questions.indexOf(q) ?? 0) + 1; }
  pct(score: number, max: number): number { return max ? Math.round(score * 100 / max) : 0; }
  duration(s: number): string { const m = Math.floor(s / 60); return m ? `${m} min ${s % 60} s` : `${s} s`; }
}
