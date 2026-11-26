import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { FreeCompiler } from './free-compiler';
import { Guide, Overview, Problem, TopicCard } from './code-ground.models';
import { Leaderboard } from './leaderboard';
import { ActivityGraph } from './activity-graph';
import { DailyCard } from './daily-card';
import { MySubmissionsView } from './my-submissions';
import { CodeGroundService } from './code-ground.service';
import { LessonReader } from './lesson-reader';
import { ProblemTable } from './problem-table';
import { ProgressSummary } from './progress-summary';
import { Roadmap } from './roadmap';
import { Workspace } from './workspace';

type View = 'topics' | 'guide' | 'problem';
type Section = 'practice' | 'submissions' | 'leaderboard' | 'compiler';

/**
 * Code ground: a study plan of topics, each with a learning guide and practice problems checked on the server, plus the college leaderboard and a free compiler.
 * Where the student is lives in the URL: ?topic=arrays (topic), &view=guide (its guide), &p=two-sum (a problem), ?tab=leaderboard or compiler.
 */
@Component({
  selector: 'pm-code-ground',
  imports: [ProgressSummary, Roadmap, ProblemTable, LessonReader, Workspace, FreeCompiler, Leaderboard, ActivityGraph, DailyCard, MySubmissionsView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    @if (view() !== 'problem' || section() !== 'practice') {
      <div class="cg-top">
        <p class="cg-lead">Learn each topic with a short guide, then solve its problems from easy to hard. Your code runs on the same judge as the mock tests.</p>
        <div class="cg-switch" role="tablist" aria-label="Code ground sections">
          <button type="button" role="tab" [attr.aria-selected]="section() === 'practice'" (click)="show('practice')"><i class="bi bi-signpost-split" aria-hidden="true"></i> Practice</button>
          <button type="button" role="tab" [attr.aria-selected]="section() === 'submissions'" (click)="show('submissions')"><i class="bi bi-clock-history" aria-hidden="true"></i> My submissions</button>
          <button type="button" role="tab" [attr.aria-selected]="section() === 'leaderboard'" (click)="show('leaderboard')"><i class="bi bi-trophy" aria-hidden="true"></i> Leaderboard</button>
          <button type="button" role="tab" [attr.aria-selected]="section() === 'compiler'" (click)="show('compiler')"><i class="bi bi-terminal" aria-hidden="true"></i> Free compiler</button>
        </div>
      </div>
    }

    <!-- only what really happens: keep this true when what is stored or shown changes -->
    @if (view() !== 'problem' && section() !== 'compiler') {
      <p class="cg-notice" role="note"><i class="bi bi-info-circle" aria-hidden="true"></i>
        <span><strong>Write your own code.</strong> Copy and paste are off in the editor. Every submission is saved with its code and time.
          Your placement cell can see your points, solved count, streak and last active day, and your college sees your name and rank on the leaderboard.</span></p>
    }

    @if (section() === 'compiler') {
      <pm-free-compiler />
    } @else if (section() === 'submissions') {
      <pm-my-submissions (solve)="solveAgain($event)" />
    } @else if (section() === 'leaderboard') {
      <pm-leaderboard />
    } @else if (failed()) {
      <div class="cg-panel cg-msg">Code ground could not be loaded. <button type="button" class="cg-btn cg-btn-primary ms-2" (click)="load()">Try again</button></div>
    } @else if (!overview() || !current()) {
      <div class="cg-skel"><span></span><span></span><span></span></div>
    } @else {
      @let t = current()!;
      @switch (view()) {
        @case ('problem') {
          @if (problem(); as pr) {
            <pm-workspace [problem]="pr" [topic]="t" [languages]="overview()!.languages" (solvedNow)="refresh()" (open)="openProblem($event)" (back)="openTopic(t.key)" />
          } @else { <div class="cg-skel"><span></span><span></span></div> }
        }
        @case ('guide') {
          <nav class="cg-crumbs" aria-label="Breadcrumb">
            <button type="button" (click)="openTopic(t.key)"><i class="bi bi-arrow-left" aria-hidden="true"></i> {{ t.title }}</button>
            <span aria-hidden="true">/</span><span>Learning guide</span>
          </nav>
          @if (guide(); as g) { <pm-lesson-reader [guide]="g" [saving]="saving()" (completed)="markRead()" (practise)="practise()" /> }
          @else { <div class="cg-skel"><span></span></div> }
        }
        @default {
          <pm-progress-summary [overview]="overview()!" (resume)="resume($event)" />
          <div class="cg-today">
            <pm-daily-card [daily]="overview()!.daily" (open)="openProblem($event)" />
            <pm-activity-graph />
          </div>
          <section class="cg-panel cg-plan" aria-label="Study plan">
            <div class="cg-plan-head"><h2>Study plan</h2><span>{{ overview()!.topics.length }} topics · easy to hard</span></div>
            <pm-roadmap [topics]="overview()!.topics" [selected]="t.key" (pick)="openTopic($event)" />
          </section>

          <section class="cg-panel cg-topic" [attr.aria-label]="t.title">
            <header class="cg-topic-head">
              <div class="min-w-0">
                <div class="cg-kicker">Topic {{ topicNo() }} of {{ overview()!.topics.length }}</div>
                <h2>{{ t.title }}</h2>
                <p>{{ t.summary }}</p>
              </div>
              <div class="cg-topic-progress">
                <div class="cg-tp-num"><strong>{{ t.done }}</strong>/{{ t.total }} done</div>
                <div class="cg-tp-bar"><span [style.width.%]="t.done / t.total * 100"></span></div>
              </div>
            </header>

            @if (t.guideSlides) {
            <button type="button" class="cg-guide" (click)="openGuide()">
              <span class="cg-guide-icon" [class.cg-guide-read]="t.guideRead" aria-hidden="true">
                <i class="bi" [class.bi-check-lg]="t.guideRead" [class.bi-journal-text]="!t.guideRead"></i></span>
              <span class="min-w-0 flex-grow-1">
                <span class="cg-guide-title">Learning guide</span>
                <span class="cg-guide-sub">{{ t.guideSlides }} short lessons on {{ t.summary.toLowerCase() }}</span>
              </span>
              <span class="cg-guide-state" [class.cg-guide-state-read]="t.guideRead">{{ t.guideRead ? 'Read' : 'Start' }}
                <i class="bi bi-chevron-right" aria-hidden="true"></i></span>
            </button>
            }

            <pm-problem-table [topic]="t" (open)="openProblem($event)" />
          </section>
        }
      }
    }`,
  styles: [`
    :host { display: block; }
    .cg-top { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .cg-lead { margin: 0; max-width: 70ch; font-size: .88rem; color: var(--pm-muted); }
    .cg-switch { display: inline-flex; flex-wrap: wrap; max-width: 100%; padding: 3px; border-radius: 9px; background: var(--cg-sub); border: 1px solid var(--cg-line); }
    .cg-switch button { all: unset; cursor: pointer; display: inline-flex; align-items: center; gap: .4rem; padding: .35rem .85rem; border-radius: 7px;
      font-size: .8rem; font-weight: 600; color: var(--pm-muted); }
    .cg-switch button:hover { color: var(--pm-heading); }
    .cg-switch [aria-selected='true'] { background: var(--cg-panel); color: var(--pm-heading); box-shadow: 0 1px 2px rgba(0,0,0,.08); }
    .cg-switch button:focus-visible { outline: 2px solid var(--pm-link); }
    .cg-msg { padding: 1.25rem; }
    .cg-notice { display: flex; gap: .6rem; align-items: baseline; margin: 0 0 1rem; padding: .6rem .85rem; border-radius: 9px; font-size: .8rem;
      color: var(--pm-label); background: var(--cg-medium-bg); border: 1px solid color-mix(in srgb, var(--cg-medium) 35%, transparent); }
    .cg-notice i { color: var(--cg-medium); }
    pm-progress-summary { display: block; margin-bottom: 1rem; }
    .cg-today { display: grid; grid-template-columns: minmax(220px, 300px) minmax(0, 1fr); gap: 1rem; margin-bottom: 1rem; }
    @media (max-width: 991.98px) { .cg-today { grid-template-columns: minmax(0, 1fr); } }
    .cg-plan { padding: .9rem .9rem .4rem; margin-bottom: 1rem; }
    .cg-plan-head { display: flex; align-items: baseline; gap: .75rem; padding: 0 .35rem .25rem; }
    .cg-plan-head h2 { font-size: .95rem; font-weight: 700; color: var(--pm-heading); margin: 0; }
    .cg-plan-head span { font-size: .76rem; color: var(--pm-muted); }
    .cg-topic { padding: 1.25rem 1.25rem .75rem; }
    .cg-topic-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .cg-topic-head h2 { font-size: 1.3rem; font-weight: 700; letter-spacing: -.01em; color: var(--pm-heading); margin: .15rem 0 .2rem; }
    .cg-topic-head p { margin: 0; font-size: .88rem; color: var(--pm-muted); }
    .cg-topic-progress { width: 220px; }
    .cg-tp-num { font-size: .8rem; color: var(--pm-muted); margin-bottom: .35rem; text-align: right; font-variant-numeric: tabular-nums; }
    .cg-tp-num strong { color: var(--pm-heading); font-size: 1rem; }
    .cg-tp-bar { height: 6px; border-radius: 999px; background: var(--cg-sub); overflow: hidden; }
    .cg-tp-bar span { display: block; height: 100%; border-radius: 999px; background: var(--cg-ok); transition: width .5s ease; }
    .cg-guide { all: unset; box-sizing: border-box; cursor: pointer; display: flex; align-items: center; gap: .9rem; width: 100%; padding: .85rem 1rem;
      margin-bottom: 1.1rem; border-radius: 10px; border: 1px solid var(--cg-line); background: linear-gradient(90deg, color-mix(in srgb, var(--pm-link) 7%, transparent), transparent 70%); }
    .cg-guide:hover { border-color: color-mix(in srgb, var(--pm-link) 45%, var(--cg-line)); }
    .cg-guide:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 2px; }
    .cg-guide-icon { flex: 0 0 40px; height: 40px; border-radius: 10px; display: grid; place-items: center; font-size: 1.1rem; color: var(--pm-link);
      background: color-mix(in srgb, var(--pm-link) 12%, transparent); }
    .cg-guide-read { color: #fff; background: var(--cg-ok); }
    .cg-guide-title { display: block; font-weight: 700; font-size: .92rem; color: var(--pm-heading); }
    .cg-guide-sub { display: block; font-size: .8rem; color: var(--pm-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cg-guide-state { flex: 0 0 auto; font-size: .8rem; font-weight: 600; color: var(--pm-link); }
    .cg-guide-state-read { color: var(--cg-ok); }
    .cg-crumbs { display: flex; align-items: center; gap: .5rem; margin-bottom: .75rem; font-size: .84rem; color: var(--pm-muted); }
    .cg-crumbs button { all: unset; cursor: pointer; display: inline-flex; align-items: center; gap: .35rem; font-weight: 600; color: var(--pm-heading);
      padding: .2rem .4rem; border-radius: 6px; }
    .cg-crumbs button:hover { background: var(--cg-sub); }
    .cg-crumbs button:focus-visible { outline: 2px solid var(--pm-link); }
    .cg-skel { display: grid; gap: 1rem; }
    .cg-skel span { height: 120px; border-radius: 10px; background: linear-gradient(90deg, var(--cg-sub) 25%, color-mix(in srgb, var(--cg-sub) 60%, var(--cg-panel)) 50%, var(--cg-sub) 75%);
      background-size: 200% 100%; animation: cgShine 1.2s ease-in-out infinite; }
    @keyframes cgShine { from { background-position: 200% 0; } to { background-position: -200% 0; } }
    @media (max-width: 575.98px) { .cg-topic-progress { width: 100%; } .cg-tp-num { text-align: left; } }
  `],
})
export class CodeGround implements OnInit {
  /** Query parameters (component input binding). */
  topic = input<string>();
  p = input<string>();
  tab = input<string>();
  viewParam = input<string>(undefined, { alias: 'view' });

  private api = inject(CodeGroundService);
  private router = inject(Router);
  private location = inject(Location);

  overview = signal<Overview | null>(null);
  failed = signal(false);
  topicKey = signal<string | null>(null);
  view = signal<View>('topics');
  guide = signal<Guide | null>(null);
  problem = signal<Problem | null>(null);
  saving = signal(false);
  section = signal<Section>('practice');

  current = computed<TopicCard | null>(() => this.overview()?.topics.find(t => t.key === this.topicKey()) ?? null);
  topicNo = computed(() => (this.overview()?.topics.findIndex(t => t.key === this.topicKey()) ?? 0) + 1);

  ngOnInit(): void {
    const tab = this.tab();
    if (tab === 'compiler' || tab === 'leaderboard' || tab === 'submissions') this.section.set(tab);
    this.load();
  }

  load(): void {
    this.failed.set(false);
    this.api.overview().subscribe({
      next: o => {
        this.overview.set(o);
        const linked = o.topics.find(t => t.key === this.topic());
        const start = linked ?? o.topics.find(t => t.done < t.total) ?? o.topics[0];
        const problem = linked?.problems.find(x => x.key === this.p())?.key;
        if (problem) { this.topicKey.set(start.key); this.openProblem(problem); }
        else if (linked && this.viewParam() === 'guide') { this.topicKey.set(start.key); this.openGuide(); }
        else this.openTopic(start.key, this.section() === 'practice');
      },
      error: () => this.failed.set(true),
    });
  }

  refresh(): void {
    this.api.overview().subscribe(o => this.overview.set(o));
  }

  openTopic(key: string, updateUrl = true): void {
    this.topicKey.set(key);
    this.view.set('topics');
    this.problem.set(null);
    if (updateUrl) this.url({ topic: key, p: null, view: null });
  }

  /** "Continue": the topic's guide if unread, else its first unsolved problem. */
  resume(key: string): void {
    const t = this.overview()?.topics.find(x => x.key === key);
    if (!t) return;
    this.topicKey.set(key);
    const p = t.problems.find(x => !x.solved);
    if (t.guideSlides && (!t.guideRead || !p)) this.openGuide(); else if (p) this.openProblem(p.key); else this.openTopic(key);
  }

  openProblem(key: string): void {
    const owner = this.overview()?.topics.find(t => t.problems.some(x => x.key === key));
    if (owner) this.topicKey.set(owner.key);
    this.view.set('problem');
    if (this.problem()?.key !== key) this.problem.set(null);
    this.url({ topic: this.topicKey(), p: key, view: null });
    this.api.problem(key).subscribe({
      next: x => { if (this.view() === 'problem' && this.topicKey() === owner?.key) this.problem.set(x); },
      // taken out of use, or the network failed: back to the list instead of a page that loads for ever
      error: () => { if (this.view() === 'problem') { this.openTopic(this.topicKey()!); this.refresh(); } },
    });
    window.scrollTo({ top: 0 });
  }

  openGuide(): void {
    const key = this.topicKey()!;
    this.view.set('guide');
    this.problem.set(null);
    if (this.guide()?.topicKey !== key) this.guide.set(null);
    this.url({ topic: key, p: null, view: 'guide' });
    this.api.guide(key).subscribe({
      next: g => { if (this.topicKey() === key) this.guide.set(g); },
      error: () => { if (this.view() === 'guide') this.openTopic(key); },
    });
    window.scrollTo({ top: 0 });
  }

  markRead(): void {
    const g = this.guide();
    if (!g || this.saving()) return;
    this.saving.set(true);
    this.api.completeGuide(g.topicKey).subscribe({
      next: () => { this.saving.set(false); this.guide.set({ ...g, read: true }); this.refresh(); },
      error: () => this.saving.set(false),
    });
  }

  practise(): void {
    const t = this.current();
    const next = t?.problems.find(x => !x.solved) ?? t?.problems[0];
    if (next) this.openProblem(next.key); else if (t) this.openTopic(t.key);
  }

  /** From the submissions list into the question's workspace. */
  solveAgain(key: string): void {
    if (!this.overview()?.topics.some(t => t.problems.some(p => p.key === key))) return;
    this.section.set('practice');
    this.url({ tab: null });
    this.openProblem(key);
  }

  show(section: Section): void {
    this.section.set(section);
    if (section !== 'practice') this.url({ tab: section, topic: null, p: null, view: null });
    else {
      this.url({ tab: null });
      this.openTopic(this.topicKey() ?? this.overview()?.topics[0]?.key ?? 'basics');
    }
  }

  /** Keeps the address bar in step without a router navigation (no page transition, nothing reloads). */
  private url(queryParams: Record<string, string | null>): void {
    const tree = this.router.parseUrl(this.location.path());
    for (const [k, v] of Object.entries(queryParams)) {
      if (v === null) delete tree.queryParams[k]; else tree.queryParams[k] = v;
    }
    this.location.replaceState(this.router.serializeUrl(tree));
  }
}
