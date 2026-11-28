import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import type { Options } from 'highcharts';
import { CxoService } from '../cxo.service';
import { CxoChart } from '../widgets/cxo-chart';
import { Forecast } from '../cxo.models';
import { shortDate } from '../cxo-coverage';

/** A rough projection of how many students of a batch will be placed by the end of the year, with the reasoning shown. */
@Component({
  selector: 'pm-cxo-forecast',
  imports: [CxoChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="fc-head">
      <div><h1>Forecast</h1><div class="fc-sub">A rough estimate from the recent pace, not a promise.</div></div>
      @if (batches().length) {
        <label class="fc-pick">Batch
          <select (change)="load(+$any($event.target).value)">
            @for (b of batches(); track b) { <option [value]="b" [selected]="b === f()?.batch">{{ b }}</option> }
          </select></label>
      }
    </header>
    @if (loading()) { <div class="x-skel" style="height: 300px; border-radius: 20px"></div> }
    @else if (error()) { <div class="x-card x-empty">{{ error() }}</div> }
    @else if (f(); as x) {
      @if (x.eligible === 0) { <div class="x-card x-empty">No eligible students are recorded for batch {{ x.batch }}, so there is nothing to project.</div> }
      @else {
        <div class="fc-kpis">
          <article class="x-card fc-k"><div class="fc-l">Placed so far</div><div class="fc-n">{{ x.placed }} <small>of {{ x.eligible }}</small></div><div class="fc-d">{{ x.placedPercent }}%</div></article>
          <article class="x-card fc-k fc-main"><div class="fc-l">Likely by {{ short(x.until) }}</div><div class="fc-n">{{ x.projectedBase }} <small>of {{ x.eligible }}</small></div>
            <div class="fc-d">{{ x.projectedBasePercent }}% · range {{ x.projectedLow }} to {{ x.projectedHigh }}</div></article>
          <article class="x-card fc-k"><div class="fc-l">Recent pace</div><div class="fc-n">{{ x.perMonthBase }} <small>students a month</small></div>
            <div class="fc-d">Last 30 days: {{ x.recent30 }} · before that: {{ x.recent60 }}, {{ x.recent90 }}</div></article>
          <article class="x-card fc-k"><div class="fc-l">Confidence</div><div class="fc-n" [class.fc-low]="x.confidence === 'LOW'">{{ x.confidence === 'LOW' ? 'Low' : 'Fair' }}</div>
            <div class="fc-d">{{ x.confidence === 'LOW' ? 'Few recent placements' : 'Steady recent pace' }}</div></article>
        </div>
        <section class="x-card fc-chart"><h2>Students placed, past and projected</h2><pm-cxo-chart [options]="options()" [height]="300" label="Forecast" /></section>
        @if (x.lastYear) { <section class="x-card fc-note"><b>Last year:</b> {{ x.lastYear }}</section> }
        <section class="x-card fc-note"><h2>How to read this</h2><ul>@for (n of x.notes; track $index) { <li>{{ n }}</li> }</ul></section>
      }
    }`,
  styles: [`
    :host { display: block; }
    .fc-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.1rem; }
    h1 { margin: 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text); }
    h2 { margin: 0 0 .5rem; font-size: .95rem; font-weight: 700; color: var(--x-text); }
    .fc-sub { font-size: .78rem; color: var(--x-muted); margin-top: .2rem; }
    .fc-pick { font-size: .8rem; color: var(--x-muted); display: flex; align-items: center; gap: .5rem; }
    select { color: #0f172a; min-width: 84px; height: 36px; border-radius: 999px; border: 1px solid var(--x-line-2); padding: 0 .8rem; background: #fff; }
    .fc-kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 14px; margin-bottom: 14px; }
    .fc-k { padding: 1rem 1.1rem; }
    .fc-main { box-shadow: 0 0 0 2px var(--x-accent-line); }
    .fc-l { font-size: .76rem; font-weight: 600; color: var(--x-muted); }
    .fc-n { font-size: 1.9rem; font-weight: 700; letter-spacing: -.03em; color: var(--x-text); margin: .2rem 0; }
    .fc-n small { font-size: .8rem; font-weight: 500; color: var(--x-muted); letter-spacing: 0; }
    .fc-d { font-size: .76rem; color: var(--x-muted); }
    .fc-low { color: #b45309; }
    .fc-chart, .fc-note { padding: 1.1rem 1.3rem; margin-bottom: 14px; }
    ul { margin: 0; padding-left: 1.1rem; font-size: .85rem; color: var(--x-text); display: grid; gap: .3rem; }
  `],
})
export class CxoForecast implements OnInit {
  private cxo = inject(CxoService);
  f = signal<Forecast | null>(null);
  batches = signal<number[]>([]);
  loading = signal(true);
  error = signal('');
  short = shortDate;

  ngOnInit(): void {
    this.cxo.catalog().subscribe({ next: c => this.batches.set(c.batches), error: () => {} });
    this.load(null);
  }

  load(batch: number | null): void {
    this.loading.set(true);
    this.error.set('');
    this.cxo.forecast(batch).subscribe({
      next: f => { this.f.set(f); this.loading.set(false); },
      error: () => { this.error.set('Could not load the forecast. Try again.'); this.loading.set(false); },
    });
  }

  options = computed<Options>(() => {
    const f = this.f();
    if (!f) return {};
    const cats = f.series.map(p => p.month);
    const lastPast = f.series.reduce((n, p, i) => (p.placed !== null ? i : n), -1);
    const join = (v: number | null, i: number) => (i === lastPast ? f.series[i].placed : v); // the projection starts where the past ends
    return {
      chart: { type: 'line' },
      xAxis: { categories: cats, tickLength: 0 },
      yAxis: { min: 0, max: Math.max(f.eligible, 1), title: { text: undefined }, plotLines: [{ value: f.eligible, color: '#94a3b8', dashStyle: 'Dash', width: 1,
        label: { text: `Eligible: ${f.eligible}`, style: { color: '#64748b', fontSize: '10px' } } }] },
      tooltip: { shared: true, valueSuffix: ' students' },
      series: [
        { type: 'line', name: 'Placed', data: f.series.map(p => p.placed), color: '#a67c1e', lineWidth: 3, marker: { enabled: false } },
        { type: 'line', name: 'Likely', data: f.series.map((p, i) => join(p.base, i)), color: '#a67c1e', dashStyle: 'ShortDash', lineWidth: 2, marker: { enabled: false } },
        { type: 'line', name: 'Slower pace', data: f.series.map((p, i) => join(p.low, i)), color: '#94a3b8', dashStyle: 'Dot', lineWidth: 1.5, marker: { enabled: false } },
        { type: 'line', name: 'Faster pace', data: f.series.map((p, i) => join(p.high, i)), color: '#94a3b8', dashStyle: 'Dot', lineWidth: 1.5, marker: { enabled: false } },
      ],
      legend: { enabled: true },
    } as Options;
  });
}
