import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CxoService } from '../cxo.service';
import { Change, ChangeReport } from '../cxo.models';
import { fmt } from '../widgets/cxo-widget';
import { shortDate } from '../cxo-coverage';

/** "What changed since last month": the last N days against the N days before, in plain numbers. */
@Component({
  selector: 'pm-cxo-changes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="ch-head">
      <div><h1>What changed</h1>
        @if (r(); as x) { <div class="ch-sub">{{ short(x.from) }} – {{ short(x.to) }} compared with {{ short(x.previousFrom) }} – {{ short(x.previousTo) }}</div> }
      </div>
      <div class="ch-pick" role="group" aria-label="Period">
        @for (d of options; track d) { <button type="button" class="x-btn" [class.ch-on]="days() === d" (click)="load(d)">{{ d }} days</button> }
      </div>
    </header>
    @if (loading()) { <div class="x-skel" style="height: 280px; border-radius: 20px"></div> }
    @else if (error()) { <div class="x-card x-empty">{{ error() }}</div> }
    @else if (r(); as x) {
      <section class="x-card ch-high" aria-label="Highlights">
        <h2>Highlights</h2>
        <ul>@for (h of x.highlights; track $index) { <li>{{ h }}</li> }</ul>
      </section>
      <div class="ch-grid">
        @for (c of x.changes; track c.key) {
          <article class="x-card ch-card">
            <div class="ch-label">{{ c.label }}</div>
            <div class="ch-num">{{ show(c.current, c) }}</div>
            <div class="ch-delta" [class.ch-good]="tone(c) === 'good'" [class.ch-bad]="tone(c) === 'bad'">{{ delta(c) }}</div>
          </article>
        }
      </div>
      @if (x.topCompanies.length) {
        <section class="x-card ch-high">
          <h2>Companies with offers in this period</h2>
          <ul>@for (t of x.topCompanies; track t.name) { <li><b>{{ t.name }}</b> · {{ t.count }} offers</li> }</ul>
        </section>
      }
    }`,
  styles: [`
    :host { display: block; }
    .ch-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.1rem; }
    h1 { margin: 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text); }
    .ch-sub { font-size: .78rem; color: var(--x-muted); margin-top: .2rem; }
    .ch-pick { display: flex; gap: .4rem; }
    .ch-on { color: var(--x-accent); border-color: var(--x-accent-line); background: var(--x-accent-soft); }
    .ch-high { padding: 1.1rem 1.3rem; margin-bottom: 14px; }
    h2 { margin: 0 0 .5rem; font-size: .95rem; font-weight: 700; color: var(--x-text); }
    ul { margin: 0; padding-left: 1.1rem; display: grid; gap: .3rem; font-size: .88rem; color: var(--x-text); }
    .ch-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 14px; margin-bottom: 14px; }
    .ch-card { padding: 1rem 1.1rem; }
    .ch-label { font-size: .76rem; color: var(--x-muted); font-weight: 600; }
    .ch-num { font-size: 1.8rem; font-weight: 700; letter-spacing: -.03em; color: var(--x-text); margin: .2rem 0; }
    .ch-delta { font-size: .78rem; color: var(--x-muted); }
    .ch-good { color: #15803d; } .ch-bad { color: var(--x-down, #dc2626); }
  `],
})
export class CxoChanges implements OnInit {
  private cxo = inject(CxoService);
  options = [7, 30, 90];
  days = signal(30);
  r = signal<ChangeReport | null>(null);
  loading = signal(true);
  error = signal('');
  short = shortDate;

  ngOnInit(): void { this.load(30); }

  load(days: number): void {
    this.days.set(days);
    this.loading.set(true);
    this.error.set('');
    this.cxo.changes(days).subscribe({
      next: r => { this.r.set(r); this.loading.set(false); },
      error: () => { this.error.set('Could not load the changes. Try again.'); this.loading.set(false); },
    });
  }

  show(v: number | null, c: Change): string { return fmt(v, c.unit); }

  delta(c: Change): string {
    if (c.current === null || c.previous === null) return c.previous === null ? 'No numbers in the period before' : '';
    const diff = c.current - c.previous;
    if (diff === 0) return `No change from ${fmt(c.previous, c.unit)}`;
    return `${diff > 0 ? '▲ up' : '▼ down'} ${fmt(Math.abs(Math.round(diff * 10) / 10), c.unit)} from ${fmt(c.previous, c.unit)}`;
  }

  tone(c: Change): 'good' | 'bad' | '' {
    if (c.current === null || c.previous === null || c.current === c.previous) return '';
    return (c.current > c.previous) === c.higherIsBetter ? 'good' : 'bad';
  }
}
