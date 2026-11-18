import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of } from 'rxjs';
import { StudentPortalService } from '../services/student-portal.service';
import { Offer, PortalAnnouncement, PortalApplication, PortalDrive, PortalInterview, PortalRound, StudentDocument } from '../../../core/models/college.models';
import type { Options } from 'highcharts';
import { StatCard } from '../../../shared/ui/stat-card';
import { Chart } from '../../../shared/ui/chart';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { Skeleton } from '../../../shared/ui/states';

const ACTIVE = ['APPLIED', 'SHORTLISTED', 'WAITLISTED', 'IN_PROCESS'];

@Component({
  selector: 'pm-student-dashboard',
  imports: [DatePipe, RouterLink, StatCard, StatusBadge, Skeleton, Chart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  styles: [`
    .st-ready-pct { font-weight: 800; font-size: 1.1rem; color: var(--pm-link); } .st-ready-done { color: #16a34a; }
    .st-ready-track { height: 10px; border-radius: 999px; background: var(--pm-soft); overflow: hidden; }
    .st-ready-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #0b5cff, #00b4ff); transition: width .4s ease; }
    .st-ready-list { list-style: none; padding: 0; margin: 0; display: grid; gap: .35rem; }
    .st-ready-item { display: flex; align-items: center; gap: .6rem; padding: .5rem .65rem; border-radius: 10px; font-size: .875rem; text-decoration: none; }
    .st-ready-ok { color: var(--pm-muted); } .st-ready-ok i { color: #16a34a; }
    .st-ready-todo { color: var(--pm-heading); background: var(--pm-soft); font-weight: 600; }
    .st-ready-todo i:first-child { color: var(--pm-muted); }
    .st-ready-todo:hover, .st-ready-todo:focus-visible { background: rgba(11, 92, 255, .1); color: var(--pm-link); }
    .st-ready-todo:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 1px; }
  `],
  template: `
    @let p = portal.me()!;
    <section class="st-card mb-3 d-flex flex-wrap gap-3 align-items-center">
      <div class="flex-grow-1 min-w-0">
        <h2 class="h4 st-h mb-1">Welcome back, {{ portal.firstName() }}</h2>
        <p class="st-muted small mb-0">{{ p.rollNumber }} · {{ p.branch ?? '-' }} · Batch {{ p.batchYear }} · CGPA {{ p.cgpa ?? '-' }}
          @if (p.activeBacklogs) { · {{ p.activeBacklogs }} active backlog(s) }</p>
      </div>
      <pm-status [value]="p.status" />
      @if (p.highestCtcLpa) { <span class="st-chip"><i class="bi bi-trophy"></i> Best offer {{ p.highestCtcLpa }} LPA</span> }
    </section>
    @if (loading()) { <pm-skeleton [rows]="6" /> } @else {
      <div class="row g-3 mb-3">
        <div class="col-6 col-xl-3"><a routerLink="/student/drives" class="text-decoration-none"><pm-stat-card label="Open for you" [value]="openDrives()" icon="bi-briefcase" tone="blue" hint="Eligible drives you can apply to" /></a></div>
        <div class="col-6 col-xl-3"><a routerLink="/student/applications" class="text-decoration-none"><pm-stat-card label="Active applications" [value]="activeApps()" icon="bi-list-check" tone="violet" /></a></div>
        <div class="col-6 col-xl-3"><a routerLink="/student/interviews" class="text-decoration-none"><pm-stat-card label="Upcoming interviews" [value]="upcoming().length" icon="bi-calendar3" tone="amber" /></a></div>
        <div class="col-6 col-xl-3"><a routerLink="/student/offers" class="text-decoration-none"><pm-stat-card label="Offers" [value]="offers().length" icon="bi-award" tone="green" [hint]="pendingOffers() ? pendingOffers() + ' waiting for your reply' : ''" /></a></div>
      </div>
      <div class="row g-3 mb-3">
        <div class="col-6 col-xl-3"><pm-stat-card label="Eligible for" [value]="eligibilityPct() + '%'" icon="bi-patch-check" tone="slate" [hint]="eligibleCount() + ' of ' + drives().length + ' drives'" /></div>
        <div class="col-6 col-xl-3"><pm-stat-card label="Success rate" [value]="successPct()" icon="bi-graph-up-arrow" tone="green" hint="Selected ÷ applications decided" /></div>
        <div class="col-6 col-xl-3"><pm-stat-card label="Avg. package open to you" [value]="avgCtc()" icon="bi-cash-coin" tone="blue" hint="Across drives you can apply to" /></div>
        <div class="col-6 col-xl-3"><a routerLink="/student/documents" class="text-decoration-none"><pm-stat-card label="Documents verified" [value]="verifiedDocs() + ' / ' + docs().length" icon="bi-shield-check" tone="violet" /></a></div>
      </div>
      <div class="row g-3 mb-3">
        <div class="col-lg-4"><section class="st-card h-100"><h3 class="h6 st-h mb-1">My applications</h3><p class="small st-muted mb-2">Where each application stands</p>
          @if (apps().length) { <pm-chart [options]="statusChart()" [height]="240" label="Applications by status" /> }
          @else { <p class="small st-muted py-5 text-center mb-0">Apply to a drive to see this chart.</p> }</section></div>
        <div class="col-lg-4"><section class="st-card h-100"><h3 class="h6 st-h mb-1">My placement funnel</h3><p class="small st-muted mb-2">From applying to offers</p>
          <pm-chart [options]="funnelChart()" [height]="240" label="Placement funnel" /></section></div>
        <div class="col-lg-4"><section class="st-card h-100"><h3 class="h6 st-h mb-1">Drive eligibility</h3><p class="small st-muted mb-2">Drives in your college this season</p>
          @if (drives().length) { <pm-chart [options]="eligibilityChart()" [height]="240" label="Drive eligibility" /> }
          @else { <p class="small st-muted py-5 text-center mb-0">No drives published yet.</p> }</section></div>
        <div class="col-lg-8"><section class="st-card h-100"><h3 class="h6 st-h mb-1">Packages on offer</h3><p class="small st-muted mb-2">CTC (LPA) of drives in your college; your best offer is the dashed line</p>
          @if (ctcDrives().length) { <pm-chart [options]="ctcChart()" [height]="260" label="Package by company" /> }
          @else { <p class="small st-muted py-5 text-center mb-0">No packages announced yet.</p> }</section></div>
        <div class="col-lg-4"><section class="st-card h-100"><h3 class="h6 st-h mb-1">Round results</h3><p class="small st-muted mb-2">All rounds you have taken</p>
          @if (roundTotal()) { <pm-chart [options]="roundChart()" [height]="260" label="Round results" /> }
          @else { <p class="small st-muted py-5 text-center mb-0">No round results yet.</p> }</section></div>
      </div>
      <div class="row g-3">
        <div class="col-lg-7">
          <section class="st-card mb-3">
            <div class="d-flex justify-content-between align-items-center mb-2"><h3 class="h6 st-h mb-0">Next interview</h3><a routerLink="/student/interviews" class="small">All</a></div>
            @if (upcoming()[0]; as i) {
              <div class="fw-semibold">{{ i.companyName }} · {{ i.roundName ?? i.driveTitle }}</div>
              <div class="small st-muted">{{ i.startsAt | date: 'EEE d MMM, h:mm a' }} – {{ i.endsAt | date: 'h:mm a' }} · {{ i.room ?? 'Online' }}</div>
              @if (i.meetingLink) { <a class="btn btn-primary btn-sm mt-2" [href]="i.meetingLink" target="_blank" rel="noopener">Join meeting</a> }
            } @else { <p class="small st-muted mb-0">No interviews scheduled yet.</p> }
          </section>
          <section class="st-card">
            <div class="d-flex justify-content-between align-items-center mb-2"><h3 class="h6 st-h mb-0">Closing soon</h3><a routerLink="/student/drives" class="small">All drives</a></div>
            @for (d of closingSoon(); track d.id) {
              <div class="d-flex gap-2 align-items-center py-2 border-bottom small"><span class="flex-grow-1"><strong>{{ d.companyName }}</strong> · {{ d.roleTitle }}</span>
                <span class="st-muted">by {{ d.registrationDeadline | date: 'd MMM, h:mm a' }}</span></div>
            } @empty { <p class="small st-muted mb-0">Nothing closing soon.</p> }
          </section>
        </div>
        <div class="col-lg-5">
          <section class="st-card mb-3">
            <div class="d-flex justify-content-between align-items-center mb-2"><h3 class="h6 st-h mb-0">Announcements</h3><a routerLink="/student/announcements" class="small">All</a></div>
            @for (a of announcements(); track a.id) {
              <div class="py-2 border-bottom small" [class.fw-semibold]="!a.read">{{ a.title }}<div class="st-muted fw-normal">{{ a.createdAt | date: 'mediumDate' }} · {{ a.fromName }}</div></div>
            } @empty { <p class="small st-muted mb-0">No announcements yet.</p> }
          </section>
          <section class="st-card">
            <div class="d-flex justify-content-between align-items-baseline mb-2">
              <h3 class="h6 st-h mb-0">Placement readiness</h3>
              <span class="st-ready-pct" [class.st-ready-done]="readiness().pct === 100">{{ readiness().pct }}%</span>
            </div>
            <div class="st-ready-track" role="progressbar" [attr.aria-valuenow]="readiness().pct" aria-valuemin="0" aria-valuemax="100" aria-label="Profile readiness">
              <div class="st-ready-fill" [style.width.%]="readiness().pct"></div></div>
            <p class="small st-muted mt-2 mb-2">{{ readiness().left ? readiness().left + ' step(s) left to a complete placement profile' : 'Your placement profile is complete' }}</p>
            <ul class="st-ready-list">
              @for (c of readiness().items; track c.label) {
                <li>
                  @if (c.done) {
                    <span class="st-ready-item st-ready-ok"><i class="bi bi-check-circle-fill"></i><span>{{ c.label }}</span></span>
                  } @else {
                    <a class="st-ready-item st-ready-todo" [routerLink]="c.link"><i class="bi bi-circle"></i><span>{{ c.label }}</span><i class="bi bi-chevron-right ms-auto"></i></a>
                  }
                </li>
              }
            </ul>
          </section>
        </div>
      </div>
    }`,
})
export class StudentDashboard implements OnInit {
  portal = inject(StudentPortalService);
  loading = signal(true);
  drives = signal<PortalDrive[]>([]);
  apps = signal<PortalApplication[]>([]);
  interviews = signal<PortalInterview[]>([]);
  offers = signal<Offer[]>([]);
  docs = signal<StudentDocument[]>([]);
  announcements = signal<PortalAnnouncement[]>([]);
  rounds = signal<PortalRound[]>([]);

  eligibleCount = computed(() => this.drives().filter(d => d.eligible).length);
  eligibilityPct = computed(() => this.drives().length ? Math.round(this.eligibleCount() * 100 / this.drives().length) : 0);
  successPct = computed(() => {
    const decided = this.apps().filter(a => a.status === 'SELECTED' || a.status === 'REJECTED').length;
    return decided ? Math.round(this.apps().filter(a => a.status === 'SELECTED').length * 100 / decided) + '%' : '-';
  });
  avgCtc = computed(() => {
    const c = this.drives().filter(d => d.canApply && d.ctcLpa).map(d => d.ctcLpa!);
    return c.length ? (c.reduce((a, b) => a + b, 0) / c.length).toFixed(1) + ' LPA' : '-';
  });
  verifiedDocs = computed(() => this.docs().filter(d => d.verificationStatus === 'VERIFIED').length);
  ctcDrives = computed(() => this.drives().filter(d => d.ctcLpa).sort((a, b) => b.ctcLpa! - a.ctcLpa!).slice(0, 10));
  roundTotal = computed(() => this.rounds().filter(r => r.result && r.result !== 'PENDING').length);

  statusChart = computed<Options>(() => {
    const counts = new Map<string, number>();
    this.apps().forEach(a => counts.set(a.status, (counts.get(a.status) ?? 0) + 1));
    return { chart: { type: 'pie' }, tooltip: { pointFormat: '<b>{point.y}</b>' },
      plotOptions: { pie: { innerSize: '60%', dataLabels: { enabled: true, format: '{point.name}: {point.y}', distance: 12 } } },
      series: [{ type: 'pie', name: 'Applications', data: [...counts].map(([k, v]) => ({ name: k.replace('_', ' ').toLowerCase(), y: v })) }] };
  });
  funnelChart = computed<Options>(() => {
    const a = this.apps().filter(x => x.status !== 'WITHDRAWN');
    const shortlisted = a.filter(x => ['SHORTLISTED', 'IN_PROCESS', 'SELECTED'].includes(x.status)).length;
    const selected = a.filter(x => x.status === 'SELECTED').length;
    return { chart: { type: 'bar' }, legend: { enabled: false }, tooltip: { pointFormat: '<b>{point.y}</b>' },
      xAxis: { categories: ['Applied', 'Shortlisted', 'Selected', 'Offers'] }, yAxis: { title: { text: undefined }, allowDecimals: false },
      plotOptions: { bar: { colorByPoint: true, borderRadius: 6, dataLabels: { enabled: true } } },
      series: [{ type: 'bar', name: 'Count', data: [a.length, shortlisted, selected, this.offers().length] }] };
  });
  eligibilityChart = computed<Options>(() => {
    const d = this.drives();
    const applied = d.filter(x => x.applicationStatus && x.applicationStatus !== 'WITHDRAWN').length;
    const open = d.filter(x => x.canApply).length;
    const notEligible = d.filter(x => !x.eligible && !(x.applicationStatus && x.applicationStatus !== 'WITHDRAWN')).length;
    const closed = Math.max(0, d.length - applied - open - notEligible);
    return { chart: { type: 'pie' }, tooltip: { pointFormat: '<b>{point.y}</b>' },
      plotOptions: { pie: { innerSize: '60%', dataLabels: { enabled: true, format: '{point.name}: {point.y}', distance: 12 } } },
      series: [{ type: 'pie', name: 'Drives', data: [
        { name: 'Applied', y: applied }, { name: 'Open for you', y: open }, { name: 'Not eligible', y: notEligible }, { name: 'Closed', y: closed },
      ].filter(x => x.y) }] };
  });
  ctcChart = computed<Options>(() => {
    const best = this.portal.me()?.highestCtcLpa ?? Math.max(0, ...this.offers().filter(o => o.status !== 'DECLINED' && o.status !== 'REVOKED').map(o => o.ctcLpa));
    const d = this.ctcDrives();
    return { chart: { type: 'column' }, legend: { enabled: false }, tooltip: { pointFormat: '<b>{point.y} LPA</b>' },
      xAxis: { categories: d.map(x => x.companyName) },
      yAxis: { title: { text: 'LPA' }, plotLines: best ? [{ value: best, dashStyle: 'Dash', color: '#16a34a', width: 2, zIndex: 5,
        label: { text: 'Your best offer ' + best + ' LPA', style: { color: '#16a34a', fontWeight: '600' } } }] : [] },
      plotOptions: { column: { borderRadius: 6, dataLabels: { enabled: true, format: '{point.y}' } } },
      series: [{ type: 'column', name: 'CTC', data: d.map(x => ({ y: x.ctcLpa!, color: x.canApply ? '#2563eb' : x.applicationStatus ? '#7c3aed' : '#94a3b8' })) }] };
  });
  roundChart = computed<Options>(() => {
    const r = this.rounds().filter(x => x.result && x.result !== 'PENDING');
    const n = (v: string) => r.filter(x => x.result === v).length;
    return { chart: { type: 'pie' }, colors: ['#16a34a', '#e11d48', '#f59e0b', '#94a3b8'], tooltip: { pointFormat: '<b>{point.y}</b>' },
      plotOptions: { pie: { innerSize: '60%', dataLabels: { enabled: true, format: '{point.name}: {point.y}', distance: 12 } } },
      series: [{ type: 'pie', name: 'Rounds', data: [{ name: 'Passed', y: n('PASS') }, { name: 'Failed', y: n('FAIL') }, { name: 'On hold', y: n('HOLD') },
        { name: 'Absent', y: n('ABSENT') }].filter(x => x.y) }] };
  });

  openDrives = computed(() => this.drives().filter(d => d.canApply).length);
  activeApps = computed(() => this.apps().filter(a => ACTIVE.includes(a.status)).length);
  pendingOffers = computed(() => this.offers().filter(o => o.status === 'OFFERED').length);
  upcoming = computed(() => this.interviews().filter(i => i.status === 'SCHEDULED' && new Date(i.endsAt) > new Date()));
  closingSoon = computed(() => this.drives().filter(d => d.canApply && d.registrationDeadline).slice(0, 5));
  readiness = computed(() => {
    const p = this.portal.me()!;
    const has = (t: string) => this.docs().some(d => d.docType === t && d.verificationStatus !== 'REJECTED');
    const items = [
      { label: 'Upload your resume', done: has('RESUME'), link: '/student/documents' },
      { label: 'Upload marksheets', done: has('MARKSHEET_10') && has('MARKSHEET_12'), link: '/student/documents' },
      { label: 'Add your skills', done: p.skills.length > 0, link: '/student/profile' },
      { label: 'Add LinkedIn profile', done: !!p.linkedinUrl, link: '/student/profile' },
      { label: 'Add phone number', done: !!p.phone, link: '/student/profile' },
    ];
    const done = items.filter(i => i.done).length;
    return { items, pct: Math.round(done * 100 / items.length), left: items.length - done };
  });

  ngOnInit(): void {
    forkJoin({ d: this.portal.drives(), a: this.portal.applications(), i: this.portal.interviews(), o: this.portal.offers(),
      docs: this.portal.documents(), ann: this.portal.announcements(0, 4) }).subscribe({
      next: r => {
        this.drives.set(r.d); this.apps.set(r.a); this.interviews.set(r.i); this.offers.set(r.o); this.docs.set(r.docs); this.announcements.set(r.ann.content);
        this.loading.set(false);
        // round results per application (a student has few applications, so one call each is fine)
        const ids = r.a.filter(a => a.status !== 'WITHDRAWN').map(a => a.id);
        (ids.length ? forkJoin(ids.map(id => this.portal.rounds(id))).pipe(map(l => l.flat())) : of([] as PortalRound[]))
          .subscribe(rs => this.rounds.set(rs));
      },
      error: () => this.loading.set(false),
    });
  }
}
