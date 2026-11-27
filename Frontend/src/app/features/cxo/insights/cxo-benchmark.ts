import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CxoService } from '../cxo.service';
import { CxoExport } from '../widgets/cxo-export';
import { Benchmark } from '../cxo.models';

/** The placement figures NIRF and NAAC ask for, for a batch and the two before it, with where each number comes from. */
@Component({
  selector: 'pm-cxo-benchmark',
  imports: [CxoExport],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bm-head">
      <div><h1>NIRF / NAAC figures</h1><div class="bm-sub">Placement outcomes from your ProveYu records, for the last three batches.</div></div>
      <div class="bm-act">
        @if (batches().length) {
          <label>Batch
            <select (change)="load(+$any($event.target).value)">
              @for (b of batches(); track b) { <option [value]="b" [selected]="b === b$()?.year">{{ b }}</option> }
            </select></label>
        }
        <pm-cxo-export [source]="exporter" [name]="'NIRF_NAAC_placement_' + (b$()?.year ?? '')" />
      </div>
    </header>
    @if (loading()) { <div class="x-skel" style="height: 360px; border-radius: 20px"></div> }
    @else if (error()) { <div class="x-card x-empty">{{ error() }}</div> }
    @else if (b$(); as b) {
      <section class="x-card bm-card">
        <table>
          <thead><tr><th>Field</th>@for (y of b.years; track y) { <th class="r">Batch {{ y }}</th> }<th>Note</th></tr></thead>
          <tbody>
            @for (r of b.rows; track r.label) {
              <tr><td>{{ r.label }}</td>@for (v of r.values; track $index) { <td class="r" [class.bm-na]="v === 'Not recorded'">{{ v }}</td> }<td class="bm-note">{{ r.note || '' }}</td></tr>
            }
          </tbody>
        </table>
      </section>
      @if (b.branches.length) {
        <section class="x-card bm-card">
          <h2>By branch, batch {{ b.year }}</h2>
          <table>
            <thead><tr>@for (h of b.branchHeader; track $index) { <th [class.r]="$index > 0">{{ h }}</th> }</tr></thead>
            <tbody>@for (row of b.branches; track row[0]) { <tr>@for (c of row; track $index) { <td [class.r]="$index > 0">{{ c }}</td> }</tr> }</tbody>
          </table>
        </section>
      }
      <section class="x-card bm-card"><h2>Please note</h2><ul>@for (n of b.notes; track $index) { <li>{{ n }}</li> }</ul></section>
    }`,
  styles: [`
    :host { display: block; }
    .bm-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.1rem; }
    h1 { margin: 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text); }
    h2 { margin: 0 0 .6rem; font-size: .95rem; font-weight: 700; color: var(--x-text); }
    .bm-sub { font-size: .78rem; color: var(--x-muted); margin-top: .2rem; }
    .bm-act { display: flex; align-items: center; gap: .7rem; font-size: .8rem; color: var(--x-muted); }
    select { color: #0f172a; min-width: 84px; height: 36px; border-radius: 999px; border: 1px solid var(--x-line-2); padding: 0 .8rem; background: #fff; margin-left: .4rem; }
    .bm-card { padding: 1rem 1.2rem; margin-bottom: 14px; overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .84rem; }
    th { text-align: left; font-size: .7rem; text-transform: uppercase; letter-spacing: .05em; color: var(--x-muted); padding: .5rem; border-bottom: 1px solid var(--x-line); }
    td { padding: .55rem .5rem; border-bottom: 1px solid var(--x-line); color: var(--x-text); }
    .r { text-align: right; font-variant-numeric: tabular-nums; }
    .bm-na { color: var(--x-muted); font-style: italic; }
    .bm-note { color: var(--x-muted); font-size: .74rem; }
    ul { margin: 0; padding-left: 1.1rem; font-size: .82rem; color: var(--x-text); display: grid; gap: .3rem; }
  `],
})
export class CxoBenchmark implements OnInit {
  private cxo = inject(CxoService);
  b$ = signal<Benchmark | null>(null);
  batches = signal<number[]>([]);
  loading = signal(true);
  error = signal('');
  exporter = (format: 'pdf' | 'pptx') => this.cxo.exportBenchmark(format, this.b$()?.year ?? null);

  ngOnInit(): void {
    this.cxo.catalog().subscribe({ next: c => this.batches.set(c.batches), error: () => {} });
    this.load(null);
  }

  load(year: number | null): void {
    this.loading.set(true);
    this.error.set('');
    this.cxo.benchmark(year).subscribe({
      next: b => { this.b$.set(b); this.loading.set(false); },
      error: () => { this.error.set('Could not load the figures. Try again.'); this.loading.set(false); },
    });
  }
}
