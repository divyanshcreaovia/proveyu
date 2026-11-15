import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../services/auth.service';
import { CollegeService } from '../../../services/college.service';
import { NotificationService } from '../../../services/notification.service';
import { ThemeService } from '../../../services/theme.service';
import { SettingsService } from '../../../services/settings.service';
import { MemberRole } from '../../../core/models/college.models';
import { GlobalSearch } from './global-search';

interface NavItem { label: string; icon: string; route: string; roles?: MemberRole[]; }

const NAV: { section: string; items: NavItem[] }[] = [
  { section: 'Overview', items: [{ label: 'Dashboard', icon: 'bi-speedometer2', route: '/college/dashboard' }] },
  { section: 'Placements', items: [
    { label: 'Students', icon: 'bi-people', route: '/college/students' },
    { label: 'Companies', icon: 'bi-building', route: '/college/companies' },
    { label: 'Drives', icon: 'bi-briefcase', route: '/college/drives' },
    { label: 'Interviews', icon: 'bi-calendar3', route: '/college/interviews' },
    { label: 'Offers', icon: 'bi-award', route: '/college/offers' },
  ] },
  { section: 'Engage', items: [
    { label: 'Communication', icon: 'bi-megaphone', route: '/college/communication' },
    { label: 'Reports', icon: 'bi-bar-chart-line', route: '/college/reports' },
  ] },
  { section: 'Administration', items: [
    { label: 'Team & roles', icon: 'bi-person-badge', route: '/college/team' },
    { label: 'Audit log', icon: 'bi-journal-text', route: '/college/audit', roles: ['COLLEGE_ADMIN'] },
    { label: 'Settings', icon: 'bi-gear', route: '/college/settings', roles: ['COLLEGE_ADMIN'] },
  ] },
];

@Component({
  selector: 'pm-college-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe, GlobalSearch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './college-theme.scss',
  template: `
    <div class="pm-shell" [attr.data-bs-theme]="theme.dark() ? 'dark' : 'light'" [class.pm-shell-collapsed]="collapsed()">
      <nav class="pm-shell-side" [class.pm-shell-side-open]="mobileOpen()" aria-label="College navigation">
        <div class="pm-shell-brand">
          @if (logo()) { <img [src]="logo()" alt="" class="pm-shell-logo" /> } @else { <span class="pm-shell-mark"><i class="bi bi-mortarboard-fill"></i></span> }
          @if (!collapsed()) { <span class="text-truncate fw-bold small">{{ college.me()?.collegeName ?? 'Placement cell' }}</span> }
        </div>
        @for (g of nav(); track g.section) {
          @if (!collapsed()) { <div class="pm-shell-section">{{ g.section }}</div> }
          @for (i of g.items; track i.route) {
            <a [routerLink]="i.route" routerLinkActive="pm-shell-active" class="pm-shell-link" (click)="mobileOpen.set(false)" [attr.title]="collapsed() ? i.label : null">
              <i class="bi {{ i.icon }}" aria-hidden="true"></i>@if (!collapsed()) { <span>{{ i.label }}</span> }
            </a>
          }
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
            <pm-global-search />
            <button type="button" class="pm-shell-icon" (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Switch to light mode' : 'Switch to dark mode'">
              <i class="bi" [class.bi-moon-stars]="!theme.dark()" [class.bi-sun]="theme.dark()"></i></button>
            <div class="position-relative">
              <button type="button" class="pm-shell-icon position-relative" (click)="toggleBell()" aria-label="Notifications" [attr.aria-expanded]="bell()">
                <i class="bi bi-bell"></i>
                @if (notifications.unread()) { <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">{{ notifications.unread() }}</span> }
              </button>
              @if (bell()) {
                <div class="pm-shell-pop pm-shell-bell shadow" role="menu">
                  <div class="px-3 py-2 fw-semibold small border-bottom">Notifications</div>
                  @for (n of notifications.items().slice(0, 12); track n.id) {
                    <button type="button" class="pm-shell-note" [class.fw-semibold]="!n.read" (click)="notifications.markRead(n.id).subscribe()">
                      <span class="d-block small">{{ n.message }}</span><small class="text-body-secondary">{{ n.createdAt | date: 'short' }}</small>
                    </button>
                  } @empty { <div class="p-3 small text-body-secondary">You're all caught up.</div> }
                </div>
              }
            </div>
            <div class="position-relative">
              <button type="button" class="pm-shell-user" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-label="Account menu">
                <span class="pm-shell-avatar">{{ initials() }}</span>
                <span class="d-none d-xl-block text-start lh-sm"><span class="d-block small fw-semibold">{{ college.me()?.fullName }}</span>
                  <small class="text-body-secondary">{{ roleLabel() }}</small></span>
              </button>
              @if (menu()) {
                <div class="pm-shell-pop shadow" role="menu">
                  <div class="px-3 py-2 small border-bottom"><div class="fw-semibold">{{ college.me()?.fullName }}</div><div class="text-body-secondary">{{ college.me()?.email }}</div></div>
                  @if (college.isAdmin()) { <a routerLink="/college/settings" class="dropdown-item small" (click)="menu.set(false)"><i class="bi bi-gear me-2"></i>Settings</a> }
                  <button type="button" class="dropdown-item small text-danger" (click)="auth.logout('/college/login')"><i class="bi bi-box-arrow-right me-2"></i>Sign out</button>
                </div>
              }
            </div>
          </div>
        </header>
        <main class="pm-shell-content" (click)="menu.set(false); bell.set(false)">
          @if (college.me()) { <router-outlet /> } @else { <div class="text-center py-5"><span class="spinner-border"></span></div> }
        </main>
      </div>
    </div>`,
  styles: [`
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
    .pm-shell-brand { display: flex; align-items: center; gap: .6rem; padding: .4rem .4rem 1rem; min-width: 0; }
    .pm-shell-mark { width: 2.2rem; height: 2.2rem; border-radius: .6rem; background: #2563eb; color: #fff; display: grid; place-items: center; flex-shrink: 0; }
    .pm-shell-logo { width: 2.2rem; height: 2.2rem; object-fit: contain; border-radius: .4rem; flex-shrink: 0; }
    .pm-shell-section { font-size: .65rem; text-transform: uppercase; letter-spacing: .08em; color: var(--bs-secondary-color); font-weight: 700; padding: .9rem .6rem .35rem; }
    .pm-shell-link { display: flex; align-items: center; gap: .7rem; padding: .5rem .65rem; border-radius: .5rem; color: var(--bs-body-color);
      text-decoration: none; font-size: .875rem; white-space: nowrap; }
    .pm-shell-link:hover { background: var(--bs-secondary-bg); }
    .pm-shell-link:focus-visible { outline: 2px solid #2563eb; }
    .pm-shell-active { background: rgba(37,99,235,.12); color: #2563eb; font-weight: 600; }
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
    .pm-shell-bell { width: min(340px, 90vw); max-height: 60vh; overflow-y: auto; }
    .pm-shell-note { all: unset; box-sizing: border-box; display: block; width: 100%; padding: .55rem .9rem; border-bottom: 1px solid var(--bs-border-color); cursor: pointer; }
    .pm-shell-note:hover { background: var(--bs-secondary-bg); }
  `],
})
export class CollegeShell implements OnInit {
  auth = inject(AuthService);
  college = inject(CollegeService);
  notifications = inject(NotificationService);
  theme = inject(ThemeService);
  private settings = inject(SettingsService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  collapsed = signal(false);
  mobileOpen = signal(false);
  menu = signal(false);
  bell = signal(false);
  title = signal('');
  logo = signal<string | null>(null);

  nav = computed(() => {
    const role = this.college.me()?.role;
    return NAV.map(g => ({ ...g, items: g.items.filter(i => !i.roles || (role && i.roles.includes(role))) })).filter(g => g.items.length);
  });
  initials = computed(() => (this.college.me()?.fullName ?? '?').split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase());
  roleLabel = computed(() => ({ COLLEGE_ADMIN: 'TPO / Admin', COLLEGE_COORDINATOR: 'Coordinator', COLLEGE_VIEWER: 'Viewer' }[this.college.me()?.role ?? 'COLLEGE_VIEWER']));

  constructor() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => this.updateTitle());
  }

  ngOnInit(): void {
    this.updateTitle();
    this.college.loadMe().subscribe(me => {
      if (me.approvalStatus !== 'APPROVED') { this.router.navigateByUrl('/college/pending-approval'); return; }
      this.college.loadReference();
      if (me.hasLogo) this.settings.logoUrl().subscribe(u => this.logo.set(u));
      if (me.brandColor) document.documentElement.style.setProperty('--pm-brand', me.brandColor);
    });
    this.notifications.load().subscribe();
  }

  toggleBell(): void {
    this.bell.set(!this.bell());
    if (this.bell()) this.notifications.load().subscribe();
  }

  private updateTitle(): void {
    let r = this.route.snapshot;
    while (r.firstChild) r = r.firstChild;
    this.title.set(r.title ?? r.routeConfig?.title?.toString() ?? '');
  }
}
