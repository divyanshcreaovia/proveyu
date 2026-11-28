import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CxoService } from '../cxo.service';
import { DashboardSummary } from '../cxo.models';

/** Saved dashboards of the signed-in leader: pinned first, then the most recently changed. */
@Component({
  selector: 'pm-cxo-dashboards',
  imports: [DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="xd-head">
      <div>
        <h1>Saved dashboards</h1>
        <p>Numbers are recalculated from live data every time you open one.</p>
      </div>
      <a routerLink="/cxo/studio" class="x-btn x-btn-accent">New dashboard</a>
    </header>
    @if (loading()) {
      <div class="xd-grid"><div class="x-skel xd-sk"></div><div class="x-skel xd-sk"></div><div class="x-skel xd-sk"></div></div>
    } @else if (!rows().length) {
      <div class="x-card x-empty">No saved dashboards yet. Build one in Overview and press Save.</div>
    } @else {
      <div class="xd-grid">
        @for (d of rows(); track d.id; let i = $index) {
          <article class="xd-card" [class.xd-pinned]="d.pinned" [style.animation-delay.ms]="i * 40">
            <a class="xd-link" [routerLink]="['/cxo/dashboards', d.id]" [attr.aria-label]="'Open ' + d.title"></a>
            <div class="xd-top">
              <span class="xd-kicker">{{ d.pinned ? 'Pinned' : d.widgets + ' widgets' }}</span>
              <button type="button" class="xd-pin" (click)="pin(d)" [attr.aria-pressed]="d.pinned">{{ d.pinned ? 'Unpin' : 'Pin' }}</button>
            </div>
            <h2>{{ d.title }}</h2>
            @if (d.prompt) { <p class="xd-prompt">{{ d.prompt }}</p> }
            <div class="xd-foot">Updated {{ d.updatedAt | date: 'd MMM, h:mm a' }}</div>
          </article>
        }
      </div>
    }`,
  styles: [`
    :host { display: block; }
    .xd-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .xd-head h1 { margin: 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text); }
    .xd-head p { margin: .2rem 0 0; font-size: .82rem; color: var(--x-muted); }
    .xd-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
    .xd-sk { height: 170px; border-radius: 20px; }
    .xd-card { position: relative; display: flex; flex-direction: column; min-height: 170px; padding: 1.1rem 1.2rem; border-radius: 20px;
      background: var(--x-panel); border: 1px solid var(--x-line); transition: border-color .2s ease, transform .25s var(--x-ease);
      animation: xdIn .4s var(--x-ease) backwards; }
    .xd-card:hover { border-color: var(--x-accent-line); transform: translateY(-2px); }
    .xd-pinned { background: radial-gradient(120% 90% at 100% 0%, rgba(166,124,30,.06), transparent 60%), var(--x-panel); }
    .xd-link { position: absolute; inset: 0; border-radius: 20px; z-index: 1; }
    .xd-link:focus-visible { outline: 2px solid var(--x-accent); outline-offset: 2px; }
    .xd-top { display: flex; justify-content: space-between; align-items: center; }
    .xd-kicker { font-size: .68rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--x-muted); }
    .xd-pinned .xd-kicker { color: var(--x-accent); }
    .xd-pin { all: unset; position: relative; z-index: 2; cursor: pointer; padding: .2rem .6rem; border-radius: 999px; font-size: .72rem; font-weight: 600;
      color: var(--x-muted); border: 1px solid var(--x-line); }
    .xd-pin:hover { color: var(--x-text); border-color: var(--x-line-2); }
    .xd-card h2 { margin: .9rem 0 .3rem; font-size: 1.1rem; font-weight: 650; letter-spacing: -.02em; color: var(--x-text); }
    .xd-prompt { margin: 0; font-size: .82rem; line-height: 1.45; color: var(--x-muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .xd-foot { margin-top: auto; padding-top: .9rem; font-size: .72rem; color: var(--x-dim); }
    @keyframes xdIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
  `],
})
export class CxoDashboards implements OnInit {
  private cxo = inject(CxoService);
  rows = signal<DashboardSummary[]>([]);
  loading = signal(true);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.cxo.dashboards().subscribe({ next: r => { this.rows.set(r); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  pin(d: DashboardSummary): void {
    this.cxo.pin(d.id, !d.pinned).subscribe(() => this.load());
  }
}
