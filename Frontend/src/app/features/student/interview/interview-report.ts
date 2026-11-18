import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppError } from '../../../core/api';
import { AnswerReview, InterviewService, RECOMMENDATION_LABELS, ReportView, TYPE_LABELS, DIFFICULTY_LABELS } from '../services/interview.service';
import { ToastService } from '../../../core/services/toast.service';
import { Skeleton } from '../../../shared/ui/states';

/**
 * Report of one AI interview: overall score, coach summary, counts (answered / skipped / not reached), criteria averages, the
 * technologies covered, strengths and improvements, and every planned question with its answer, scores and a model answer.
 */
@Component({
  selector: 'pm-interview-report',
  imports: [DatePipe, DecimalPipe, RouterLink, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <a routerLink="/student/ai-interview" class="small d-inline-block mb-2"><i class="bi bi-arrow-left"></i> Back to AI interview</a>
    @if (!r()) { <pm-skeleton [rows]="8" /> } @else {
      @let rep = r()!;
      @if (rep.status === 'EVALUATING' || rep.status === 'IN_PROGRESS') {
        <section class="st-card text-center py-5 mb-3" aria-live="polite">
          <span class="spinner-border text-primary mb-3" role="status"></span>
          <h2 class="h5 st-h">Scoring your interview…</h2>
          <p class="st-muted mb-2">The AI is reviewing each answer. This page updates by itself.</p>
          <div class="rp-bar mx-auto"><div [style.width.%]="rep.total ? rep.evaluated * 100 / rep.total : 5"></div></div>
          <p class="small st-muted mt-2 mb-0">{{ rep.evaluated }} of {{ rep.total }} answers scored</p>
        </section>
      } @else if (rep.status === 'FAILED') {
        <section class="st-card text-center py-4 mb-3">
          <h2 class="h5 st-h">Scoring did not finish</h2>
          <p class="st-muted">{{ rep.errorMessage }}</p>
          <button class="btn btn-primary" (click)="retry()" [disabled]="retrying()">Try scoring again</button>
        </section>
      } @else {
        <section class="st-card rp-hero mb-3">
          <svg viewBox="0 0 120 120" class="rp-ring" role="img" [attr.aria-label]="'Score ' + (rep.overallScore | number: '1.0-0') + ' out of 100'">
            <circle cx="60" cy="60" r="52" class="rp-track" />
            <circle cx="60" cy="60" r="52" class="rp-val" [class.mid]="tone(rep) === 'mid'" [class.good]="tone(rep) === 'good'"
              [attr.stroke-dasharray]="circ" [attr.stroke-dashoffset]="circ * (1 - (rep.overallScore ?? 0) / 100)" transform="rotate(-90 60 60)" />
            <text x="60" y="62" text-anchor="middle" class="rp-pct">{{ rep.overallScore | number: '1.0-0' }}</text>
            <text x="60" y="80" text-anchor="middle" class="rp-of">out of 100</text>
          </svg>
          <div class="min-w-0 flex-grow-1">
            <span class="rp-rec" [class.mid]="tone(rep) === 'mid'" [class.good]="tone(rep) === 'good'">{{ recLabels[rep.recommendation!] }}</span>
            <h2 class="h4 st-h mt-2 mb-1">{{ typeLabels[rep.type] }} interview · {{ levelLabels[rep.difficulty] }} · {{ rep.companyName ? rep.companyName + ' · ' : '' }}{{ rep.roleTitle }}</h2>
            <p class="st-muted small mb-2">{{ rep.startedAt | date: 'medium' }}@if (rep.durationSeconds) { · {{ duration(rep.durationSeconds) }} }</p>
            @if (rep.summary) { <p class="mb-0">{{ rep.summary }}</p> }
          </div>
        </section>

        <section class="st-card mb-3" aria-label="Interview details">
          <div class="rp-stats">
            <div><span class="rp-stat">{{ rep.plannedQuestions }}</span><span class="small st-muted">Questions</span></div>
            <div><span class="rp-stat text-success">{{ rep.answeredCount }}</span><span class="small st-muted">Answered</span></div>
            <div><span class="rp-stat" [class.text-danger]="rep.skippedCount">{{ rep.skippedCount }}</span><span class="small st-muted">Skipped (0 each)</span></div>
            <div><span class="rp-stat" [class.text-danger]="rep.notReachedCount">{{ rep.notReachedCount }}</span><span class="small st-muted">Not reached (0 each)</span></div>
          </div>
          <div class="rp-scores mt-3">
            @for (s of criteriaRows(rep); track s.label) {
              <div><span class="small st-muted">{{ s.label }}</span>
                <div class="rp-bar"><div [style.width.%]="s.value * 20" [class.mid]="s.value >= 2.5 && s.value < 3.5" [class.good]="s.value >= 3.5"></div></div>
                <span class="small fw-semibold">{{ s.value | number: '1.1-1' }}/5</span></div>
            }
          </div>
          <p class="small st-muted mt-3 mb-0">
            @if (rep.focus.length) { Questions were limited to: @for (f of rep.focus; track f) { <span class="st-chip ms-1">{{ f }}</span> } }
            @else { Questions covered general fundamentals (no specific technology). }
            @if (rep.ownJobDescription) { <span class="ms-1">· Based on the job description you pasted.</span> }
            <br>Scores are 0 to 5 per criterion and based only on what you said; a skipped or unanswered question is 0.
          </p>
        </section>

        @if (rep.strengths.length || rep.improvements.length) {
          <div class="row g-3 mb-3">
            <div class="col-md-6"><div class="st-card h-100">
              <h3 class="h6 st-h"><i class="bi bi-hand-thumbs-up text-success me-1"></i> What went well</h3>
              <ul class="rp-list mb-0">@for (s of rep.strengths; track $index) { <li>{{ s }}</li> } @empty { <li class="st-muted">Keep practising to build strengths.</li> }</ul>
            </div></div>
            <div class="col-md-6"><div class="st-card h-100">
              <h3 class="h6 st-h"><i class="bi bi-graph-up-arrow text-primary me-1"></i> Work on next</h3>
              <ul class="rp-list mb-0">@for (s of rep.improvements; track $index) { <li>{{ s }}</li> }</ul>
            </div></div>
          </div>
        }
      }

      @if (rep.answers.length) {
        <h2 class="h6 st-h mb-2">Question by question</h2>
        @for (a of rep.answers; track a.questionId) {
          <article class="st-card rp-q mb-2" [class.rp-miss]="a.status !== 'ANSWERED'">
            <div class="d-flex gap-2 align-items-center flex-wrap mb-2">
              <span class="rp-num">Q{{ a.number }}</span>
              @if (a.followUp) { <span class="st-chip">Follow-up</span> }
              @if (a.topic) { <span class="st-chip">{{ a.topic }}</span> }
              @if (a.status === 'SKIPPED') { <span class="rp-flag">Skipped</span> }
              @if (a.status === 'NOT_REACHED') { <span class="rp-flag">Not reached</span> }
              @if (a.percent != null) { <span class="ms-auto rp-mini" [class.mid]="a.percent >= 50 && a.percent < 75" [class.good]="a.percent >= 75">{{ a.percent }}/100</span> }
            </div>
            <p class="fw-semibold st-h mb-2">{{ a.question }}</p>
            @if (a.status !== 'NOT_REACHED') {
              <div class="rp-said small mb-2">
                <div class="rp-label">You said @if (a.inputMode === 'TEXT' && a.transcript) { (typed) } @if (a.wordsPerMinute) { · {{ a.wordsPerMinute }} words/min }</div>
                @if (a.transcript) { {{ a.transcript }} } @else { <em class="st-muted">Skipped, no answer given</em> }
                @if (a.hasAudio && a.answerId) {
                  <div class="mt-1">@if (audio()[a.answerId]; as url) { <audio [src]="url" controls class="rp-audio"></audio> }
                    @else { <button class="btn btn-sm btn-link p-0" (click)="loadAudio(a)"><i class="bi bi-play-circle"></i> Play my recording</button> }</div>
                }
              </div>
            }
            @if (a.content != null) {
              @if (a.status === 'ANSWERED') {
                <div class="rp-scores mb-2">
                  @for (s of scoreRows(a); track s.label) {
                    <div><span class="small st-muted">{{ s.label }}</span>
                      <div class="rp-bar"><div [style.width.%]="s.value * 20" [class.mid]="s.value === 3" [class.good]="s.value >= 4"></div></div>
                      <span class="small fw-semibold">{{ s.value }}/5</span></div>
                  }
                </div>
              }
              @if (a.feedback) { <p class="small mb-2"><i class="bi bi-chat-left-quote text-primary me-1"></i>{{ a.feedback }}</p> }
              @if (a.betterAnswer) {
                <details class="small" [open]="a.status === 'SKIPPED'"><summary class="fw-semibold">What a strong answer includes</summary><p class="mt-2 mb-0 st-muted">{{ a.betterAnswer }}</p></details>
              }
            } @else { <p class="small st-muted mb-0">Scoring…</p> }
          </article>
        }
      }
    }`,
  styles: [`
    .rp-hero { display: flex; gap: 1.5rem; align-items: center; flex-wrap: wrap; }
    .rp-ring { width: 132px; height: 132px; flex-shrink: 0; }
    .rp-track { fill: none; stroke: var(--pm-soft); stroke-width: 10; }
    .rp-val { fill: none; stroke: #e11d48; stroke-width: 10; stroke-linecap: round; transition: stroke-dashoffset .6s; }
    .rp-val.mid { stroke: #d97706; } .rp-val.good { stroke: #16a34a; }
    .rp-pct { font-size: 30px; font-weight: 800; fill: var(--pm-heading); }
    .rp-of { font-size: 11px; fill: var(--pm-muted); }
    .rp-rec { display: inline-block; font-size: .75rem; font-weight: 700; padding: .2rem .7rem; border-radius: 999px; background: rgba(225,29,72,.12); color: #be123c; }
    .rp-rec.mid { background: rgba(217,119,6,.14); color: #b45309; } .rp-rec.good { background: rgba(22,163,74,.14); color: #15803d; }
    .rp-list { padding-left: 1.1rem; display: grid; gap: .35rem; font-size: .9rem; }
    .rp-num { font-weight: 800; color: var(--pm-link); }
    .rp-mini { font-weight: 800; font-variant-numeric: tabular-nums; color: #e11d48; }
    .rp-mini.mid { color: #d97706; } .rp-mini.good { color: #16a34a; }
    .rp-said { padding: .6rem .8rem; border-radius: 10px; background: var(--pm-soft); }
    .rp-label { font-size: .68rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--pm-muted); margin-bottom: .2rem; }
    .rp-audio { height: 32px; max-width: 100%; }
    .rp-scores { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: .4rem 1.25rem; }
    .rp-scores > div { display: grid; grid-template-columns: 6.5rem 1fr auto; align-items: center; gap: .5rem; }
    .rp-bar { height: .45rem; border-radius: 999px; background: var(--pm-soft); overflow: hidden; max-width: 320px; }
    .rp-bar > div { height: 100%; background: #e11d48; border-radius: 999px; }
    .rp-bar > div.mid { background: #d97706; } .rp-bar > div.good { background: #16a34a; }
    .rp-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: .75rem; }
    .rp-stats > div { display: grid; gap: .1rem; }
    .rp-stat { font-size: 1.6rem; font-weight: 800; line-height: 1; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .rp-flag { font-size: .7rem; font-weight: 700; padding: .12rem .55rem; border-radius: 999px; background: rgba(225,29,72,.12); color: #be123c; }
    .rp-miss { border-left: 3px solid rgba(225,29,72,.5); }
    section.text-center .rp-bar > div { background: var(--pm-link); }
  `],
})
export class InterviewReport implements OnInit {
  readonly id = input.required<string>();
  readonly typeLabels = TYPE_LABELS;
  readonly levelLabels = DIFFICULTY_LABELS;
  readonly recLabels = RECOMMENDATION_LABELS;
  readonly circ = 2 * Math.PI * 52;
  private api = inject(InterviewService);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);
  r = signal<ReportView | null>(null);
  audio = signal<Record<string, string>>({});
  retrying = signal(false);
  private poll?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.load();
    this.destroyRef.onDestroy(() => {
      clearTimeout(this.poll);
      Object.values(this.audio()).forEach(u => URL.revokeObjectURL(u));
    });
  }

  load(): void {
    this.api.report(this.id()).subscribe({
      next: r => {
        this.r.set(r);
        if (r.status === 'EVALUATING' || r.status === 'IN_PROGRESS') this.poll = setTimeout(() => this.load(), 3000);
      },
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  retry(): void {
    this.retrying.set(true);
    this.api.retryReport(this.id()).subscribe({ next: r => { this.retrying.set(false); this.r.set(r); this.load(); }, error: () => this.retrying.set(false) });
  }

  loadAudio(a: AnswerReview): void {
    const id = a.answerId;
    if (!id) return;
    this.api.answerAudio(id).subscribe({
      next: url => this.audio.update(m => ({ ...m, [id]: url })),
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  tone(r: ReportView): 'good' | 'mid' | 'low' {
    const s = r.overallScore ?? 0;
    return s >= 75 ? 'good' : s >= 55 ? 'mid' : 'low';
  }
  duration(sec: number): string { const m = Math.floor(sec / 60); return m ? `${m} min ${sec % 60} s` : `${sec} s`; }
  criteriaRows(r: ReportView): { label: string; value: number }[] {
    const c = r.criteria;
    return [{ label: 'Content', value: c.content }, { label: 'Relevance', value: c.relevance },
      { label: 'Communication', value: c.communication }, { label: 'Confidence', value: c.confidence }];
  }
  scoreRows(a: AnswerReview): { label: string; value: number }[] {
    return [{ label: 'Content', value: a.content ?? 0 }, { label: 'Relevance', value: a.relevance ?? 0 },
      { label: 'Communication', value: a.communication ?? 0 }, { label: 'Confidence', value: a.confidence ?? 0 }];
  }
}
