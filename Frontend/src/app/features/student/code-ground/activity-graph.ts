import { ChangeDetectionStrategy, Component, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { Activity } from './code-ground.models';
import { CodeGroundService } from './code-ground.service';

interface Cell { date: string; count: number; level: number; title: string; }
const DAY = 86_400_000;
const nice = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const month = new Intl.DateTimeFormat('en-IN', { month: 'short', timeZone: 'UTC' });

/** One year of practice, a square per day (weeks are columns, Sunday on top), darker for more problems; with the streaks. */
@Component({
  selector: 'pm-activity-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    @if (failed()) {
      <section class="cg-panel ag">The activity graph could not be loaded. <button type="button" class="cg-btn ms-2" (click)="ngOnInit()">Try again</button></section>
    }
    @if (data(); as d) {
      <section class="cg-panel ag" aria-label="Your practice over the last year">
        <header class="ag-head">
          <div class="ag-total"><strong>{{ total() }}</strong> solved in all · active on <strong>{{ d.activeDays }}</strong> {{ d.activeDays === 1 ? 'day' : 'days' }} in the last year</div>
          <div class="ag-streaks">
            <span class="ag-now" [class.ag-hot]="d.currentStreak > 0"><i class="bi bi-fire" aria-hidden="true"></i> Current streak <strong>{{ d.currentStreak }}</strong></span>
            <span>Longest <strong>{{ d.longestStreak }}</strong></span>
          </div>
        </header>
        <div class="ag-scroll" #scroll tabindex="0" role="img"
             [attr.aria-label]="'Practice calendar: ' + d.activeDays + ' active days, current streak ' + d.currentStreak + ', longest ' + d.longestStreak">
          <div class="ag-months" aria-hidden="true">@for (m of months(); track $index) { <span [style.grid-column]="m.column">{{ m.label }}</span> }</div>
          <div class="ag-grid" aria-hidden="true">
            @for (c of cells(); track c.date) { <span class="ag-cell" [attr.data-level]="c.level" [attr.title]="c.title" [attr.data-date]="c.date"></span> }
          </div>
        </div>
        <footer class="ag-foot">
          <span>A day counts when you solve a new problem or the problem of the day.</span>
          <span class="ag-legend" aria-hidden="true">Less @for (l of [0, 1, 2, 3, 4]; track l) { <span class="ag-cell" [attr.data-level]="l"></span> } More</span>
        </footer>
      </section>
    }`,
  styles: [`
    :host { display: block; min-width: 0; }
    .ag { padding: 1rem 1.1rem; height: 100%; }
    .ag-head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem 1rem; margin-bottom: .75rem; font-size: .84rem; color: var(--pm-muted); }
    .ag-head strong { color: var(--pm-heading); font-size: 1rem; font-variant-numeric: tabular-nums; }
    .ag-streaks { display: flex; gap: 1rem; }
    .ag-hot i { color: #ea580c; }
    .ag-scroll { overflow-x: auto; padding-bottom: .25rem; }
    .ag-scroll:focus-visible { outline: 2px solid var(--pm-link); }
    .ag-months, .ag-grid { display: grid; grid-auto-columns: 11px; column-gap: 3px; width: max-content; }
    .ag-months { grid-auto-flow: column; height: 16px; font-size: .66rem; color: var(--pm-muted); }
    .ag-months span { grid-row: 1; white-space: nowrap; }
    .ag-grid { grid-template-rows: repeat(7, 11px); grid-auto-flow: column; row-gap: 3px; }
    .ag-cell { display: inline-block; width: 11px; height: 11px; border-radius: 2px; background: var(--cg-sub); box-shadow: inset 0 0 0 1px rgba(127,127,127,.12); }
    .ag-cell[data-level='1'] { background: color-mix(in srgb, var(--cg-ok) 35%, var(--cg-sub)); }
    .ag-cell[data-level='2'] { background: color-mix(in srgb, var(--cg-ok) 55%, var(--cg-sub)); }
    .ag-cell[data-level='3'] { background: color-mix(in srgb, var(--cg-ok) 78%, var(--cg-sub)); }
    .ag-cell[data-level='4'] { background: var(--cg-ok); }
    .ag-foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem; margin-top: .6rem; font-size: .72rem; color: var(--pm-muted); }
    .ag-legend { display: inline-flex; align-items: center; gap: 3px; }
  `],
})
export class ActivityGraph implements OnInit {
  private api = inject(CodeGroundService);
  private scroll = viewChild<ElementRef<HTMLElement>>('scroll');
  data = signal<Activity | null>(null);
  failed = signal(false);

  cells = computed<Cell[]>(() => {
    const d = this.data();
    if (!d) return [];
    const counts = new Map(d.days.map(x => [x.date, x.count]));
    const cells: Cell[] = [];
    for (let t = Date.parse(d.from); t <= Date.parse(d.to); t += DAY) {
      const date = new Date(t).toISOString().slice(0, 10);
      const count = counts.get(date) ?? 0;
      const level = count === 0 ? 0 : count === 1 ? 1 : count === 2 ? 2 : count <= 4 ? 3 : 4;
      cells.push({ date, count, level, title: `${count || 'No'} ${count === 1 ? 'problem' : 'problems'} on ${nice.format(t)}` });
    }
    return cells;
  });
  /** A month's name above the first week that starts in it. */
  months = computed(() => {
    const labels: { column: number; label: string }[] = [];
    let previous = '';
    const cells = this.cells();
    for (let i = 0; i < cells.length; i += 7) {
      const label = month.format(Date.parse(cells[i].date));
      const column = i / 7 + 1;
      if (label !== previous) {
        // two names would overlap when a month shows for only a week or two at the left edge
        if (labels.length && column - labels[labels.length - 1].column < 3) labels.pop();
        labels.push({ column, label });
      }
      previous = label;
    }
    return labels;
  });
  total = computed(() => this.data()?.solved ?? 0);

  constructor() {
    // today is on the right: show it when the year does not fit
    effect(() => {
      const el = this.scroll()?.nativeElement;
      if (el && this.cells().length) el.scrollLeft = el.scrollWidth;
    });
  }

  ngOnInit(): void {
    this.failed.set(false);
    this.api.activity().subscribe({ next: a => this.data.set(a), error: () => this.failed.set(true) });
  }
}
