import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EligibilityService } from '../../../../services/eligibility.service';
import { ApplicationService } from '../../../../services/application.service';
import { CollegeService } from '../../../../services/college.service';
import { ToastService } from '../../../../services/toast.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog';
import { Drive, EligibilityPreview, EligibilityRow, Override, OverrideAction } from '../../../../core/models/college.models';
import { Column, DataTable } from '../../../../shared/ui/data-table';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { StatCard } from '../../../../shared/ui/stat-card';

/** Preview eligible / ineligible students with reasons, manage audited overrides and register students. */
@Component({
  selector: 'pm-eligibility-tab',
  imports: [FormsModule, DatePipe, DataTable, StatusBadge, StatCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (p(); as pv) {
      <div class="pm-el-stats">
        <pm-stat-card label="Students" [value]="pv.totalStudents" icon="bi-people" />
        <pm-stat-card label="Eligible" [value]="pv.eligible" icon="bi-check2-circle" tone="green" />
        <pm-stat-card label="Not eligible" [value]="pv.ineligible" icon="bi-x-circle" tone="rose" />
        <pm-stat-card label="Registered" [value]="pv.applied" icon="bi-person-check" tone="violet" />
      </div>
    }
    <div class="d-flex flex-wrap gap-2 align-items-center my-3">
      <div class="btn-group btn-group-sm" role="group" aria-label="Filter">
        <button class="btn" [class.btn-primary]="filter() === null" [class.btn-outline-primary]="filter() !== null" (click)="setFilter(null)">All</button>
        <button class="btn" [class.btn-primary]="filter() === true" [class.btn-outline-primary]="filter() !== true" (click)="setFilter(true)">Eligible</button>
        <button class="btn" [class.btn-primary]="filter() === false" [class.btn-outline-primary]="filter() !== false" (click)="setFilter(false)">Not eligible</button>
      </div>
      <input type="search" class="form-control form-control-sm w-auto" placeholder="Search…" aria-label="Search students" [(ngModel)]="q" (ngModelChange)="q$.next()" />
      @if (college.canWrite() && drive().status !== 'COMPLETED' && drive().status !== 'CANCELLED') {
        <button class="btn btn-sm btn-primary ms-auto" [disabled]="!selected().length" (click)="register()"><i class="bi bi-person-plus"></i> Register selected ({{ selected().length }})</button>
      }
    </div>
    <pm-data-table [columns]="cols" [rows]="p()?.content ?? []" [total]="p()?.totalElements ?? 0" [page]="page()" [size]="size()" [loading]="loading()"
      [selectable]="college.canWrite()" [(selected)]="selected" [rowId]="rowId" [cell]="cellTpl" (pageChange)="load($event)" (sizeChange)="size.set($event); load(0)"
      emptyTitle="No students match" emptyIcon="bi-filter-circle" />
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('name') { <div class="fw-semibold">{{ row.name }}</div><div class="small text-body-secondary">{{ row.rollNumber }} · {{ row.branchCode }} · {{ row.batchYear }}</div> }
        @case ('eligible') { @if (row.eligible) { <span class="text-success fw-semibold"><i class="bi bi-check-circle-fill"></i> Eligible</span> }
          @else { <span class="text-danger fw-semibold"><i class="bi bi-x-circle-fill"></i> No</span> }
          @if (row.overridden) { <span class="badge text-bg-warning ms-1">override</span> } }
        @case ('reasons') { <span class="small">{{ row.reasons.join('; ') || '-' }}</span> }
        @case ('applicationStatus') { @if (row.applicationStatus) { <pm-status [value]="row.applicationStatus" /> } @else { - } }
        @case ('actions') {
          @if (college.canWrite()) {
            <div class="btn-group btn-group-sm">
              @if (!row.eligible) { <button class="btn btn-outline-success" (click)="override(row, 'INCLUDE')" title="Include with override">Include</button> }
              @else { <button class="btn btn-outline-danger" (click)="override(row, 'EXCLUDE')" title="Exclude with override">Exclude</button> }
            </div>
          }
        }
        @default { {{ row[col.key] ?? '-' }} }
      }
    </ng-template>
    @if (overrides().length) {
      <section class="pm-el-card mt-3"><h3 class="h6">Overrides (audited)</h3>
        @for (o of overrides(); track o.id) {
          <div class="d-flex justify-content-between align-items-center small border-bottom py-2 gap-2">
            <span><span class="badge" [class.text-bg-success]="o.action === 'INCLUDE'" [class.text-bg-danger]="o.action === 'EXCLUDE'">{{ o.action }}</span>
              {{ o.rollNumber }} {{ o.studentName }} - {{ o.reason }} <span class="text-body-secondary">({{ o.createdAt | date: 'short' }})</span></span>
            @if (college.canWrite()) { <button class="btn btn-sm btn-link text-danger" (click)="removeOverride(o)">Remove</button> }
          </div>
        }
      </section>
    }`,
  styles: [`.pm-el-stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: .75rem; }
    .pm-el-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }`],
})
export class EligibilityTab implements OnInit {
  private svc = inject(EligibilityService);
  private apps = inject(ApplicationService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  drive = input.required<Drive>();
  cols: Column[] = [{ key: 'name', label: 'Student' }, { key: 'cgpa', label: 'CGPA', hideSm: true }, { key: 'activeBacklogs', label: 'Backlogs', hideSm: true },
    { key: 'eligible', label: 'Eligible' }, { key: 'reasons', label: 'Reasons', hideSm: true }, { key: 'applicationStatus', label: 'Registered' }, { key: 'actions', label: '', align: 'end' }];
  p = signal<EligibilityPreview | null>(null);
  overrides = signal<Override[]>([]);
  filter = signal<boolean | null>(null);
  page = signal(0); size = signal(50); loading = signal(false);
  selected = signal<string[]>([]);
  q = '';
  q$ = new Subject<void>();
  rowId = (r: EligibilityRow) => r.studentId;

  constructor() { this.q$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.load(0)); }
  ngOnInit(): void { this.load(0); this.loadOverrides(); }

  setFilter(f: boolean | null): void { this.filter.set(f); this.load(0); }
  load(page = this.page()): void {
    this.loading.set(true);
    this.svc.preview(this.drive().id, this.filter(), this.q, page, this.size()).subscribe({
      next: p => { this.p.set(p); this.page.set(page); this.loading.set(false); }, error: () => this.loading.set(false),
    });
  }
  loadOverrides(): void { this.svc.overrides(this.drive().id).subscribe(o => this.overrides.set(o)); }

  async override(row: EligibilityRow, action: OverrideAction): Promise<void> {
    const r = await this.confirm.ask({ title: `${action === 'INCLUDE' ? 'Include' : 'Exclude'} ${row.name}?`,
      message: 'Overrides bypass the eligibility rules and are recorded in the audit log.', inputLabel: 'Reason (min 5 characters)', inputRequired: true, inputMinLength: 5,
      tone: action === 'EXCLUDE' ? 'danger' : 'primary' });
    if (!r.ok) return;
    this.svc.addOverride(this.drive().id, row.studentId, action, r.value).subscribe(() => { this.toast.success('Override saved'); this.load(); this.loadOverrides(); });
  }
  removeOverride(o: Override): void { this.svc.removeOverride(o.id).subscribe(() => { this.load(); this.loadOverrides(); }); }

  async register(): Promise<void> {
    const rows = (this.p()?.content ?? []).filter(r => this.selected().includes(r.studentId));
    const ineligible = rows.filter(r => !r.eligible).length;
    let reason: string | undefined;
    if (ineligible) {
      const r = await this.confirm.ask({ title: `${ineligible} selected student(s) are not eligible`, message: 'Registering them creates an audited INCLUDE override.',
        inputLabel: 'Override reason (min 5 characters)', inputRequired: true, inputMinLength: 5 });
      if (!r.ok) return;
      reason = r.value;
    }
    this.apps.add(this.drive().id, this.selected(), reason).subscribe(res => {
      this.toast.success(`${res.updated} registered${res.skipped.length ? `, ${res.skipped.length} skipped` : ''}`);
      if (res.skipped.length) this.toast.info(res.skipped.slice(0, 3).join(' | '));
      this.selected.set([]); this.load(); this.loadOverrides();
    });
  }
}
