import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CxoService } from '../cxo.service';
import { CxoBoard } from '../widgets/cxo-board';
import { CxoExport } from '../widgets/cxo-export';
import { CxoShare } from './cxo-share';
import { DashboardDetail, Insight, WidgetData } from '../cxo.models';
import { coverage } from '../cxo-coverage';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { ToastService } from '../../../core/services/toast.service';

const LIVE_EVERY_MS = 30_000;

/** A saved dashboard with live numbers (refreshes every 30 s); pin, open in Overview or delete. */
@Component({
  selector: 'pm-cxo-dashboard-view',
  imports: [RouterLink, CxoBoard, CxoExport, CxoShare],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!d()) {
      <div class="x-skel" style="height: 60px; margin-bottom: 14px"></div><div class="x-skel" style="height: 420px; border-radius: 20px"></div>
    } @else {
      @let dash = d()!;
      <header class="xv-head">
        <div class="min-w-0">
          <a routerLink="/cxo/dashboards" class="xv-back">Saved dashboards</a>
          <h1>{{ dash.title }}</h1>
          <div class="xv-sub">
            @if (live()) { <span class="xv-dot"></span>Live · {{ updatedAgo() }} } @else { Paused · {{ updatedAgo() }} }
            @if (dash.prompt) { <span class="xv-prompt">· {{ dash.prompt }}</span> }
          </div>
          @if (coverageLine()) { <div class="xv-cover" [attr.title]="coverageLine()"><b>Data:</b> {{ coverageLine() }}</div> }
        </div>
        <div class="xv-actions">
          <button type="button" class="x-btn" [class.xv-on]="live()" (click)="toggleLive()" [attr.aria-pressed]="live()">{{ live() ? 'Live' : 'Go live' }}</button>
          <pm-cxo-export [spec]="dash.spec" [prompt]="dash.prompt ?? null" [name]="dash.title" />
          <button type="button" class="x-btn" (click)="sharing.set(true)">Share</button>
          <button type="button" class="x-btn" (click)="pin()">{{ dash.pinned ? 'Unpin' : 'Pin' }}</button>
          <a class="x-btn" routerLink="/cxo/studio" [queryParams]="{ d: dash.id }">Open in Overview</a>
          <button type="button" class="x-btn x-btn-danger" (click)="remove()">Delete</button>
        </div>
      </header>
      <pm-cxo-board [spec]="dash.spec" [data]="data()" [insight]="insight()" [insightPending]="summarizing()" />
      @if (sharing()) { <pm-cxo-share [dashboardId]="dash.id" (closed)="sharing.set(false)" /> }
    }`,
  styles: [`
    :host { display: block; }
    .xv-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.1rem; }
    .xv-back { font-size: .76rem; font-weight: 600; color: var(--x-muted) !important; text-decoration: none; }
    .xv-back:hover { color: var(--x-text) !important; }
    .xv-head h1 { margin: .2rem 0 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text); }
    .xv-sub { display: flex; align-items: center; gap: .45rem; margin-top: .2rem; font-size: .78rem; color: var(--x-muted); min-width: 0; }
    .xv-cover { margin-top: .25rem; font-size: .76rem; color: var(--x-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 760px; }
    .xv-cover b { font-weight: 600; color: var(--x-text); }
    .xv-prompt { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 520px; }
    .xv-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--x-accent); animation: xvPulse 1.8s ease-out infinite; }
    .xv-actions { display: flex; gap: .45rem; flex-wrap: wrap; }
    .xv-on { color: var(--x-accent); border-color: var(--x-accent-line); background: var(--x-accent-soft); }
    @keyframes xvPulse { 0% { box-shadow: 0 0 0 0 rgba(43,89,195,.45); } 100% { box-shadow: 0 0 0 7px rgba(43,89,195,0); } }
  `],
})
export class CxoDashboardView implements OnInit {
  private cxo = inject(CxoService);
  private router = inject(Router);
  private confirm = inject(ConfirmService);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);
  /** Route parameter (component input binding). */
  id = input.required<string>();

  d = signal<DashboardDetail | null>(null);
  data = signal<Record<string, WidgetData>>({});
  insight = signal<Insight | null>(null);
  summarizing = signal(true);
  live = signal(true);
  sharing = signal(false);
  refreshing = signal(false);
  lastUpdated = signal<number | null>(null);
  private now = signal(Date.now());
  private onRecord = signal<number[]>([]);
  /** Which data the board shows: batch, branch, period, what the tiles are compared with. */
  coverageLine = computed(() => coverage(this.d()?.spec ?? null, this.data(), this.onRecord()));
  private timer?: ReturnType<typeof setInterval>;

  updatedAgo = computed(() => {
    const t = this.lastUpdated();
    if (!t) return 'loading';
    const s = Math.max(0, Math.round((this.now() - t) / 1000));
    return s < 5 ? 'updated just now' : s < 60 ? `updated ${s}s ago` : `updated ${Math.floor(s / 60)}m ago`;
  });

  ngOnInit(): void {
    this.cxo.catalog().subscribe({ next: c => this.onRecord.set(c.batches), error: () => {} });
    this.cxo.dashboard(this.id()).subscribe(d => {
      this.d.set(d); this.refresh(); this.startLive();
      this.cxo.summary(d.prompt ?? null, d.spec).subscribe({ next: i => { this.insight.set(i); this.summarizing.set(false); },
        error: () => this.summarizing.set(false) });
    });
    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => { clearInterval(clock); clearInterval(this.timer); });
  }

  toggleLive(): void {
    if (this.live()) { clearInterval(this.timer); this.live.set(false); }
    else { this.live.set(true); this.refresh(); this.startLive(); }
  }

  private startLive(): void {
    clearInterval(this.timer);
    if (this.live()) this.timer = setInterval(() => this.refresh(), LIVE_EVERY_MS);
  }

  refresh(): void {
    const d = this.d();
    if (!d || this.refreshing()) return;
    this.refreshing.set(true);
    this.cxo.run(d.spec).subscribe({
      next: rows => {
        const map: Record<string, WidgetData> = {};
        rows.forEach(r => (map[r.widgetId] = r));
        this.data.set(map);
        this.lastUpdated.set(Date.now());
        this.refreshing.set(false);
      },
      error: () => this.refreshing.set(false),
    });
  }

  pin(): void {
    const d = this.d();
    if (!d) return;
    this.cxo.pin(d.id, !d.pinned).subscribe(() => this.d.set({ ...d, pinned: !d.pinned }));
  }

  async remove(): Promise<void> {
    const d = this.d();
    if (!d) return;
    const r = await this.confirm.ask({ title: `Delete "${d.title}"?`, message: 'The saved dashboard is removed. Your data is not affected.',
      confirmText: 'Delete', tone: 'danger' });
    if (!r.ok) return;
    this.cxo.remove(d.id).subscribe(() => { this.toast.success('Dashboard deleted'); this.router.navigateByUrl('/cxo/dashboards'); });
  }
}
