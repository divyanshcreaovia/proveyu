import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LanguageKey, MySubmission, MySubmissions, SubmissionQuery } from './code-ground.models';
import { CodeGroundService } from './code-ground.service';

const VERDICTS = { ACCEPTED: 'Accepted', WRONG_ANSWER: 'Wrong Answer', TIME_LIMIT: 'Time Limit Exceeded', RUNTIME_ERROR: 'Runtime Error', COMPILE_ERROR: 'Compile Error' };
const LANGUAGES: Record<LanguageKey, string> = { javascript: 'JavaScript', python: 'Python', java: 'Java', cpp: 'C++' };
type Range = '' | '1' | '7' | '30' | 'custom';

/** Every submission across Code ground, grouped by day, with filters and a summary of what matches them. */
@Component({
  selector: 'pm-my-submissions',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    <div class="cg-panel ms-filters">
      <label><span>When</span>
        <select [value]="range()" (change)="setRange($any($event.target).value)">
          <option value="">All time</option><option value="1">Today</option><option value="7">Last 7 days</option><option value="30">Last 30 days</option>
          <option value="custom">Choose dates</option></select></label>
      @if (range() === 'custom') {
        <label><span>From</span><input type="date" [value]="query().from ?? ''" (change)="set({ from: $any($event.target).value })" /></label>
        <label><span>To</span><input type="date" [value]="query().to ?? ''" (change)="set({ to: $any($event.target).value })" /></label>
      }
      <label><span>Result</span>
        <select [value]="query().verdict ?? ''" (change)="set({ verdict: $any($event.target).value })">
          <option value="">Any</option>@for (v of verdicts; track v[0]) { <option [value]="v[0]">{{ v[1] }}</option> }</select></label>
      <label><span>Language</span>
        <select [value]="query().language ?? ''" (change)="set({ language: $any($event.target).value })">
          <option value="">Any</option>@for (l of languages; track l[0]) { <option [value]="l[0]">{{ l[1] }}</option> }</select></label>
      <label><span>Difficulty</span>
        <select [value]="query().difficulty ?? ''" (change)="set({ difficulty: $any($event.target).value })">
          <option value="">Any</option><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></label>
      <label class="ms-search"><span>Question</span><input type="search" placeholder="Search by name" [value]="query().q ?? ''" (input)="search($any($event.target).value)" /></label>
      <label class="ms-check"><input type="checkbox" [checked]="query().stuck" (change)="set({ stuck: $any($event.target).checked })" /> Not solved yet</label>
      @if (filtered()) { <button type="button" class="cg-btn cg-btn-ghost" (click)="clear()">Clear</button> }
    </div>

    @if (failed()) {
      <div class="cg-panel ms-msg">Your submissions could not be loaded. <button type="button" class="cg-btn cg-btn-primary ms-2" (click)="load()">Try again</button></div>
    } @else if (!data()) {
      <div class="cg-panel ms-msg">Loading…</div>
    } @else {
      @let s = data()!.summary;
      <section class="cg-panel ms-summary" aria-label="Summary of these results" aria-live="polite">
        <div><span>Submissions</span><strong>{{ s.total }}</strong></div>
        <div><span>Accepted</span><strong>{{ s.accepted }}</strong><small>{{ rate() }}%</small></div>
        <div><span>Questions solved</span><strong>{{ s.solvedProblems }}</strong><small>of {{ s.problems }} tried</small></div>
        <div><span>Most used</span><strong>{{ s.topLanguage ? languageName[s.topLanguage] : '—' }}</strong></div>
      </section>

      @for (day of days(); track day.date) {
        <h3 class="ms-day">{{ day.date | date: 'EEE, d MMM y' }} <span>{{ day.items.length }}</span></h3>
        <ul class="cg-panel ms-list">
          @for (x of day.items; track x.id) {
            <li>
              <button type="button" class="ms-row" [attr.aria-expanded]="open() === x.id" (click)="toggle(x)">
                <span class="ms-time">{{ x.createdAt | date: 'h:mm a' }}</span>
                <span class="ms-title">{{ x.title }}
                  @if (x.difficulty) { <span class="cg-diff" [attr.data-level]="x.difficulty">{{ x.difficulty.toLowerCase() }}</span> }</span>
                <span class="ms-verdict" [class.ms-ok]="x.verdict === 'ACCEPTED'">{{ verdictName[x.verdict] }}</span>
                <span class="ms-muted">{{ x.passed }}/{{ x.total }}</span>
                <span class="ms-muted ms-lang">{{ languageName[x.language] }}</span>
                <i class="bi" [class.bi-chevron-down]="open() !== x.id" [class.bi-chevron-up]="open() === x.id" aria-hidden="true"></i>
              </button>
              @if (open() === x.id) {
                <div class="ms-detail">
                  <pre class="cg-code">{{ code() ?? 'Loading…' }}</pre>
                  @if (x.available) { <button type="button" class="cg-btn cg-btn-primary" (click)="solve.emit(x.problemKey)">Open question <i class="bi bi-arrow-right" aria-hidden="true"></i></button> }
                  @else { <span class="ms-muted">This question is no longer available.</span> }
                </div>
              }
            </li>
          }
        </ul>
      } @empty {
        <div class="cg-panel ms-msg">{{ filtered() ? 'Nothing matches these filters.' : 'Nothing submitted yet. Submit a solution and it appears here.' }}</div>
      }
      @if (data()!.hasMore) { <button type="button" class="cg-btn ms-more" [disabled]="loading()" (click)="more()">Show more</button> }
      <p class="ms-note">The last 20 submissions of each question are kept. Run is not recorded.</p>
    }`,
  styles: [`
    :host { display: block; }
    .ms-filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: .75rem; padding: .85rem 1rem; margin-bottom: 1rem; }
    label { display: flex; flex-direction: column; gap: .2rem; margin: 0; font-size: .72rem; font-weight: 600; color: var(--pm-muted); }
    select, input[type='date'], input[type='search'] { height: 32px; padding: 0 .55rem; border-radius: 7px; border: 1px solid var(--cg-line); background: var(--cg-panel);
      color: var(--pm-heading); font-size: .8rem; }
    select:focus-visible, input:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 1px; }
    .ms-search { flex: 1; min-width: 160px; }
    .ms-check { flex-direction: row; align-items: center; gap: .4rem; height: 32px; font-size: .8rem; color: var(--pm-heading); }
    .ms-msg { padding: 1.5rem; text-align: center; color: var(--pm-muted); }
    .ms-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; padding: .9rem 1.1rem; margin-bottom: 1rem; }
    .ms-summary span { display: block; font-size: .7rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--pm-muted); }
    .ms-summary strong { font-size: 1.4rem; font-weight: 700; color: var(--pm-heading); margin-right: .35rem; font-variant-numeric: tabular-nums; }
    .ms-summary small { font-size: .76rem; color: var(--pm-muted); }
    .ms-day { font-size: .84rem; font-weight: 700; color: var(--pm-heading); margin: 1rem 0 .4rem; }
    .ms-day span { margin-left: .35rem; font-weight: 500; color: var(--pm-muted); }
    .ms-list { list-style: none; margin: 0; padding: 0; overflow: hidden; }
    .ms-list li + li { border-top: 1px solid var(--cg-line); }
    .ms-row { all: unset; box-sizing: border-box; cursor: pointer; display: grid; grid-template-columns: 5rem minmax(0, 1fr) 9.5rem 3rem 6rem 1rem; align-items: center;
      gap: .75rem; width: 100%; padding: .65rem 1rem; font-size: .84rem; color: var(--pm-label); }
    .ms-row:hover { background: var(--cg-sub); }
    .ms-row:focus-visible { outline: 2px solid var(--pm-link); outline-offset: -2px; }
    .ms-time, .ms-muted { font-size: .78rem; color: var(--pm-muted); font-variant-numeric: tabular-nums; }
    .ms-title { font-weight: 600; color: var(--pm-heading); overflow-wrap: anywhere; }
    .ms-title .cg-diff { margin-left: .4rem; text-transform: capitalize; }
    .ms-verdict { font-weight: 700; color: var(--cg-bad); }
    .ms-ok { color: var(--cg-ok); }
    .ms-detail { padding: 0 1rem .85rem; }
    .ms-detail pre { margin: 0 0 .6rem; padding: .65rem .8rem; border-radius: 8px; background: var(--cg-sub); color: var(--pm-heading); max-height: 320px; overflow: auto; }
    .ms-more { margin-top: .85rem; width: 100%; }
    .ms-note { margin: .85rem 0 0; font-size: .74rem; color: var(--pm-muted); }
    @media (max-width: 767.98px) {
      .ms-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .ms-row { grid-template-columns: minmax(0, 1fr) auto; }
      .ms-title { grid-column: 1; grid-row: 1; }
      .ms-verdict { grid-column: 1; grid-row: 2; }
      .ms-time { grid-column: 2; grid-row: 1; text-align: right; }
      .ms-muted:not(.ms-time) { display: none; }
      .ms-row i { grid-column: 2; grid-row: 2; justify-self: end; }
    }
  `],
})
export class MySubmissionsView implements OnInit {
  /** Open this question in the practice view. */
  solve = output<string>();

  private api = inject(CodeGroundService);
  private destroyRef = inject(DestroyRef);
  verdictName = VERDICTS;
  languageName = LANGUAGES;
  verdicts = Object.entries(VERDICTS);
  languages = Object.entries(LANGUAGES);

  range = signal<Range>('');
  query = signal<SubmissionQuery>({ page: 0 });
  data = signal<MySubmissions | null>(null);
  loading = signal(false);
  failed = signal(false);
  open = signal<number | null>(null);
  code = signal<string | null>(null);
  private request = 0;
  private typing?: ReturnType<typeof setTimeout>;

  filtered = computed(() => {
    const q = this.query();
    return !!(this.range() || q.verdict || q.language || q.difficulty || q.q || q.stuck);
  });
  rate = computed(() => {
    const s = this.data()?.summary;
    return s?.total ? Math.round(s.accepted / s.total * 100) : 0;
  });
  /** Grouped by the day it was in this browser's time. */
  days = computed(() => {
    const days: { date: string; items: MySubmission[] }[] = [];
    for (const x of this.data()?.items ?? []) {
      const date = new Date(x.createdAt).toDateString();
      const last = days[days.length - 1];
      if (last?.date === date) last.items.push(x); else days.push({ date, items: [x] });
    }
    return days;
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.typing));
    this.load();
  }

  setRange(range: Range): void {
    this.range.set(range);
    const days = range && range !== 'custom' ? Number(range) : undefined;
    this.set({ days, from: undefined, to: undefined });
  }

  set(change: Partial<SubmissionQuery>): void {
    this.query.update(q => ({ ...q, ...change, page: 0 }));
    this.load();
  }

  search(q: string): void {
    clearTimeout(this.typing);
    this.typing = setTimeout(() => this.set({ q }), 300);
  }

  clear(): void {
    clearTimeout(this.typing);
    this.range.set('');
    this.query.set({ page: 0 });
    this.load();
  }

  load(): void {
    const n = ++this.request;
    this.failed.set(false);
    this.loading.set(true);
    this.open.set(null);
    this.api.mySubmissions({ ...this.query(), page: 0 }).subscribe({
      next: d => { if (n === this.request) { this.data.set(d); this.loading.set(false); } },
      error: () => { if (n === this.request) { this.failed.set(true); this.loading.set(false); } },
    });
  }

  more(): void {
    const n = this.request, page = this.query().page + 1;
    this.loading.set(true);
    this.api.mySubmissions({ ...this.query(), page }).subscribe({
      next: d => {
        if (n !== this.request) return;
        this.query.update(q => ({ ...q, page }));
        this.data.update(old => ({ ...d, items: [...(old?.items ?? []), ...d.items] }));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  toggle(x: MySubmission): void {
    if (this.open() === x.id) { this.open.set(null); return; }
    this.open.set(x.id);
    this.code.set(null);
    this.api.submissionCode(x.id).subscribe({
      next: r => {
        if (this.open() !== x.id) return;
        // the student's own part, without the input / print code
        const marker = r.code.indexOf('no need to change below');
        this.code.set((marker < 0 ? r.code : r.code.slice(0, r.code.lastIndexOf('\n', marker))).trimEnd());
      },
      error: () => { if (this.open() === x.id) this.code.set('The code could not be loaded.'); },
    });
  }
}
