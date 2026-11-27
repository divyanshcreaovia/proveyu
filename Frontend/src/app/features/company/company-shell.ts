import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { DriveRoomService, RoomDriveCard } from './services/drive-room.service';

/** Company (recruiter) portal layout: same look as the student and college shells. */
@Component({
  selector: 'pm-company-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../college/layout/college-theme.scss', './company-public.scss'],
  template: `
    <div class="pm-shell" [attr.data-bs-theme]="theme.dark() ? 'dark' : 'light'" [class.pm-shell-collapsed]="collapsed()">
      <nav class="pm-shell-side" [class.pm-shell-side-open]="mobileOpen()" aria-label="Recruiter navigation">
        <a routerLink="/company/drives" class="pm-shell-brand" title="ProveYu Recruiter">
          <img src="images/proveyu-mark.png" alt="ProveYu" class="pm-shell-brand-mark" />
          @if (!collapsed()) { <span class="pm-shell-wordmark">Prove<span>Yu</span></span><span class="pm-shell-product">Recruiter</span> }
        </a>
        @if (!collapsed()) { <div class="pm-shell-section">Overview</div> }
        <a routerLink="/company/drives" routerLinkActive="pm-shell-active" [routerLinkActiveOptions]="{ exact: true }" class="pm-shell-link" (click)="mobileOpen.set(false)">
          <i class="bi bi-grid" aria-hidden="true"></i>@if (!collapsed()) { <span>My drives</span> }</a>
        @if (!collapsed() && drives().length) { <div class="pm-shell-section">Drive rooms</div> }
        @for (d of drives(); track d.driveId) {
          <a [routerLink]="['/company/drives', d.driveId]" routerLinkActive="pm-shell-active" class="pm-shell-link" (click)="mobileOpen.set(false)" [attr.title]="d.title + ' · ' + d.collegeName">
            <i class="bi bi-briefcase" aria-hidden="true"></i>@if (!collapsed()) { <span class="text-truncate">{{ d.title }}<small class="d-block cs-sub text-truncate">{{ d.collegeName }}</small></span> }</a>
        }
        <button type="button" class="pm-shell-collapse d-none d-lg-flex" (click)="collapsed.set(!collapsed())" [attr.aria-label]="collapsed() ? 'Expand menu' : 'Collapse menu'">
          <i class="bi" [class.bi-chevron-double-left]="!collapsed()" [class.bi-chevron-double-right]="collapsed()"></i>
        </button>
      </nav>
      @if (mobileOpen()) { <div class="pm-shell-scrim d-lg-none" (click)="mobileOpen.set(false)"></div> }

      <div class="pm-shell-main">
        <header class="pm-shell-top">
          <button type="button" class="pm-shell-icon d-lg-none" (click)="mobileOpen.set(true)" aria-label="Open menu"><i class="bi bi-list"></i></button>
          <h1 class="pm-shell-title text-truncate">{{ title() }}</h1>
          <div class="ms-auto d-flex align-items-center gap-2">
            <button type="button" class="pm-shell-icon" (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Switch to light mode' : 'Switch to dark mode'">
              <i class="bi" [class.bi-moon-stars]="!theme.dark()" [class.bi-sun]="theme.dark()"></i></button>
            <div class="position-relative">
              <button type="button" class="pm-shell-user" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-label="Account menu">
                <span class="pm-shell-avatar">{{ initials() }}</span>
                <span class="d-none d-xl-block text-start lh-sm"><span class="d-block small fw-semibold">{{ name }}</span><small class="text-body-secondary">Recruiter</small></span>
              </button>
              @if (menu()) {
                <div class="pm-shell-pop pm-shell-menu shadow" role="menu">
                  <div class="pm-menu-head"><span class="pm-shell-avatar">{{ initials() }}</span>
                    <div class="min-w-0"><div class="pm-menu-name">{{ name }}</div><div class="pm-menu-sub">{{ email }}</div></div></div>
                  <a routerLink="/company/drives" class="pm-menu-item" role="menuitem" (click)="menu.set(false)"><i class="bi bi-grid"></i>My drives</a>
                  <div class="pm-menu-sep"></div>
                  <button type="button" class="pm-menu-item pm-menu-danger" role="menuitem" (click)="auth.logout('/company/login')"><i class="bi bi-box-arrow-right"></i>Sign out</button>
                </div>
              }
            </div>
          </div>
        </header>
        <main class="pm-shell-content" (click)="menu.set(false)"><router-outlet /></main>
      </div>
    </div>`,
  styles: [`
    .cs-sub { font-size: .7rem; color: var(--bs-secondary-color); font-weight: 400; }
    :host { display: block; }
    .pm-shell { display: flex; height: 100vh; overflow: hidden; background: var(--pm-page-bg); color: var(--pm-text); font-family: 'Inter', sans-serif; }
    .pm-shell-side { width: 248px; flex-shrink: 0; background: var(--bs-body-bg); border-right: 1px solid var(--bs-border-color); overflow-y: auto;
      display: flex; flex-direction: column; padding: .75rem; transition: width .15s; }
    .pm-shell-collapsed .pm-shell-side { width: 72px; }
    @media (max-width: 991.98px) {
      .pm-shell-side { position: fixed; inset: 0 auto 0 0; z-index: 1045; transform: translateX(-105%); transition: transform .2s; width: 260px !important; }
      .pm-shell-side-open { transform: none; }
    }
    .pm-shell-scrim { position: fixed; inset: 0; background: rgba(15,23,42,.45); z-index: 1040; }
    .pm-shell-brand { display: flex; align-items: center; gap: .55rem; padding: .35rem .4rem .75rem; min-width: 0; text-decoration: none; }
    .pm-shell-brand-mark { width: 2.1rem; height: 2.1rem; object-fit: contain; flex-shrink: 0; }
    .pm-shell-wordmark { font-weight: 800; font-size: 1.12rem; letter-spacing: -.02em; color: var(--pm-heading); line-height: 1; }
    .pm-shell-wordmark span { background: linear-gradient(90deg, #0b5cff, #00c2ff); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .pm-shell-product { font-size: .6rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; padding: .2rem .45rem; border-radius: 6px;
      background: linear-gradient(90deg, #0b5cff, #00b4ff); color: #fff; line-height: 1; }
    .pm-shell-collapsed .pm-shell-brand { justify-content: center; }
    .pm-shell-section { font-size: .65rem; text-transform: uppercase; letter-spacing: .08em; color: var(--bs-secondary-color); font-weight: 700; padding: .9rem .6rem .35rem; }
    .pm-shell-link { display: flex; align-items: center; gap: .7rem; padding: .5rem .65rem; border-radius: .5rem; color: var(--bs-body-color);
      text-decoration: none; font-size: .875rem; white-space: nowrap; }
    .pm-shell-link:hover { background: var(--bs-secondary-bg); }
    .pm-shell-link:focus-visible { outline: 2px solid #2563eb; }
    .pm-shell-active { background: rgba(37,99,235,.12); color: #2563eb; font-weight: 600; }
    .pm-shell[data-bs-theme='dark'] .pm-shell-active { background: rgba(59,130,246,.32); color: #fff; }
    .pm-shell-link { position: relative; }
    .pm-nav-badge { min-width: 1.3rem; height: 1.3rem; padding: 0 .38rem; border-radius: 999px; background: #dc2626; color: #fff; font-size: .7rem;
      font-weight: 700; display: inline-grid; place-items: center; line-height: 1; }
    .pm-nav-badge-chat { background: #22c55e; }
    .pm-nav-badge-dot { position: absolute; top: .3rem; right: .55rem; min-width: .6rem; width: .6rem; height: .6rem; padding: 0; }
    .pm-shell-collapse { margin-top: auto; align-self: flex-end; border: 0; background: transparent; color: var(--bs-secondary-color); padding: .4rem; }
    .pm-shell-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .pm-shell-top { display: flex; align-items: center; gap: .75rem; padding: 1.1rem 1rem .9rem; background: transparent; }
    @media (min-width: 768px) { .pm-shell-top { padding: 1.25rem 1.5rem 1rem; } }
    .pm-shell-title { font-size: 1.75rem; font-weight: 700; margin: 0; min-width: 0; color: var(--pm-heading); letter-spacing: -0.02em; }
    @media (max-width: 575.98px) { .pm-shell-title { font-size: 1.3rem; } }
    .pm-shell-icon { width: 42px; height: 42px; border-radius: 50%; border: 0; background: var(--pm-card-bg); color: var(--pm-heading);
      display: inline-grid; place-items: center; box-shadow: 0 2px 8px rgba(15, 23, 42, .06); transition: box-shadow .2s, transform .2s; flex-shrink: 0; }
    .pm-shell-icon:hover { box-shadow: 0 6px 16px rgba(15, 23, 42, .1); transform: translateY(-1px); }
    .pm-shell-icon:focus-visible { outline: 2px solid #4f46e5; outline-offset: 2px; }
    .pm-shell-content { flex: 1; overflow-y: auto; padding: 0 1rem 1.5rem; }
    @media (min-width: 768px) { .pm-shell-content { padding: 0 1.5rem 2rem; } }
    .pm-shell-user { all: unset; display: flex; align-items: center; gap: .55rem; cursor: pointer; padding: .3rem .9rem .3rem .3rem; border-radius: 50px;
      background: var(--pm-card-bg); box-shadow: 0 2px 8px rgba(15, 23, 42, .06); }
    .pm-shell-user:focus-visible { outline: 2px solid #2563eb; }
    .pm-shell-avatar { width: 2.2rem; height: 2.2rem; border-radius: 50%; background: #16122b; color: #fff; display: grid; place-items: center; font-size: .75rem; font-weight: 700; }
    .pm-shell-pop { position: absolute; right: 0; top: calc(100% + .5rem); min-width: 240px; background: var(--pm-card-bg); border: 1px solid var(--pm-card-border);
      border-radius: 14px; z-index: 1030; overflow: hidden; box-shadow: 0 16px 40px rgba(15, 23, 42, .12) !important; }
    .pm-shell-menu { width: 270px; padding: .4rem; }
    .pm-menu-head { display: flex; align-items: center; gap: .7rem; padding: .7rem .7rem .8rem; margin-bottom: .3rem; border-bottom: 1px solid var(--pm-divider, #ebedf2); }
    .pm-menu-head .pm-shell-avatar { width: 2.6rem; height: 2.6rem; flex-shrink: 0; }
    .pm-menu-name { font-weight: 700; font-size: .9rem; color: var(--pm-heading); line-height: 1.25; }
    .pm-menu-sub { font-size: .78rem; color: var(--pm-muted, #64748b); overflow-wrap: anywhere; line-height: 1.3; }
    .pm-menu-item { all: unset; box-sizing: border-box; display: flex; align-items: center; gap: .7rem; width: 100%; padding: .6rem .75rem;
      border-radius: 10px; font-size: .875rem; font-weight: 500; color: var(--pm-text); cursor: pointer; text-decoration: none; }
    .pm-menu-item i { width: 1.1rem; text-align: center; font-size: 1rem; color: var(--pm-muted, #64748b); }
    .pm-menu-item:hover, .pm-menu-item:focus-visible { background: var(--pm-soft, #f1f5f9); color: var(--pm-heading); }
    .pm-menu-item:focus-visible { outline: 2px solid #4f46e5; outline-offset: -2px; }
    .pm-menu-sep { height: 1px; background: var(--pm-divider, #ebedf2); margin: .3rem .25rem; }
    .pm-menu-danger, .pm-menu-danger i { color: #dc2626; }
    .pm-menu-danger:hover, .pm-menu-danger:focus-visible { background: rgba(220, 38, 38, .08); color: #b91c1c; }
    .pm-shell-bell { width: min(360px, 92vw); }
    .pm-bell-badge { position: absolute; top: -3px; right: -3px; min-width: 1.2rem; height: 1.2rem; padding: 0 .3rem; border-radius: 999px;
      background: #ef4444; color: #fff; font-size: .66rem; font-weight: 800; line-height: 1.2rem; text-align: center;
      box-shadow: 0 0 0 2px var(--pm-card-bg); font-variant-numeric: tabular-nums; }
  `],
})
export class CompanyShell implements OnInit {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  private room = inject(DriveRoomService);
  private storage = inject(TokenStorageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  collapsed = signal(false);
  mobileOpen = signal(false);
  menu = signal(false);
  title = signal('');
  drives = signal<RoomDriveCard[]>([]);
  name = this.storage.get('candidateFullName') ?? 'Recruiter';
  email = this.storage.get('email') ?? '';
  initials = computed(() => this.name.split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase());

  constructor() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => this.updateTitle());
  }

  ngOnInit(): void {
    this.updateTitle();
    this.room.drives().subscribe({ next: d => this.drives.set(d), error: () => this.drives.set([]) });
  }

  private updateTitle(): void {
    let r = this.route.snapshot;
    while (r.firstChild) r = r.firstChild;
    this.title.set(r.title ?? r.routeConfig?.title?.toString() ?? '');
  }
}
