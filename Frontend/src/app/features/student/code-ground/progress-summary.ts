import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Difficulty, Overview } from './code-ground.models';

const R = 40;

/** Overall progress, like a profile card on a practice platform: solved ring, solved per difficulty, guides read, and where to continue. */
@Component({
  selector: 'pm-progress-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    <section class="cg-panel ps" aria-label="Your progress">
      <div class="ps-ring" role="img" [attr.aria-label]="solved() + ' of ' + total() + ' problems solved'">
        <svg viewBox="0 0 96 96" aria-hidden="true">
          <circle cx="48" cy="48" [attr.r]="r" class="ps-track" />
          <circle cx="48" cy="48" [attr.r]="r" class="ps-fill" [attr.stroke-dasharray]="length" [attr.stroke-dashoffset]="length * (1 - (total() ? solved() / total() : 0))" />
        </svg>
        <div class="ps-ring-text"><strong>{{ solved() }}</strong><span>/{{ total() }}</span><small>Solved</small></div>
      </div>

      <ul class="ps-levels" aria-label="Solved by difficulty">
        @for (l of levels(); track l.level) {
          <li>
            <div class="ps-level-head"><span class="cg-diff" [attr.data-level]="l.level">{{ l.label }}</span>
              <span class="ps-num"><strong>{{ l.solved }}</strong>/{{ l.total }}</span></div>
            <div class="ps-bar" [attr.data-level]="l.level"><span [style.width.%]="l.total ? l.solved / l.total * 100 : 0"></span></div>
          </li>
        }
      </ul>

      <div class="ps-side">
        <div class="ps-stat"><span class="cg-kicker">Guides read</span><strong>{{ guides() }}<span>/{{ guideCount() }}</span></strong></div>
        <div class="ps-stat"><span class="cg-kicker">Overall</span><strong>{{ percent() }}<span>%</span></strong></div>
        @if (next(); as n) {
          <button type="button" class="cg-btn cg-btn-primary ps-continue" (click)="resume.emit(n.topic)">
            <span class="min-w-0 text-truncate">Continue: {{ n.title }}</span><i class="bi bi-arrow-right" aria-hidden="true"></i></button>
        } @else {
          <div class="ps-done"><i class="bi bi-trophy" aria-hidden="true"></i> Everything done</div>
        }
      </div>
    </section>`,
  styles: [`
    .ps { display: grid; grid-template-columns: auto minmax(0, 1.4fr) minmax(220px, 1fr); gap: 1.75rem; align-items: center; padding: 1.25rem 1.5rem; }
    .ps-ring { position: relative; width: 104px; height: 104px; }
    .ps-ring svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 6; }
    .ps-track { stroke: var(--cg-sub); }
    .ps-fill { stroke: var(--cg-ok); stroke-linecap: round; transition: stroke-dashoffset .6s ease; }
    .ps-ring-text { position: absolute; inset: 0; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; line-height: 1.1; }
    .ps-ring-text strong { font-size: 1.5rem; font-weight: 700; color: var(--pm-heading); }
    .ps-ring-text span { font-size: .85rem; color: var(--pm-muted); align-self: flex-end; padding-bottom: .2rem; }
    .ps-ring-text small { width: 100%; text-align: center; font-size: .72rem; color: var(--pm-muted); }
    .ps-levels { list-style: none; margin: 0; padding: 0; display: grid; gap: .8rem; }
    .ps-level-head { display: flex; justify-content: space-between; margin-bottom: .3rem; }
    .ps-num { font-size: .8rem; color: var(--pm-muted); font-variant-numeric: tabular-nums; }
    .ps-num strong { color: var(--pm-heading); }
    .ps-bar { height: 6px; border-radius: 999px; background: var(--cg-sub); overflow: hidden; }
    .ps-bar span { display: block; height: 100%; border-radius: 999px; transition: width .6s ease; }
    .ps-bar[data-level='EASY'] span { background: var(--cg-easy); }
    .ps-bar[data-level='MEDIUM'] span { background: var(--cg-medium); }
    .ps-bar[data-level='HARD'] span { background: var(--cg-hard); }
    .ps-side { display: grid; grid-template-columns: 1fr 1fr; gap: .9rem 1rem; padding-left: 1.5rem; border-left: 1px solid var(--cg-line); }
    .ps-stat { display: flex; flex-direction: column; gap: .15rem; }
    .ps-stat strong { font-size: 1.35rem; font-weight: 700; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .ps-stat strong span { font-size: .85rem; font-weight: 500; color: var(--pm-muted); }
    .ps-continue { grid-column: 1 / -1; justify-content: space-between; width: 100%; }
    .ps-done { grid-column: 1 / -1; font-weight: 600; color: var(--cg-ok); }
    @media (max-width: 991.98px) {
      .ps { grid-template-columns: auto minmax(0, 1fr); gap: 1.25rem; }
      .ps-side { grid-column: 1 / -1; padding-left: 0; border-left: 0; border-top: 1px solid var(--cg-line); padding-top: 1rem; }
    }
  `],
})
export class ProgressSummary {
  overview = input.required<Overview>();
  resume = output<string>();
  readonly r = R;
  readonly length = 2 * Math.PI * R;

  private all = computed(() => this.overview().topics.flatMap(t => t.problems));
  solved = computed(() => this.all().filter(p => p.solved).length);
  total = computed(() => this.all().length);
  guides = computed(() => this.overview().topics.filter(t => t.guideRead).length);
  guideCount = computed(() => this.overview().topics.filter(t => t.guideSlides > 0).length);
  percent = computed(() => {
    const t = this.overview().topics;
    const all = t.reduce((n, x) => n + x.total, 0);
    return all ? Math.round(t.reduce((n, x) => n + x.done, 0) / all * 100) : 0;
  });
  levels = computed(() => (['EASY', 'MEDIUM', 'HARD'] as Difficulty[]).map(level => {
    const list = this.all().filter(p => p.difficulty === level);
    return { level, label: level === 'EASY' ? 'Easy' : level === 'MEDIUM' ? 'Medium' : 'Hard', solved: list.filter(p => p.solved).length, total: list.length };
  }));
  /** The first topic with something left; its first unsolved problem (or its guide). */
  next = computed(() => {
    const t = this.overview().topics.find(x => x.done < x.total);
    if (!t) return null;
    const p = t.problems.find(x => !x.solved);
    return { topic: t.key, title: (t.guideSlides > 0 && !t.guideRead) || !p ? `${t.title} guide` : p.title };
  });
}
