import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AppError } from '../../../core/api';
import { CxoService } from '../cxo.service';

/**
 * ProveYu Leadership (CXO) layout: its own always-dark design with a slim top bar instead of a sidebar, so the whole width goes
 * to the dashboard. Independent of the college / student portal themes.
 */
@Component({
  selector: 'pm-cxo-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./cxo-theme.scss', './cxo-public.scss'],
  host: { '(document:keydown.escape)': 'drawer.set(false)' },
  template: `
    <div class="x-app" data-bs-theme="dark" (click)="menu.set(false)">
      <header class="x-top">
        <button type="button" class="x-burger" (click)="drawer.set(true); menu.set(false)" [attr.aria-expanded]="drawer()" aria-label="Open menu"><i class="bi bi-list"></i></button>
        <a routerLink="/cxo/studio" class="x-brand" aria-label="ProveYu Leadership home">
          <img src="images/proveyu-mark.png" alt="" /><b>ProveYu</b><span>Leadership</span>
        </a>
        <nav class="x-nav" aria-label="Leadership">
          <a routerLink="/cxo/studio" routerLinkActive="x-on">Overview</a>
          <a routerLink="/cxo/dashboards" routerLinkActive="x-on">Saved</a>
          <a routerLink="/cxo/changes" routerLinkActive="x-on">What changed</a>
          <a routerLink="/cxo/forecast" routerLinkActive="x-on">Forecast</a>
          <a routerLink="/cxo/benchmark" routerLinkActive="x-on">NIRF / NAAC</a>
        </nav>
        <div class="x-top-right">
          @if (cxo.me(); as m) { <span class="x-college" [title]="m.collegeName">{{ m.collegeName }}</span> }
          <div class="position-relative" (click)="$event.stopPropagation()">
            <button type="button" class="x-user" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-label="Account menu">
              <span class="x-user-text text-end"><span class="x-user-name d-block">{{ cxo.me()?.fullName }}</span>
                <span class="x-user-role d-block">{{ cxo.me()?.designation || 'Leadership' }}</span></span>
              <span class="x-avatar">{{ initials() }}</span>
            </button>
            @if (menu()) {
              <div class="x-menu" role="menu">
                <div class="x-menu-head"><div>{{ cxo.me()?.fullName }}</div><small>{{ cxo.me()?.email }}</small><small>{{ cxo.me()?.collegeName }}</small></div>
                <a routerLink="/cxo/dashboards" role="menuitem" (click)="menu.set(false)">Saved dashboards</a>
                <button type="button" role="menuitem" class="x-danger" (click)="auth.logout('/cxo/login')">Sign out</button>
              </div>
            }
          </div>
        </div>
      </header>
      @if (drawer()) {
        <div class="x-scrim" (click)="drawer.set(false)"></div>
        <aside class="x-drawer" aria-label="Leadership menu">
          <div class="x-drawer-head">
            <span class="x-brand"><img src="images/proveyu-mark.png" alt="" /><b>ProveYu</b><span>Leadership</span></span>
            <button type="button" class="x-drawer-close" (click)="drawer.set(false)" aria-label="Close menu"><i class="bi bi-x-lg"></i></button>
          </div>
          <nav class="x-drawer-links" aria-label="Leadership pages">
            <a routerLink="/cxo/studio" routerLinkActive="x-on" (click)="drawer.set(false)"><i class="bi bi-speedometer2"></i>Overview</a>
            <a routerLink="/cxo/dashboards" routerLinkActive="x-on" (click)="drawer.set(false)"><i class="bi bi-bookmark"></i>Saved</a>
            <a routerLink="/cxo/changes" routerLinkActive="x-on" (click)="drawer.set(false)"><i class="bi bi-activity"></i>What changed</a>
            <a routerLink="/cxo/forecast" routerLinkActive="x-on" (click)="drawer.set(false)"><i class="bi bi-graph-up-arrow"></i>Forecast</a>
            <a routerLink="/cxo/benchmark" routerLinkActive="x-on" (click)="drawer.set(false)"><i class="bi bi-patch-check"></i>NIRF / NAAC</a>
          </nav>
          <div class="x-drawer-foot">
            <div class="x-drawer-me"><span class="x-avatar">{{ initials() }}</span>
              <div class="min-w-0"><b>{{ cxo.me()?.fullName }}</b><small>{{ cxo.me()?.designation || 'Leadership' }}</small><small>{{ cxo.me()?.collegeName }}</small></div></div>
            <button type="button" class="x-drawer-out" (click)="auth.logout('/cxo/login')">Sign out</button>
          </div>
        </aside>
      }
      <main class="x-main">
        @if (failed()) {
          <div class="x-card x-empty">{{ failed() }}<div class="mt-3"><button class="x-btn" (click)="auth.logout('/cxo/login')">Sign out</button></div></div>
        } @else if (cxo.me()) { <router-outlet /> }
        @else { <div class="x-skel" style="height: 120px"></div> }
      </main>
    </div>`,
})
export class CxoShell implements OnInit {
  readonly auth = inject(AuthService);
  readonly cxo = inject(CxoService);
  menu = signal(false);
  /** The phone menu; the button that opens it is shown only on small screens. */
  drawer = signal(false);
  failed = signal('');
  initials = computed(() => (this.cxo.me()?.fullName ?? '?').split(/\s+/).filter(w => !/^(dr|prof|mr|mrs|ms)\.?$/i.test(w))
    .map(p => p[0]).slice(0, 2).join('').toUpperCase());

  ngOnInit(): void {
    this.cxo.catalog().subscribe({ error: () => {} }); // metric definitions for the "how is this calculated" notes
    this.cxo.loadMe().subscribe({ error: (e: AppError) => this.failed.set(e.message || 'Your leadership access could not be loaded.') });
  }
}
