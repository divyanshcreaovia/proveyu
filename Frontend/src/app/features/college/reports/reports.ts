import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import type { Options } from 'highcharts';
import { ReportService } from '../services/report.service';
import { AnalyticsService } from '../services/analytics.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ReportFormat, ReportFrequency, ReportSchedule, ReportTable, ReportType, SkillGapRow } from '../../../core/models/college.models';
import { Chart } from '../../../shared/ui/chart';
import { EmptyState, Skeleton } from '../../../shared/ui/states';

@Component({
  selector: 'pm-reports',
  imports: [FormsModule, DatePipe, Chart, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-xl-4">
        <section class="pm-rp-card">
          <h2 class="h6">Reports</h2>
          <label class="form-label small" for="rp-batch">Batch</label>
          <select id="rp-batch" class="form-select form-select-sm mb-3" [(ngModel)]="batch" (change)="preview()">
            <option [ngValue]="null">All batches</option>@for (b of college.batches(); track b.id) { <option [ngValue]="b.gradYear">{{ b.gradYear }}</option> }</select>
          <div class="list-group">
            @for (t of types(); track t.key) {
              <button type="button" class="list-group-item list-group-item-action small" [class.active]="type() === t.key" (click)="type.set(t.key); preview()">
                <strong class="d-block">{{ t.key }}</strong>{{ t.description }}</button>
            }
          </div>
        </section>
        <section class="pm-rp-card mt-3">
          <h2 class="h6">Scheduled e-mail exports</h2>
          @for (s of schedules(); track s.id) {
            <div class="small border-bottom py-2 d-flex justify-content-between gap-2">
              <span><strong>{{ s.reportType }}</strong> · {{ s.frequency }} · {{ s.format }}<br><span class="text-body-secondary">{{ s.recipients }} · next {{ s.nextRunAt | date: 'medium' }}</span></span>
              @if (college.canWrite()) {
                <span class="d-flex flex-column gap-1">
                  <button class="btn btn-sm btn-outline-secondary" (click)="toggle(s)">{{ s.active ? 'Pause' : 'Resume' }}</button>
                  <button class="btn btn-sm btn-outline-danger" (click)="removeSchedule(s)" aria-label="Delete schedule"><i class="bi bi-trash"></i></button></span>
              }
            </div>
          } @empty { <p class="small text-body-secondary">None.</p> }
          @if (college.canWrite()) {
            <form class="mt-2" (ngSubmit)="schedule()">
              <div class="d-flex gap-2 mb-2">
                <select class="form-select form-select-sm" aria-label="Frequency" [(ngModel)]="freq" name="f"><option>DAILY</option><option>WEEKLY</option><option>MONTHLY</option></select>
                <select class="form-select form-select-sm" aria-label="Format" [(ngModel)]="fmt" name="fm"><option>XLSX</option><option>PDF</option><option>CSV</option></select>
              </div>
              <input class="form-control form-control-sm mb-2" placeholder="recipients, comma separated" aria-label="Recipients" [(ngModel)]="recipients" name="r" />
              <button class="btn btn-sm btn-outline-primary w-100" [disabled]="!recipients.trim()">Schedule "{{ type() }}"</button>
            </form>
          }
        </section>
      </div>
      <div class="col-xl-8">
        <section class="pm-rp-card">
          <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
            <h2 class="h6 m-0">{{ table()?.title ?? type() }}</h2>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-primary" (click)="dl('xlsx')"><i class="bi bi-file-earmark-excel"></i> Excel</button>
              <button class="btn btn-outline-primary" (click)="dl('csv')">CSV</button>
              <button class="btn btn-outline-primary" (click)="dl('pdf')"><i class="bi bi-file-earmark-pdf"></i> PDF</button>
            </div>
          </div>
          @if (loading()) { <pm-skeleton [rows]="6" /> }
          @else if (table() && table()!.rows.length) {
            <div class="table-responsive pm-rp-scroll"><table class="table table-sm table-striped small mb-0">
              <thead><tr>@for (h of table()!.headers; track $index) { <th>{{ h }}</th> }</tr></thead>
              <tbody>@for (r of table()!.rows.slice(0, 200); track $index) { <tr [class.fw-bold]="isTotal(r)">@for (c of r; track $index) { <td>{{ c }}</td> }</tr> }</tbody></table></div>
            @if (table()!.rows.length > 200) { <p class="small text-body-secondary mt-2">Showing 200 of {{ table()!.rows.length }} rows. Download for the full report.</p> }
          } @else { <pm-empty-state icon="bi-table" title="No data for this report yet" /> }
        </section>
        <section class="pm-rp-card mt-3">
          <h2 class="h6">Skill-gap analysis <small class="text-body-secondary fw-normal">(drive demand vs. student supply, last 12 months)</small></h2>
          @if (gap().length) { <pm-chart [options]="gapChart()" [height]="320" label="Skill gap chart" /> }
          @else { <pm-empty-state icon="bi-bullseye" title="Not enough data" text="Add skills to drives and students to see gaps." /> }
        </section>
      </div>
    </div>`,
  styles: [`.pm-rp-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; } .pm-rp-scroll { max-height: 480px; overflow: auto; }`],
})
export class Reports implements OnInit {
  private svc = inject(ReportService);
  private analytics = inject(AnalyticsService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  types = signal<ReportType[]>([]);
  type = signal('placement-summary');
  table = signal<ReportTable | null>(null);
  loading = signal(false);
  schedules = signal<ReportSchedule[]>([]);
  gap = signal<SkillGapRow[]>([]);
  batch: number | null = null;
  freq: ReportFrequency = 'WEEKLY'; fmt: ReportFormat = 'XLSX'; recipients = '';

  gapChart = computed<Options>(() => {
    const g = this.gap().slice(0, 15);
    return { chart: { type: 'bar' }, xAxis: { categories: g.map(x => x.skill) }, yAxis: { title: { text: 'Count' }, allowDecimals: false },
      tooltip: { shared: true }, series: [{ type: 'bar', name: 'Drives requiring', data: g.map(x => x.demand) },
        { type: 'bar', name: 'Students with skill', data: g.map(x => x.supply) }] };
  });

  ngOnInit(): void {
    this.svc.types().subscribe(t => this.types.set(t));
    this.preview();
    this.loadSchedules();
    this.analytics.skillGap().subscribe(g => this.gap.set(g));
  }
  preview(): void {
    this.loading.set(true);
    this.svc.preview(this.type(), this.batch).subscribe({ next: t => { this.table.set(t); this.loading.set(false); }, error: () => this.loading.set(false) });
  }
  isTotal(r: (string | number)[]): boolean { return String(r[0]).includes('TOTAL'); }
  dl(f: 'xlsx' | 'csv' | 'pdf'): void { this.svc.download(this.type(), f, this.batch).subscribe(); }
  loadSchedules(): void { this.svc.schedules().subscribe(s => this.schedules.set(s)); }
  schedule(): void {
    this.svc.schedule({ reportType: this.type(), format: this.fmt, frequency: this.freq, recipients: this.recipients.trim() })
      .subscribe(() => { this.toast.success('Report scheduled'); this.recipients = ''; this.loadSchedules(); });
  }
  toggle(s: ReportSchedule): void { this.svc.toggle(s.id, !s.active).subscribe(() => this.loadSchedules()); }
  removeSchedule(s: ReportSchedule): void { this.svc.removeSchedule(s.id).subscribe(() => this.loadSchedules()); }
}
