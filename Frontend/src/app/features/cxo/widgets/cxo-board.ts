import { ChangeDetectionStrategy, Component, DestroyRef, afterNextRender, computed, effect, inject, input, signal } from '@angular/core';
import { CxoWidget } from './cxo-widget';
import { DashboardSpec, Insight, WidgetData, WidgetSpec } from '../cxo.models';

interface Cell { kind: 'insight' | 'widget'; w?: WidgetSpec; span: number; }

const GAP = 14;

/**
 * A leadership dashboard: a KPI band (the first tile is the wider mint hero), then a bento grid (4 columns on wide screens, 3 below)
 * with the AI summary first. Heights follow the content; leftover space in a row is shared out so there are no holes.
 */
@Component({
  selector: 'pm-cxo-board',
  imports: [CxoWidget],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (kpis().length) {
      <div class="xb-kpis" [style.grid-template-columns]="kpiCols()">
        @for (w of kpis(); track w.id; let i = $index) {
          <pm-cxo-widget [spec]="w" [data]="data()[w.id] ?? null" [hero]="i === 0" />
        }
      </div>
    }
    <div class="xb-grid" [style.grid-template-columns]="'repeat(' + cols() + ', minmax(0, 1fr))'">
      @for (c of cells(); track $index) {
        <div class="xb-cell" [style.grid-column]="cols() > 1 ? 'span ' + c.span : 'auto'">
          @if (c.kind === 'insight') {
            <article class="xb-ai" aria-live="polite">
              <div class="xb-ai-k"><span class="xb-ai-dot"></span>AI summary</div>
              @if (insight(); as i) {
                <p class="xb-ai-h">{{ typed() }}</p>
                @if (typed().length >= i.headline.length) {
                  <ul>@for (b of i.bullets; track $index) { <li [style.animation-delay.ms]="$index * 120">{{ b }}</li> }</ul>
                }
              } @else if (insightPending()) {
                <div class="xb-ai-wait"><span></span><span></span><span></span></div>
                <div class="xb-ai-note">Reading the numbers…</div>
              } @else {
                <div class="xb-ai-note">Ask AI a question and the key takeaways appear here.</div>
              }
            </article>
          } @else {
            <pm-cxo-widget [spec]="c.w!" [data]="data()[c.w!.id] ?? null" [height]="chartH" />
          }
        </div>
      }
    </div>`,
  styles: [`
    :host { display: block; }
    /* heights follow the content: rows are as tall as their tallest card, every card in a row stretches to match */
    .xb-kpis { display: grid; gap: ${GAP}px; margin-bottom: ${GAP}px; grid-auto-rows: minmax(140px, auto); }
    .xb-grid { display: grid; gap: ${GAP}px; grid-auto-rows: auto; align-items: stretch; }
    .xb-cell { min-width: 0; }
    @media (max-width: 1199.98px) { .xb-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; } }
    @media (max-width: 479.98px) { .xb-kpis { grid-template-columns: minmax(0, 1fr) !important; } }

    .xb-ai { position: relative; overflow: hidden; height: 100%; min-height: 290px; display: flex; flex-direction: column; padding: 1.15rem 1.25rem; border-radius: var(--x-radius, 22px);
      border: 1px solid transparent;
      background: linear-gradient(var(--x-panel), var(--x-panel)) padding-box,
                  linear-gradient(160deg, rgba(43,89,195,.35), rgba(15,23,42,.08) 45%) border-box;
      box-shadow: var(--x-shadow);
      animation: xbIn .45s var(--x-ease) backwards; }
    .xb-ai-k { display: inline-flex; align-items: center; gap: .5rem; font-size: .7rem; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; color: var(--x-accent); }
    .xb-ai-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--x-accent); box-shadow: 0 0 0 4px rgba(43,89,195,.12); }
    .xb-ai-h { margin: .9rem 0 0; font-size: clamp(1rem, 1.05vw, 1.2rem); font-weight: 600; line-height: 1.4; letter-spacing: -.012em; color: var(--x-text); }
    .xb-ai ul { margin: auto 0 0; padding: .85rem 0 0; list-style: none; display: grid; gap: .5rem; border-top: 1px solid var(--x-line); overflow: auto; }
    .xb-ai li { position: relative; padding-left: .95rem; font-size: .8rem; line-height: 1.45; color: var(--x-muted); animation: xbIn .35s ease backwards; }
    .xb-ai li::before { content: ''; position: absolute; left: 0; top: .5rem; width: 5px; height: 5px; border-radius: 2px; background: var(--x-accent); }
    .xb-ai-note { margin-top: auto; font-size: .82rem; color: var(--x-dim); }
    .xb-ai-wait { display: flex; gap: .35rem; margin-top: 1rem; }
    .xb-ai-wait span { width: 7px; height: 7px; border-radius: 50%; background: var(--x-accent); animation: xbDot 1.2s ease-in-out infinite; }
    .xb-ai-wait span:nth-child(2) { animation-delay: .15s; } .xb-ai-wait span:nth-child(3) { animation-delay: .3s; }
    @keyframes xbIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
    @keyframes xbDot { 0%, 100% { opacity: .25; } 50% { opacity: 1; } }
  `],
})
export class CxoBoard {
  spec = input.required<DashboardSpec>();
  data = input<Partial<Record<string, WidgetData>>>({});
  insight = input<Insight | null>(null);
  insightPending = input(false);
  /** Show the AI summary card (studio); saved dashboards can hide it. */
  showInsight = input(true);

  typed = signal('');
  /** Viewport width, for the number of columns. */
  private vw = signal(typeof window === 'undefined' ? 1440 : window.innerWidth);
  /** Chart height inside a card (cards grow with their content). */
  readonly chartH = 230;
  private timer?: ReturnType<typeof setInterval>;

  kpis = computed(() => this.spec().widgets.filter(w => w.type === 'KPI').slice(0, 4));
  /** The hero KPI is wider than the rest (bento). */
  kpiCols = computed(() => {
    const n = this.kpis().length;
    return n <= 1 ? '1fr' : `minmax(0, 1.55fr) repeat(${n - 1}, minmax(0, 1fr))`;
  });
  cols = computed(() => (this.vw() >= 1400 ? 4 : this.vw() >= 992 ? 3 : 1));

  cells = computed<Cell[]>(() => {
    const cols = this.cols();
    const items: Cell[] = [];
    if (this.showInsight()) items.push({ kind: 'insight', span: 1 });
    const kpiIds = new Set(this.kpis().map(k => k.id));
    for (const w of this.spec().widgets) if (!kpiIds.has(w.id)) items.push({ kind: 'widget', w, span: Math.min(cols, this.span(w)) });
    if (cols === 1) return items.map(c => ({ ...c, span: 1 }));
    // pack into rows of `cols`, in order, but pull a later item forward when it fits the room left in a row (fewer, fuller rows);
    // any room still left in a row is shared out among its items, narrowest first (no holes, no lone giant)
    const queue = [...items];
    const rows: Cell[][] = [];
    while (queue.length) {
      const row: Cell[] = [];
      let used = 0;
      for (let i = 0; i < queue.length && used < cols;) {
        if (used + queue[i].span <= cols) { used += queue[i].span; row.push({ ...queue.splice(i, 1)[0] }); }
        else i++;
      }
      if (!row.length) row.push({ ...queue.shift()!, span: cols });
      let free = cols - row.reduce((a, c) => a + c.span, 0);
      while (free > 0) {
        row.reduce((min, c) => (c.span < min.span ? c : min)).span++;
        free--;
      }
      rows.push(row);
    }
    return rows.flat();
  });

  /** Trends and long rankings take two columns; the rest one. */
  private span(w: WidgetSpec): number {
    if (w.type === 'LINE' || w.type === 'AREA') return 2;
    if ((w.type === 'BAR' || w.type === 'TABLE') && (w.dimension === 'COMPANY' || w.dimension === 'TEST')) return 2;
    return 1;
  }

  constructor() {
    const measure = () => this.vw.set(window.innerWidth);
    afterNextRender(() => {
      measure();
      window.addEventListener('resize', measure, { passive: true });
    });
    effect(() => {
      const text = this.insight()?.headline ?? '';
      clearInterval(this.timer);
      const reduce = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (!text || reduce) { this.typed.set(text); return; }
      let i = 0;
      this.typed.set('');
      this.timer = setInterval(() => {
        i = Math.min(text.length, i + 2);
        this.typed.set(text.slice(0, i));
        if (i >= text.length) clearInterval(this.timer);
      }, 16);
    });
    inject(DestroyRef).onDestroy(() => { clearInterval(this.timer); window.removeEventListener('resize', measure); });
  }
}
