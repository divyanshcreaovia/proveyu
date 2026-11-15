import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { Options } from 'highcharts';
import { AnalyticsService } from '../../../services/analytics.service';
import { CollegeService } from '../../../services/college.service';
import { ReportService } from '../../../services/report.service';
import { Dashboard } from '../../../core/models/college.models';
import { StatCard } from '../../../shared/ui/stat-card';
import { Chart } from '../../../shared/ui/chart';
import { ErrorState, Skeleton, EmptyState } from '../../../shared/ui/states';

@Component({
  selector: 'pm-college-dashboard',
  imports: [FormsModule, RouterLink, StatCard, Chart, Skeleton, ErrorState, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-dash-filters">
      <div>
        <label for="d-batch" class="form-label small mb-1">Batch</label>
        <select id="d-batch" class="form-select form-select-sm" [(ngModel)]="batch" (ngModelChange)="load()">
          <option [ngValue]="null">All batches</option>
          @for (b of data()?.batches ?? []; track b) { <option [ngValue]="b">{{ b }}</option> }
        </select>
      </div>
      <div><label for="d-from" class="form-label small mb-1">Offers from</label>
        <input id="d-from" type="date" class="form-control form-control-sm" [(ngModel)]="from" (change)="load()" /></div>
      <div><label for="d-to" class="form-label small mb-1">to</label>
        <input id="d-to" type="date" class="form-control form-control-sm" [(ngModel)]="to" (change)="load()" /></div>
      <div class="ms-md-auto d-flex gap-2 align-items-end">
        <button class="btn btn-sm btn-outline-secondary" (click)="reset()">Reset</button>
        <button class="btn btn-sm btn-outline-primary" (click)="exportSummary()"><i class="bi bi-download"></i> Placement summary</button>
      </div>
    </div>

    @if (error()) { <pm-error-state (retry)="load()" /> }
    @else if (!data()) { <pm-skeleton [rows]="8" [height]="60" /> }
    @else {
      @let k = data()!.kpis;
      @if (k.totalStudents === 0) {
        <div class="pm-dash-card mb-3">
          <pm-empty-state icon="bi-rocket-takeoff" title="Welcome! Let's set up your placement season"
                          text="Add branches, import your students and create your first drive.">
            <div class="d-flex gap-2 justify-content-center flex-wrap">
              @if (college.isAdmin()) { <a routerLink="/college/settings" class="btn btn-sm btn-outline-primary">1. Add branches</a> }
              <a routerLink="/college/students/import" class="btn btn-sm btn-outline-primary">2. Import students</a>
              <a routerLink="/college/drives/new" class="btn btn-sm btn-primary">3. Create a drive</a>
            </div>
          </pm-empty-state>
        </div>
      }
      <div class="pm-dash-kpis">
        <pm-stat-card label="Total students" [value]="k.totalStudents" icon="bi-people" tone="blue" [hint]="k.eligibleStudents + ' eligible'" />
        <pm-stat-card label="Registered" [value]="k.registeredStudents" icon="bi-person-check" tone="violet" hint="applied to ≥ 1 drive" />
        <pm-stat-card label="Placed" [value]="k.placedStudents" icon="bi-trophy" tone="green" [hint]="k.placementPercent + '% of eligible'" />
        <pm-stat-card label="Placement %" [value]="k.placementPercent + '%'" icon="bi-percent" tone="green" />
        <pm-stat-card label="Average CTC" [value]="lpa(k.avgCtc)" icon="bi-cash-stack" tone="amber" [hint]="'Median ' + lpa(k.medianCtc)" />
        <pm-stat-card label="Highest CTC" [value]="lpa(k.highestCtc)" icon="bi-graph-up-arrow" tone="rose" />
        <pm-stat-card label="Active drives" [value]="k.activeDrives" icon="bi-briefcase" tone="blue" />
        <pm-stat-card label="Interviews (7 days)" [value]="k.upcomingInterviews" icon="bi-calendar-event" tone="violet" />
        <pm-stat-card label="Pending offers" [value]="k.pendingOffers" icon="bi-hourglass" tone="amber" [hint]="k.totalOffers + ' active offers'" />
      </div>

      <div class="row g-3 mt-1">
        <div class="col-xl-8"><section class="pm-dash-card"><h2 class="pm-dash-h">Branch-wise placement</h2>
          <pm-chart [options]="branchChart()" label="Branch-wise placement chart" /></section></div>
        <div class="col-xl-4"><section class="pm-dash-card"><h2 class="pm-dash-h">Placement funnel</h2>
          <pm-chart [options]="funnelChart()" label="Placement funnel" /></section></div>
        <div class="col-xl-6"><section class="pm-dash-card"><h2 class="pm-dash-h">Monthly offers trend</h2>
          <pm-chart [options]="trendChart()" label="Monthly offers trend" /></section></div>
        <div class="col-xl-6"><section class="pm-dash-card"><h2 class="pm-dash-h">CTC distribution (best offer per student)</h2>
          <pm-chart [options]="ctcChart()" label="CTC distribution" /></section></div>
        <div class="col-xl-8"><section class="pm-dash-card"><h2 class="pm-dash-h">Company-wise offers (top 10)</h2>
          @if (data()!.companyWise.length) { <pm-chart [options]="companyChart()" label="Company-wise offers" /> }
          @else { <pm-empty-state icon="bi-building" title="No offers yet" /> }</section></div>
        <div class="col-xl-4"><section class="pm-dash-card"><h2 class="pm-dash-h">Offers by tier</h2>
          <pm-chart [options]="tierChart()" label="Offers by tier" /></section></div>
      </div>
    }`,
  styles: [`
    .pm-dash-filters { display: flex; flex-wrap: wrap; gap: .75rem; align-items: flex-end; margin-bottom: 1rem; }
    .pm-dash-kpis { display: grid; gap: .75rem; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); }
    .pm-dash-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; height: 100%; }
    .pm-dash-h { font-size: .9rem; font-weight: 700; margin-bottom: .5rem; }
  `],
})
export class CollegeDashboard implements OnInit {
  private analytics = inject(AnalyticsService);
  private reports = inject(ReportService);
  college = inject(CollegeService);
  data = signal<Dashboard | null>(null);
  error = signal(false);
  batch: number | null = null;
  from = '';
  to = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.error.set(false);
    this.analytics.dashboard(this.batch, this.from || null, this.to || null).subscribe({
      next: d => this.data.set(d),
      error: () => this.error.set(true),
    });
  }
  reset(): void { this.batch = null; this.from = ''; this.to = ''; this.load(); }
  exportSummary(): void { this.reports.download('placement-summary', 'xlsx', this.batch).subscribe(); }
  lpa(v?: number | null): string { return v === null || v === undefined ? '-' : `${v} LPA`; }

  branchChart = computed<Options>(() => {
    const b = this.data()?.branchWise ?? [];
    return {
      chart: { type: 'column' }, xAxis: { categories: b.map(x => x.code) },
      yAxis: [{ title: { text: 'Students' } }, { title: { text: 'Placement %' }, opposite: true, max: 100 }],
      tooltip: { shared: true },
      series: [
        { type: 'column', name: 'Total', data: b.map(x => x.total) },
        { type: 'column', name: 'Placed', data: b.map(x => x.placed) },
        { type: 'spline', name: 'Placement %', yAxis: 1, data: b.map(x => x.percent), tooltip: { valueSuffix: '%' } },
      ],
    };
  });
  funnelChart = computed<Options>(() => {
    const f = this.data()?.funnel ?? [];
    return { chart: { type: 'bar' }, xAxis: { categories: f.map(x => x.stage) }, yAxis: { title: { text: undefined } }, legend: { enabled: false },
      plotOptions: { bar: { colorByPoint: true, dataLabels: { enabled: true } } }, series: [{ type: 'bar', name: 'Students', data: f.map(x => x.count) }] };
  });
  trendChart = computed<Options>(() => {
    const t = this.data()?.monthlyTrend ?? [];
    return { chart: { type: 'areaspline' }, xAxis: { categories: t.map(x => x.month) }, yAxis: { title: { text: undefined }, allowDecimals: false },
      series: [{ type: 'areaspline', name: 'Offers', data: t.map(x => x.offers), fillOpacity: .15 },
               { type: 'areaspline', name: 'Accepted', data: t.map(x => x.placed), fillOpacity: .15 }] };
  });
  ctcChart = computed<Options>(() => {
    const c = this.data()?.ctcDistribution ?? [];
    return { chart: { type: 'column' }, xAxis: { categories: c.map(x => x.label) }, yAxis: { title: { text: 'Students' }, allowDecimals: false },
      legend: { enabled: false }, series: [{ type: 'column', name: 'Students', data: c.map(x => x.count), color: '#7c3aed' }] };
  });
  companyChart = computed<Options>(() => {
    const c = this.data()?.companyWise ?? [];
    return { chart: { type: 'bar' }, xAxis: { categories: c.map(x => x.label) }, yAxis: { title: { text: 'Offers' }, allowDecimals: false },
      legend: { enabled: false }, series: [{ type: 'bar', name: 'Offers', data: c.map(x => x.count), color: '#16a34a' }] };
  });
  tierChart = computed<Options>(() => {
    const t = this.data()?.tierWise ?? [];
    return { chart: { type: 'pie' }, plotOptions: { pie: { innerSize: '55%', dataLabels: { enabled: true, format: '{point.name}: {point.y}' } } },
      series: [{ type: 'pie', name: 'Offers', data: t.map(x => ({ name: x.label, y: x.count })) }] };
  });
}
