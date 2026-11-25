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
  styleUrl: './cxo-theme.scss',
  template: `
    <div class="x-app" data-bs-theme="dark" (click)="menu.set(false)">
      <header class="x-top">
        <a routerLink="/cxo/studio" class="x-brand" aria-label="ProveYu Leadership home">
          <img src="images/proveyu-mark.png" alt="" /><b>ProveYu</b><span>Leadership</span>
        </a>
        <nav class="x-nav" aria-label="Leadership">
          <a routerLink="/cxo/studio" routerLinkActive="x-on">Overview</a>
          <a routerLink="/cxo/dashboards" routerLinkActive="x-on">Saved</a>
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
  failed = signal('');
  initials = computed(() => (this.cxo.me()?.fullName ?? '?').split(/\s+/).filter(w => !/^(dr|prof|mr|mrs|ms)\.?$/i.test(w))
    .map(p => p[0]).slice(0, 2).join('').toUpperCase());

  ngOnInit(): void {
    this.cxo.loadMe().subscribe({ error: (e: AppError) => this.failed.set(e.message || 'Your leadership access could not be loaded.') });
  }
}
