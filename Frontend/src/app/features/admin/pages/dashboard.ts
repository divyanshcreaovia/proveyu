import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminDashboard, AdminService, ROLE_LABELS } from '../services/admin.service';
import { StatCard } from '../../../shared/ui/stat-card';
import { Skeleton, EmptyState } from '../../../shared/ui/states';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { SupportService } from '../../support/support.service';

/** Platform overview: colleges, users, placements, preparation, payments, AI usage, and what needs the admin now. */
@Component({
  selector: 'pm-admin-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, StatCard, Skeleton, EmptyState, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!d()) { <pm-skeleton [rows]="6" /> } @else {
      @let x = d()!;
      @if (x.colleges['PENDING'] || x.paymentsWaiting || support.open()) {
        <div class="ad-todo mb-3">
          @if (x.colleges['PENDING']) {
            <a routerLink="/admin/colleges" class="ad-todo-item"><i class="bi bi-building-exclamation"></i>
              <span><strong>{{ x.colleges['PENDING'] }}</strong> college{{ x.colleges['PENDING'] === 1 ? '' : 's' }} waiting for approval</span><i class="bi bi-chevron-right ms-auto"></i></a>
          }
          @if (x.paymentsWaiting) {
            <a routerLink="/admin/payments" class="ad-todo-item"><i class="bi bi-currency-rupee"></i>
              <span><strong>{{ x.paymentsWaiting }}</strong> payment{{ x.paymentsWaiting === 1 ? '' : 's' }} to verify</span><i class="bi bi-chevron-right ms-auto"></i></a>
          }
          @if (support.open(); as n) {
            <a routerLink="/admin/support" class="ad-todo-item"><i class="bi bi-life-preserver"></i>
              <span><strong>{{ n }}</strong> support request{{ n === 1 ? '' : 's' }} to answer</span><i class="bi bi-chevron-right ms-auto"></i></a>
          }
        </div>
      }

      <div class="ad-kpis mb-3">
        <pm-stat-card label="Active colleges" [value]="x.colleges['APPROVED'] ?? 0" icon="bi-building-check" tone="blue"
          [hint]="(x.colleges['PENDING'] ?? 0) + ' pending · ' + (x.colleges['SUSPENDED'] ?? 0) + ' suspended'" />
        <pm-stat-card label="Students" [value]="x.students | number" icon="bi-mortarboard" tone="violet" [hint]="(x.users['CANDIDATE'] ?? 0) + ' student accounts'" />
        <pm-stat-card label="Placed" [value]="x.placedStudents | number" icon="bi-trophy" tone="green" [hint]="x.acceptedOffers + ' accepted offers'" />
        <pm-stat-card label="Live drives" [value]="x.liveDrives" icon="bi-briefcase" tone="amber" [hint]="x.drives + ' drives in total'" />
        <pm-stat-card label="Revenue" [value]="'₹' + (x.revenueRupees | number)" icon="bi-cash-stack" tone="green"
          [hint]="'₹' + (x.revenueThisMonthRupees | number) + ' this month · AI interviews'" />
        <pm-stat-card label="AI interviews" [value]="x.interviews | number" icon="bi-mic" tone="violet" [hint]="x.completedInterviews + ' completed'" />
        <pm-stat-card label="Mock tests" [value]="x.mockAttempts | number" icon="bi-journal-code" tone="blue"
          [hint]="x.flaggedAttempts + ' flagged · ' + x.autoSubmittedAttempts + ' auto-submitted'" />
        <pm-stat-card label="Blocked users" [value]="x.blockedUsers" icon="bi-person-x" tone="rose" />
      </div>

      <div class="row g-3">
        <div class="col-lg-7">
          <section class="bg-body border rounded-3 p-3 h-100">
            <div class="d-flex align-items-center mb-2"><h2 class="h6 fw-bold mb-0 flex-grow-1">Colleges waiting for approval</h2>
              <a routerLink="/admin/colleges" class="small">All colleges</a></div>
            @for (c of x.pendingColleges; track c.collegeId) {
              <a routerLink="/admin/colleges" [queryParams]="{ open: c.collegeId }" class="ad-row">
                <span class="flex-grow-1 min-w-0"><strong class="d-block text-truncate">{{ c.collegeName }}</strong>
                  <small class="text-body-secondary">{{ c.city }}{{ c.state ? ', ' + c.state : '' }} · {{ c.tpoName }} · {{ c.createdAt | date: 'd MMM' }}</small></span>
                <pm-status [value]="c.approvalStatus" />
              </a>
            } @empty { <pm-empty-state icon="bi-building-check" title="Nothing to approve" text="New college registrations appear here." /> }
          </section>
        </div>
        <div class="col-lg-5">
          <section class="bg-body border rounded-3 p-3 h-100">
            <h2 class="h6 fw-bold mb-1">AI usage</h2>
            <p class="small text-body-secondary mb-2">Since the server started {{ x.ai.since | date: 'd MMM, h:mm a' }}</p>
            <dl class="ad-dl">
              <dt>Cloud AI calls (Groq)</dt><dd>{{ x.ai.cloudCalls | number }}</dd>
              <dt>Tokens used</dt><dd>{{ x.ai.cloudTokens | number }}</dd>
              <dt>Cloud failures</dt><dd [class.text-danger]="x.ai.cloudFailures > 0">{{ x.ai.cloudFailures | number }}</dd>
              <dt>Answered by the local model</dt><dd>{{ x.ai.localCalls | number }}</dd>
              <dt>Speech-to-text (cloud / local)</dt><dd>{{ x.ai.cloudTranscriptions | number }} / {{ x.ai.localTranscriptions | number }}</dd>
            </dl>
            <h2 class="h6 fw-bold mt-3 mb-2">Accounts</h2>
            <dl class="ad-dl">
              @for (r of roles(x); track r.key) { <dt>{{ r.label }}</dt><dd>{{ r.n | number }}</dd> }
            </dl>
          </section>
        </div>
        <div class="col-12">
          <section class="bg-body border rounded-3 p-3">
            <div class="d-flex align-items-center mb-2"><h2 class="h6 fw-bold mb-0 flex-grow-1">Recent admin actions</h2>
              <a routerLink="/admin/audit" class="small">Audit log</a></div>
            @for (a of x.recentActions; track a.id) {
              <div class="ad-row"><span class="badge text-bg-light border me-2">{{ a.action }}</span>
                <span class="flex-grow-1 small min-w-0 text-truncate">{{ a.summary }}</span>
                <small class="text-body-secondary text-nowrap ms-2">{{ a.adminName }} · {{ a.createdAt | date: 'd MMM, h:mm a' }}</small></div>
            } @empty { <p class="small text-body-secondary mb-0">No admin actions yet.</p> }
          </section>
        </div>
      </div>
    }`,
  styles: [`
    .ad-kpis { display: grid; gap: .75rem; grid-template-columns: repeat(4, minmax(0, 1fr)); }
    .ad-kpis > * { display: block; min-width: 0; }
    @media (max-width: 1199.98px) { .ad-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    @media (max-width: 575.98px) { .ad-kpis { grid-template-columns: minmax(0, 1fr); } }
    .ad-todo { display: grid; gap: .5rem; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
    .ad-todo-item { display: flex; align-items: center; gap: .6rem; padding: .75rem 1rem; border-radius: 12px; text-decoration: none; color: inherit;
      background: rgba(217,119,6,.1); border: 1px solid rgba(217,119,6,.3); }
    .ad-todo-item > i:first-child { color: #b45309; font-size: 1.1rem; }
    .ad-todo-item:hover { background: rgba(217,119,6,.16); }
    .ad-row { display: flex; align-items: center; gap: .5rem; padding: .55rem .25rem; border-bottom: 1px solid var(--bs-border-color); text-decoration: none; color: inherit; }
    .ad-row:last-child { border-bottom: 0; }
    a.ad-row:hover { background: var(--bs-tertiary-bg); }
    .ad-dl { display: grid; grid-template-columns: 1fr auto; gap: .35rem 1rem; margin: 0; font-size: .875rem; }
    .ad-dl dt { font-weight: 400; color: var(--bs-secondary-color); } .ad-dl dd { margin: 0; font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }
  `],
})
export class AdminDashboardPage implements OnInit {
  private admin = inject(AdminService);
  support = inject(SupportService);
  d = signal<AdminDashboard | null>(null);

  ngOnInit(): void { this.admin.dashboard().subscribe(d => this.d.set(d)); this.support.refreshOpen(); }

  roles(x: AdminDashboard): { key: string; label: string; n: number }[] {
    return Object.entries(x.users).map(([key, n]) => ({ key, label: ROLE_LABELS[key] ?? key, n: n ?? 0 })).sort((a, b) => b.n - a.n);
  }
}
