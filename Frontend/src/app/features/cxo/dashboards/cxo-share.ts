import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { CxoService } from '../cxo.service';
import { ShareLink } from '../cxo.models';
import { ToastService } from '../../../core/services/toast.service';

/** Read-only links to a saved dashboard for people without an account (a trustee, a director). */
@Component({
  selector: 'pm-cxo-share',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="xs-back" (click)="closed.emit()">
      <section class="xs" role="dialog" aria-modal="true" aria-label="Share this dashboard" (click)="$event.stopPropagation()">
        <header><h2>Share this dashboard</h2><button type="button" class="x-btn x-btn-icon" (click)="closed.emit()" aria-label="Close">✕</button></header>
        <p class="xs-note">Anyone with the link can see this dashboard's numbers, read only and without signing in. Lists of student names are never shown.
          Numbers are live. You can switch a link off at any time.</p>
        <div class="xs-row">
          <label for="xs-days">Link works for</label>
          <select id="xs-days" [value]="days()" (change)="days.set(+$any($event.target).value)">
            <option value="7">7 days</option><option value="30">30 days</option><option value="90">90 days</option><option value="180">180 days</option>
          </select>
          <button type="button" class="x-btn x-btn-accent" (click)="create()" [disabled]="busy()">Create link</button>
        </div>
        @if (fresh(); as f) {
          <div class="xs-new">
            <div class="xs-url">{{ url(f) }}</div>
            <button type="button" class="x-btn" (click)="copy(f)">Copy</button>
          </div>
          <div class="xs-hint">Copy it now: for safety the link is not shown again.</div>
        }
        @if (links().length) {
          <h3>Links</h3>
          <ul>
            @for (l of links(); track l.id) {
              <li>
                <span>Created {{ l.createdAt | date: 'd MMM' }} · {{ l.revoked ? 'switched off' : isExpired(l) ? 'expired' : 'until ' + (l.expiresAt | date: 'd MMM') }} · {{ l.views }} views</span>
                @if (!l.revoked && !isExpired(l)) { <button type="button" class="x-btn" (click)="revoke(l)">Switch off</button> }
              </li>
            }
          </ul>
        }
      </section>
    </div>`,
  styles: [`
    .xs-back { position: fixed; inset: 0; z-index: 1050; display: grid; place-items: center; padding: 1rem; background: rgba(15, 23, 42, .45); }
    .xs { width: min(560px, 100%); max-height: 86vh; overflow: auto; padding: 1.2rem 1.3rem; background: var(--x-panel, #fff); border-radius: 20px; box-shadow: 0 24px 60px rgba(15, 23, 42, .3); }
    header { display: flex; justify-content: space-between; align-items: center; }
    h2 { margin: 0; font-size: 1.15rem; font-weight: 700; color: var(--x-text); }
    h3 { margin: 1rem 0 .4rem; font-size: .8rem; text-transform: uppercase; letter-spacing: .05em; color: var(--x-muted); }
    .xs-note, .xs-hint { font-size: .8rem; color: var(--x-muted); line-height: 1.5; }
    .xs-row { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; margin: .8rem 0; font-size: .82rem; }
    select { color: #0f172a; min-width: 84px; height: 36px; border-radius: 999px; border: 1px solid var(--x-line-2); padding: 0 .8rem; background: #fff; font-size: .82rem; }
    .xs-new { display: flex; gap: .5rem; align-items: center; margin-top: .4rem; }
    .xs-url { flex: 1; min-width: 0; padding: .55rem .75rem; border-radius: 10px; background: var(--x-panel-2); font-size: .76rem; word-break: break-all; color: var(--x-text); }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: .4rem; }
    li { display: flex; justify-content: space-between; align-items: center; gap: .6rem; font-size: .78rem; color: var(--x-text); padding: .45rem 0; border-bottom: 1px solid var(--x-line); }
  `],
})
export class CxoShare implements OnInit {
  private cxo = inject(CxoService);
  private toast = inject(ToastService);
  dashboardId = input.required<string>();
  closed = output<void>();

  days = signal(30);
  busy = signal(false);
  fresh = signal<ShareLink | null>(null);
  links = signal<ShareLink[]>([]);

  ngOnInit(): void { this.load(); }

  private load(): void { this.cxo.shares(this.dashboardId()).subscribe(l => this.links.set(l)); }

  url(l: ShareLink): string { return `${location.origin}${l.path}`; }
  isExpired(l: ShareLink): boolean { return new Date(l.expiresAt).getTime() < Date.now(); }

  create(): void {
    this.busy.set(true);
    this.cxo.createShare(this.dashboardId(), this.days()).subscribe({
      next: l => { this.fresh.set(l); this.busy.set(false); this.load(); },
      error: () => this.busy.set(false),
    });
  }

  async copy(l: ShareLink): Promise<void> {
    try { await navigator.clipboard.writeText(this.url(l)); this.toast.success('Link copied'); }
    catch { this.toast.error('Could not copy. Select the link and copy it.'); }
  }

  revoke(l: ShareLink): void {
    this.cxo.revokeShare(l.id).subscribe(() => { this.toast.success('Link switched off'); this.load(); });
  }
}
