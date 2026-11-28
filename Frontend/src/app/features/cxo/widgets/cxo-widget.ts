import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import type { Options, SeriesOptionsType } from 'highcharts';
import { CXO_COLORS, CxoChart } from './cxo-chart';
import { CxoDrill } from './cxo-drill';
import { CxoService } from '../cxo.service';
import { period } from '../cxo-coverage';
import { Dimension, Unit, WidgetData, WidgetSpec } from '../cxo.models';

/** Formats a number with its unit (12.5%, 6.4 LPA, 1,240). */
export function fmt(v: number | null | undefined, unit: Unit): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '–';
  const n = Number.isInteger(v) ? v.toLocaleString('en-IN') : v.toLocaleString('en-IN', { maximumFractionDigits: 1 });
  return unit === 'PERCENT' ? `${n}%` : unit === 'LPA' ? `${n} LPA` : unit === 'CGPA' ? `${n} CGPA` : n;
}

/** "2026-03" -> "Mar"; enum-like values -> "In process". */
function label(raw: string, dim: Dimension): string {
  if (dim === 'MONTH' && /^\d{4}-\d{2}$/.test(raw)) {
    const [y, m] = raw.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'short' });
  }
  if (/^[A-Z_]+$/.test(raw) && dim !== 'BRANCH' && raw.length > 3) return raw.charAt(0) + raw.slice(1).toLowerCase().replace(/_/g, ' ');
  return raw;
}

const MINT = '#a67c1e';  // the accent gold: the leading bar
const LIME = '#a67c1e';  // trend line
const TRACK = '#eef1f5';

/**
 * One leadership widget: a KPI tile (the first one is the mint hero), a chart or a short list. Shows a skeleton until its data
 * arrives, so the dashboard visibly fills in while it is built.
 */
@Component({
  selector: 'pm-cxo-widget',
  imports: [CxoChart, CxoDrill],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (spec().type === 'KPI') {
      <article class="xk" [class.xk-hero]="hero()" [class.xk-click]="canDrill()" [attr.aria-busy]="!data()"
        (click)="canDrill() && data() && openDrill(null)">
        <div class="xk-label">{{ spec().title }}@if (info()) { <button type="button" class="xw-info" (mouseenter)="showTip($event)" (mouseleave)="tip.set(null)" (focus)="showTip($event)" (blur)="tip.set(null)" (click)="$event.stopPropagation()" aria-label="What data is this?"><i class="bi bi-info-lg"></i></button> }</div>
        @if (canDrill()) { <span class="xk-drill" title="Click to see the students behind this number"><i class="bi bi-people"></i></span> }
        @if (data()) {
          <div class="xk-row">
            <div class="xk-num">{{ shown() }}</div>
            @if (delta(); as c) {
              <div class="xk-delta" [class.xk-good]="c.good === true" [class.xk-bad]="c.good === false" [attr.title]="c.vs + ': ' + c.prev">
                <b>{{ c.arrow }} {{ c.text }}</b><span>vs {{ c.vs }}</span>
              </div>
            }
          </div>
          @if (data()!.unit === 'PERCENT' && data()!.value !== null) {
            <div class="xk-meter" aria-hidden="true"><span [style.width.%]="meter()"></span></div>
          }
          <div class="xk-sub"><span>{{ subtitle() }}</span>@if (scope()) { <span class="xk-scope">{{ scope() }}</span> }</div>
        } @else { <div class="x-skel xk-skel"></div><div class="x-skel xk-skel-sm"></div> }
        @if (hero()) { <span class="xk-arrow" aria-hidden="true"><i class="bi bi-arrow-up-right"></i></span> }
      </article>
    } @else {
      <article class="xw" [attr.aria-busy]="!data()">
        <header class="xw-head">
          <h3>{{ spec().title }}@if (info()) { <button type="button" class="xw-info" (mouseenter)="showTip($event)" (mouseleave)="tip.set(null)" (focus)="showTip($event)" (blur)="tip.set(null)" aria-label="What data is this?"><i class="bi bi-info-lg"></i></button> }</h3>
          <span class="xw-head-r">
            @if (canDrill()) { <span class="xw-drill" title="Click a bar or slice to see its students"><i class="bi bi-people"></i></span> }
            @if (headline(); as h) { <span class="xw-figure">{{ h }}</span> }
          </span>
        </header>
        <div class="xw-scope">{{ caption() }}@if (scope()) { · {{ scope() }} }</div>
        <div class="xw-body">
          @if (!data()) {
            <div class="xw-skel"><span></span><span></span><span></span><span></span><span></span><span></span><span></span></div>
          } @else if (empty()) {
            <div class="xw-empty">No data for this view yet</div>
          } @else if (spec().type === 'TABLE') {
            <ol class="xw-list">
              @for (r of rows(); track $index) {
                <li><span>{{ r.label }}@if (r.meta) { <small>{{ r.meta }}</small> }</span><b>{{ r.value }}</b></li>
              }
            </ol>
          } @else if (spec().type === 'DONUT') {
            <div class="xw-donut">
              @for (k of [chartKey()]; track k) { <pm-cxo-chart [options]="options()" [height]="height() - 20" [label]="spec().title" /> }
              <ul class="xw-legend">
                @for (r of rows().slice(0, 6); track $index) {
                  <li><i [style.background]="colors[$index % colors.length]"></i><span>{{ r.label }}</span><b>{{ r.value }}</b></li>
                }
              </ul>
            </div>
          } @else {
            @for (k of [chartKey()]; track k) { <pm-cxo-chart [options]="options()" [height]="height()" [label]="spec().title" /> }
          }
        </div>
      </article>
    }
    @if (tip(); as t) {
      <div class="xw-about xw-float" role="tooltip" [style.left.px]="t.x" [style.top.px]="t.y"><b>{{ info()?.label }}</b>{{ aboutText() }}<div class="xw-chips">@for (c of chips(); track c) { <span>{{ c }}</span> }</div></div>
    }
    @if (drill() !== null) { <pm-cxo-drill [spec]="spec()" [label]="drill()!.label" (closed)="drill.set(null)" /> }`,
  styles: [`
    .xk-click { cursor: pointer; }
    .xw-info { all: unset; cursor: pointer; margin-left: .45rem; width: 16px; height: 16px; border-radius: 50%; display: inline-grid; place-items: center; vertical-align: middle;
      font-size: .58rem; color: var(--x-muted); background: rgba(15, 23, 42, .06); transition: background-color .15s ease, color .15s ease; }
    .xw-info:hover, .xw-info:focus-visible { color: #fff; background: var(--x-accent, #a67c1e); }
    .xw-info:focus-visible { outline: 2px solid var(--x-accent); outline-offset: 2px; }
    .xw-about { padding: .65rem .8rem; border-radius: 12px; background: var(--x-panel-2); font-size: .74rem; line-height: 1.5; color: var(--x-muted); text-align: left; font-weight: 400; letter-spacing: 0; text-transform: none; }
    .xw-float { position: fixed; z-index: 1040; width: 290px; pointer-events: none; background: #fff; border: 1px solid var(--x-line-2); box-shadow: 0 16px 40px -10px rgba(15, 23, 42, .28); animation: xwTip .14s ease backwards; }
    @keyframes xwTip { from { opacity: 0; transform: translateY(4px); } }
    .xw-about b { display: block; margin-bottom: .15rem; color: var(--x-text); font-size: .78rem; }
    .xw-chips { display: flex; flex-wrap: wrap; gap: .3rem; margin-top: .45rem; }
    .xw-chips span { padding: .1rem .5rem; border-radius: 999px; font-size: .68rem; font-weight: 600; color: var(--x-accent, #a67c1e); background: var(--x-accent-soft, rgba(166,124,30,.1)); }
    .xk-drill, .xw-drill { display: inline-grid; place-items: center; width: 26px; height: 26px; border-radius: 9px; font-size: .8rem; color: var(--x-muted); background: rgba(15, 23, 42, .05); }
    .xk-drill { position: absolute; top: .85rem; right: 1rem; }
    .xk-hero .xk-drill { color: #fff; background: rgba(255, 255, 255, .18); }
    .xk-hero .xw-info { color: #fff; background: rgba(255, 255, 255, .2); }
    .xw-head-r { display: inline-flex; align-items: center; gap: .5rem; flex-shrink: 0; }
    .xk-click { cursor: pointer; }
    :host { display: block; height: 100%; }
    /* ---------- KPI ---------- */
    .xk { position: relative; overflow: hidden; height: 100%; min-height: 128px; padding: 1.1rem 1.25rem; border-radius: var(--x-radius, 22px);
      border: 1px solid transparent; background: linear-gradient(var(--x-panel), var(--x-panel)) padding-box, var(--x-edge) border-box;
      display: flex; flex-direction: column; animation: xIn .45s var(--x-ease) backwards; container-type: size; box-shadow: var(--x-shadow); }
    .xk-label { font-size: .78rem; font-weight: 600; color: var(--x-muted); letter-spacing: .01em; padding-right: 2.5rem; }
    .xk-num { margin-top: auto; font-size: clamp(1.7rem, 28cqh, 2.6rem); font-weight: 700; letter-spacing: -.035em; line-height: 1.05; font-variant-numeric: tabular-nums; color: var(--x-text); }
    .xk-sub { display: flex; align-items: center; gap: .5rem; margin-top: .45rem; font-size: .72rem; color: var(--x-muted); }
    .xk-scope { padding: .1rem .45rem; border-radius: 999px; background: var(--x-panel-2); }
    .xk-row { margin-top: auto; display: flex; align-items: flex-end; gap: .7rem; min-width: 0; }
    .xk-row .xk-num { margin-top: 0; }
    .xk-delta { display: flex; flex-direction: column; min-width: 0; padding-bottom: .3rem; font-size: .72rem; line-height: 1.25; color: var(--x-muted); }
    .xk-delta b { font-weight: 700; white-space: nowrap; }
    .xk-delta span { font-weight: 500; opacity: .8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .xk-good { color: var(--x-up); } .xk-bad { color: var(--x-down); }
    .xk-hero .xk-delta { color: rgba(255,255,255,.95); }
    .xk-skel { height: 2rem; width: 60%; margin-top: auto; } .xk-skel-sm { height: .65rem; width: 35%; margin-top: .55rem; }
    .xk-hero { color: var(--x-accent-ink); border: 0;
      background: radial-gradient(120% 120% at 100% 0%, rgba(255,255,255,.08), transparent 50%), linear-gradient(145deg, #1f2937, #111827 60%, #0b1120);
      box-shadow: 0 14px 30px -18px rgba(15,23,42,.6), inset 0 1px 0 rgba(255,255,255,.08); }
    .xk-hero .xk-label { color: rgba(255,255,255,.72); font-weight: 600; }
    .xk-hero .xk-num { color: var(--x-accent-ink); font-size: clamp(2rem, 34cqh, 3.1rem); }
    .xk-meter { height: 6px; margin-top: .6rem; border-radius: 999px; background: rgba(15,23,42,.07); overflow: hidden; }
    .xk-meter span { display: block; height: 100%; border-radius: 999px; background: var(--x-accent);
      transition: width .9s var(--x-ease); }
    .xk-hero .xk-meter { background: rgba(255,255,255,.16); } .xk-hero .xk-meter span { background: #ffffff; }
    .xk-hero .xk-sub { color: rgba(255,255,255,.72); }
    .xk-hero .xk-sub > span:first-child { padding: .18rem .55rem; border-radius: 999px; background: rgba(255,255,255,.14); color: #ffffff; font-weight: 600; }
    .xk-hero .xk-scope { background: rgba(255,255,255,.1); }
    .xk-hero .x-skel { background: rgba(255,255,255,.25); animation: none; }
    .xk-arrow { position: absolute; top: .8rem; right: .8rem; width: 2.1rem; height: 2.1rem; border-radius: 50%; display: grid; place-items: center;
      background: rgba(255,255,255,.12); color: #ffffff; font-size: .85rem; }
    /* ---------- chart card ---------- */
    .xw { height: 100%; display: flex; flex-direction: column; padding: 1.05rem 1.2rem .7rem; border-radius: var(--x-radius, 22px);
      border: 1px solid transparent; background: linear-gradient(var(--x-panel), var(--x-panel)) padding-box, var(--x-edge) border-box;
      animation: xIn .45s var(--x-ease) backwards; transition: background .25s ease, box-shadow .25s ease; box-shadow: var(--x-shadow); }
    .xw:hover { background: linear-gradient(var(--x-panel), var(--x-panel)) padding-box, linear-gradient(180deg, var(--x-accent-line), var(--x-line-2) 55%) border-box;
      box-shadow: 0 8px 24px -14px rgba(15,23,42,.18); }
    .xw-head { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
    .xw-head h3 { margin: 0; font-size: .95rem; font-weight: 600; letter-spacing: -.01em; color: var(--x-text); }
    .xw-figure { font-size: 1.25rem; font-weight: 700; letter-spacing: -.02em; color: var(--x-text); font-variant-numeric: tabular-nums; white-space: nowrap; }
    .xw-scope { font-size: .72rem; color: var(--x-muted); margin-top: .1rem; }
    .xw-body { flex: 1; min-height: 0; margin-top: .5rem; }
    .xw-empty { height: 100%; min-height: 160px; display: grid; place-items: center; color: var(--x-dim); font-size: .82rem; }
    .xw-skel { height: 170px; display: flex; align-items: flex-end; gap: 4%; padding: .5rem 2% 0; }
    .xw-skel span { flex: 1; border-radius: 8px 8px 3px 3px; background: #eef0f3; animation: xBar 1.4s ease-in-out infinite; }
    .xw-skel span:nth-child(1) { height: 40%; } .xw-skel span:nth-child(2) { height: 62%; animation-delay: .08s; } .xw-skel span:nth-child(3) { height: 48%; animation-delay: .16s; }
    .xw-skel span:nth-child(4) { height: 80%; animation-delay: .24s; } .xw-skel span:nth-child(5) { height: 58%; animation-delay: .32s; }
    .xw-skel span:nth-child(6) { height: 90%; animation-delay: .4s; } .xw-skel span:nth-child(7) { height: 66%; animation-delay: .48s; }
    .xw-list { list-style: none; margin: 0; padding: 0 .2rem 0 0; display: grid; align-content: start; gap: .15rem; max-height: 250px;
      overflow: auto; scrollbar-width: thin; scrollbar-color: rgba(15,23,42,.18) transparent; }
    .xw-list li span { min-width: 0; color: var(--x-text); }
    .xw-list li small { display: block; margin-top: .1rem; font-size: .72rem; color: var(--x-dim); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .xw-list li b { white-space: nowrap; align-self: center; }
    .xw-list li { display: flex; justify-content: space-between; gap: 1rem; padding: .5rem .6rem; border-radius: 10px; font-size: .84rem; color: var(--x-muted); }
    .xw-list li:nth-child(odd) { background: var(--x-panel-2); }
    .xw-list b { color: var(--x-text); font-weight: 600; font-variant-numeric: tabular-nums; }
    .xw-donut { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); align-items: center; gap: 1rem; width: 100%; max-width: 560px; margin: 0 auto; }
    .xw-legend { list-style: none; margin: 0; padding: 0; display: grid; gap: .45rem; }
    .xw-legend li { display: grid; grid-template-columns: 8px 1fr auto; align-items: center; gap: .55rem; font-size: .78rem; color: var(--x-muted); }
    .xw-legend li span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .xw-legend i { width: 8px; height: 8px; border-radius: 3px; }
    .xw-legend b { color: var(--x-text); font-weight: 600; font-variant-numeric: tabular-nums; }
    @keyframes xIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
    @keyframes xBar { 0%, 100% { opacity: .45; } 50% { opacity: 1; } }
  `],
})
export class CxoWidget {
  spec = input.required<WidgetSpec>();
  data = input<WidgetData | null>(null);
  /**
   * What the chart shows. A different metric, split, chart type or filter gets a fresh chart: morphing one Highcharts chart into
   * an unrelated one leaves stray bars and labels behind. New numbers for the same widget (live refresh) still animate in place.
   */
  chartKey = computed(() => {
    const s = this.spec();
    return [s.metric, s.dimension, s.type, s.batchYear, (s.batches ?? []).join(','), s.branchCode, s.from, s.to, s.limit].join('|');
  });
  /** The first KPI of a dashboard is drawn as the mint hero tile. */
  hero = input(false);
  height = input(210);
  readonly colors = CXO_COLORS;

  shown = signal('–');

  private cxo = inject(CxoService);
  /** Where the hover note sits (under the i button): fixed to the screen so a card's edge never cuts it. */
  tip = signal<{ x: number; y: number } | null>(null);
  showTip(e: Event): void {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    this.tip.set({ x: Math.max(8, Math.min(r.left - 12, window.innerWidth - 306)), y: r.bottom + 8 });
  }
  /** A shared (public) board is read only: no student lists, no extra lookups. */
  interactive = input(true);
  drill = signal<{ label: string | null } | null>(null);
  info = computed(() => (this.interactive() ? this.cxo.metrics()[this.spec().metric] : undefined));
  /** What the number is: the metric's own definition. */
  aboutText = computed(() => ' ' + (this.info()?.description ?? 'A number from this college\'s ProveYu records.'));
  /** The data the card shows, as small tags: which batches, branch, period and split. */
  chips = computed(() => {
    const s = this.spec();
    const out = [s.batches?.length ? `Batches ${s.batches.join(', ')}` : s.batchYear ? `Batch ${s.batchYear}` : 'All batches', s.branchCode ?? 'All branches', period(s.from, s.to)];
    if (s.dimension !== 'NONE') out.push(`By ${s.dimension.toLowerCase().replace('_', ' ')}`);
    return out.filter(Boolean);
  });
  canDrill = computed(() => this.interactive() && !!this.info()?.drillable && this.spec().dimension !== 'STUDENT');
  openDrill(label: string | null): void { if (this.canDrill()) this.drill.set({ label }); }
  private raf = 0;

  scope = computed(() => [this.spec().batches?.length ? `Batches ${this.spec().batches!.join(', ')}` : this.spec().batchYear ? `Batch ${this.spec().batchYear}` : '', this.spec().branchCode ?? '',
    period(this.spec().from, this.spec().to)].filter(Boolean).join(' · '));
  empty = computed(() => {
    const d = this.data();
    // a student list is not empty just because CGPAs are missing: the names are the content
    if (!d || !d.labels.length) return true;
    if (this.spec().dimension === 'STUDENT') return false;
    // a rate or an average of 0 is a real number; only counts that are all zero mean "nothing recorded yet"
    return d.unit === 'COUNT' ? d.values.every(v => !v) : d.values.every(v => v === null);
  });
  rows = computed(() => {
    const d = this.data();
    const dim = this.spec().dimension;
    return d ? d.labels.map((l, i) => {
      // a student row is "Name (roll) · BRANCH · batch": the name on top, the rest small underneath
      const cut = dim === 'STUDENT' ? l.indexOf(' (') : -1;
      return cut > 0 ? { label: l.slice(0, cut), meta: l.slice(cut + 1), value: fmt(d.values[i], d.unit) }
        : { label: label(l, dim), meta: '', value: fmt(d.values[i], d.unit) };
    }) : [];
  });
  /**
   * Change against the previous batch / same period last year: points for rates, % for the rest (or the plain difference when the
   * previous value was 0). For unplaced students and proctoring flags a rise is bad, so the colours flip.
   */
  delta = computed(() => {
    const d = this.data();
    if (!d || d.value === null || d.previous === null || d.previous === undefined || !d.previousLabel) return null;
    const diff = d.value - d.previous;
    const abs = Math.abs(diff);
    const text = d.unit === 'PERCENT' ? `${Math.round(abs * 10) / 10} pts`
      : d.previous === 0 ? fmt(abs, d.unit) : `${Math.round((abs / Math.abs(d.previous)) * 1000) / 10}%`;
    const riseIsBad = ['unplaced_students', 'unplaced_list', 'proctoring_flags', 'flagged_attempts'].includes(this.spec().metric);
    return { arrow: diff > 0 ? '▲' : diff < 0 ? '▼' : '=', text: diff === 0 ? 'no change' : text, vs: d.previousLabel, prev: fmt(d.previous, d.unit),
      good: diff === 0 ? null : (diff > 0) !== riseIsBad };
  });
  /** 0..100 for the KPI meter (percent KPIs). */
  meter = computed(() => Math.max(0, Math.min(100, this.data()?.value ?? 0)));
  /** Short line under a chart title: how many items it shows. */
  caption = computed(() => {
    const s = this.spec();
    const n = this.data()?.labels.length ?? 0;
    const noun: Record<string, string> = { BRANCH: 'branches', BATCH: 'batches', MONTH: 'months', COMPANY: 'companies', STATUS: 'statuses',
      TYPE: 'types', TIER: 'tiers', CTC_BAND: 'package bands', TEST: 'tests', DIFFICULTY: 'levels', STAGE: 'stages', ROUND: 'rounds',
      STUDENT: 'students', ANNOUNCEMENT: 'announcements', JOB: 'jobs', YEAR: 'years', LANGUAGE: 'languages', GENDER: 'groups', CGPA_BAND: 'CGPA bands' };
    if (!this.data()) return 'Loading';
    if (s.dimension === 'MONTH') return n ? `${n} months` : 'Monthly';
    if (s.dimension === 'STAGE') return 'Eligible to placed';
    if (s.dimension === 'STUDENT') return n ? `${n} students · highest CGPA first` : 'No students';
    const plural = noun[s.dimension] ?? 'items';
    const one: Record<string, string> = { branches: 'branch', batches: 'batch', statuses: 'status', companies: 'company', groups: 'group' };
    return n ? `${n} ${n === 1 ? one[plural] ?? plural.replace(/s$/, '') : plural}` : 'No data yet';
  });
  subtitle = computed(() => {
    const u = this.data()?.unit;
    if (this.spec().type === 'KPI' && this.data() && this.data()!.value === null) return 'No data for this period';
    return u === 'PERCENT' ? 'Percent' : u === 'LPA' ? 'Lakh per annum' : u === 'SCORE' ? 'Out of 100' : 'Total';
  });
  /** The figure beside a chart title: the sum for counts, the top value otherwise. */
  headline = computed(() => {
    const d = this.data();
    if (d && !this.empty() && this.spec().dimension === 'STUDENT') return String(d.labels.length);
    if (!d || this.empty() || this.spec().type === 'TABLE') return '';
    const vals = d.values.filter((v): v is number => v !== null);
    if (!vals.length) return '';
    if (d.unit === 'COUNT' && this.spec().dimension !== 'STAGE') return fmt(vals.reduce((a, b) => a + b, 0), d.unit);
    if (this.spec().dimension === 'STAGE') return '';
    return 'Top ' + fmt(Math.max(...vals), d.unit);
  });

  options = computed<Options>(() => {
    const s = this.spec();
    const d = this.data();
    if (!d) return {};
    const cats = d.labels.map(l => label(l, s.dimension));
    const values = d.values.map(v => (v === null ? 0 : Number(v)));
    const suffix = d.unit === 'PERCENT' ? '%' : d.unit === 'LPA' ? ' LPA' : '';
    const max = Math.max(...values, 0);
    const tooltip = { valueSuffix: suffix, headerFormat: '<span style="font-size:11px;color:#64748b">{point.key}</span><br/>',
      pointFormat: '<b style="font-size:15px">{point.y}' + suffix + '</b>' };

    if (s.type === 'DONUT') {
      const total = values.reduce((a, b) => a + b, 0);
      return {
        chart: { type: 'pie', animation: { duration: 900 } },
        title: { text: d.unit === 'COUNT' ? fmt(total, 'COUNT') : '', verticalAlign: 'middle', y: 8,
          style: { color: '#0f172a', fontSize: '22px', fontWeight: '700' } },
        tooltip: { ...tooltip, pointFormat: '<b style="font-size:15px">{point.y}' + suffix + '</b> · {point.percentage:.0f}%' },
        plotOptions: { pie: { innerSize: '76%', borderWidth: 3, borderColor: '#ffffff', borderRadius: 6, dataLabels: { enabled: false }, ...this.pointClick() } },
        series: [{ type: 'pie', name: s.title, data: cats.map((name, i) => ({ name, y: values[i] })) }],
      };
    }

    if (s.type === 'LINE' || s.type === 'AREA') {
      // keep empty months as gaps (an average of no offers is not 0); counts already arrive as 0
      const raw = d.values.map(v => (v === null ? null : Number(v)));
      const last = raw.length - 1;
      return {
        chart: { type: 'areaspline', animation: { duration: 900 } },
        tooltip,
        xAxis: { categories: cats, tickLength: 0, lineWidth: 0 },
        yAxis: { gridLineDashStyle: 'Dot', labels: { format: '{value}' + suffix } },
        plotOptions: { areaspline: { lineWidth: 2.5, color: LIME, threshold: null, marker: { enabled: false },
          fillColor: { linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 }, stops: [[0, 'rgba(166,124,30,.16)'], [1, 'rgba(166,124,30,0)']] } } },
        series: [{ type: 'areaspline', name: s.title,
          connectNulls: true,
          // the latest point is marked; with only a few real points every point is marked so a lone month is still visible
          data: raw.map((y, i) => y !== null && (i === last || raw.filter(v => v !== null).length <= 3)
            ? { y, marker: { enabled: true, radius: 5, fillColor: LIME, lineColor: '#ffffff', lineWidth: 3 } } : y) } as SeriesOptionsType],
      };
    }

    // bars and columns: a dark track behind every bar, the leading bar in full mint, the rest softer
    const horizontal = s.type === 'BAR';
    const type = horizontal ? 'bar' : 'column';
    const grad = horizontal ? { x1: 0, y1: 0, x2: 1, y2: 0 } : { x1: 0, y1: 1, x2: 0, y2: 0 };
    const soft = '#ecd8a8';   // the rest: a light shade of the accent
    const strong = MINT;      // the leading bar
    return {
      chart: { type, animation: { duration: 900 } },
      tooltip: { ...tooltip, shared: false },
      xAxis: { categories: cats, tickLength: 0, lineWidth: 0, labels: { style: { color: '#64748b', fontSize: '11px' } } },
      yAxis: { visible: !horizontal, gridLineDashStyle: 'Dot', labels: { format: '{value}' + suffix }, max: max || 1 },
      plotOptions: {
        series: { grouping: false, borderWidth: 0, animation: { duration: 900 }, ...this.pointClick() } as never,
        bar: { borderRadius: 7, pointPadding: 0.18, groupPadding: 0.06, maxPointWidth: 22 },
        column: { borderRadius: 8, pointPadding: 0.14, groupPadding: 0.1, maxPointWidth: 44 },
      },
      series: [
        { type, name: 'track', data: values.map(() => max || 1), color: TRACK, enableMouseTracking: false, states: { inactive: { opacity: 1 } }, animation: false },
        { type, name: s.title, data: values.map(y => ({ y, color: y === max && max > 0 ? strong : soft,
          // white figure on the dark leading bar, dark figure on the light ones
          dataLabels: { style: { color: y === max && max > 0 ? '#ffffff' : '#262a4a' } } })),
          dataLabels: { enabled: horizontal, align: 'right', inside: true, format: '{y}' + suffix,
            style: { color: '#262a4a', textOutline: 'none', fontWeight: '700', fontSize: '11px' } } },
      ] as SeriesOptionsType[],
    } as Options;
  });

  /** Clicking a bar or slice lists its students (only where the number is counted over students). */
  private pointClick(): Record<string, unknown> {
    if (!this.canDrill() || this.spec().dimension === 'MONTH') return {};
    const labels = this.data()?.labels ?? [];
    const open = (i: number) => this.openDrill(labels[i] ?? null);
    return { cursor: 'pointer', point: { events: { click(this: { index: number }) { open(this.index); } } } };
  }

  constructor() {
    // KPI count-up whenever a new value arrives (first build or live refresh)
    let from = 0;
    effect(() => {
      const d = this.data();
      if (!d || this.spec().type !== 'KPI') return;
      const target = d.value ?? null;
      cancelAnimationFrame(this.raf);
      if (target === null) { this.shown.set('–'); return; }
      const reduce = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      const start = performance.now(), dur = reduce ? 0 : 900, begin = from;
      const step = (now: number) => {
        const t = dur ? Math.min(1, (now - start) / dur) : 1;
        const v = begin + (target - begin) * (1 - Math.pow(1 - t, 3));
        this.shown.set(fmt(t === 1 ? target : Number.isInteger(target) ? Math.round(v) : Math.round(v * 10) / 10, d.unit));
        if (t < 1) this.raf = requestAnimationFrame(step); else from = target;
      };
      this.raf = requestAnimationFrame(step);
    });
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.raf));
  }
}
