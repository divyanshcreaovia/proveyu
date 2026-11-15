import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PlacementDriveService, DriveFilter } from '../../../services/placement-drive.service';
import { CollegeService } from '../../../services/college.service';
import { ToastService } from '../../../services/toast.service';
import { Drive, DriveStatus, DriveType } from '../../../core/models/college.models';
import { Column, DataTable } from '../../../shared/ui/data-table';
import { StatusBadge } from '../../../shared/ui/status-badge';

@Component({
  selector: 'pm-drives',
  imports: [FormsModule, RouterLink, DatePipe, DataTable, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-dr-bar">
      <input type="search" class="form-control form-control-sm pm-dr-q" placeholder="Search drives…" aria-label="Search drives" [(ngModel)]="f.q" (ngModelChange)="search$.next()" />
      <select class="form-select form-select-sm w-auto" aria-label="Status" [(ngModel)]="f.status" (change)="reload(0)">
        <option [ngValue]="null">All statuses</option>@for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }</select>
      <select class="form-select form-select-sm w-auto" aria-label="Type" [(ngModel)]="f.type" (change)="reload(0)">
        <option [ngValue]="null">All types</option>@for (t of types; track t) { <option [ngValue]="t">{{ t.replace('_', ' ') }}</option> }</select>
      <div class="form-check form-switch"><input id="dr-trash" class="form-check-input" type="checkbox" [(ngModel)]="f.deleted" (change)="reload(0)" />
        <label for="dr-trash" class="form-check-label small">Trash</label></div>
      <div class="ms-auto d-flex gap-2">
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-secondary" (click)="svc.export('xlsx', f).subscribe()">Excel</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('csv', f).subscribe()">CSV</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('pdf', f).subscribe()">PDF</button>
        </div>
        @if (college.canWrite()) { <a routerLink="/college/drives/new" class="btn btn-sm btn-primary"><i class="bi bi-plus-lg"></i> New drive</a> }
      </div>
    </div>
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [sort]="sort()" [dir]="dir()" [loading]="loading()"
      [clickable]="true" [cell]="cellTpl" (rowClick)="open($event)" (pageChange)="reload($event)" (sizeChange)="size.set($event); reload(0)"
      (sortChange)="sort.set($event.sort); dir.set($event.dir); reload(0)" emptyIcon="bi-briefcase" emptyTitle="No drives" emptyText="Create your first placement drive.">
      @if (college.canWrite() && !f.deleted) { <a pmEmpty routerLink="/college/drives/new" class="btn btn-sm btn-primary">New drive</a> }
    </pm-data-table>
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('title') { <div class="fw-semibold">{{ row.title }}</div><div class="small text-body-secondary">{{ row.companyName }} · {{ row.roleTitle }}</div> }
        @case ('ctcLpa') { {{ row.ctcLpa ? row.ctcLpa + ' LPA' : (row.stipendMonthly ? '₹' + row.stipendMonthly + '/mo' : '-') }}
          @if (row.tierName) { <span class="badge text-bg-light border ms-1">{{ row.tierName }}</span> } }
        @case ('driveType') { <span class="small">{{ row.driveType.replace('_', ' ') }}</span> }
        @case ('driveDate') { {{ row.driveDate ? (row.driveDate | date: 'mediumDate') : 'TBD' }} }
        @case ('applicants') { {{ applicants(row) }} }
        @case ('status') { <pm-status [value]="row.status" /> }
        @case ('actions') { @if (row.deleted && college.canWrite()) { <button class="btn btn-sm btn-outline-success" (click)="$event.stopPropagation(); restore(row)">Restore</button> } }
        @default { {{ row[col.key] ?? '-' }} }
      }
    </ng-template>`,
  styles: [`.pm-dr-bar { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; margin-bottom: .75rem; } .pm-dr-q { flex: 1 1 200px; max-width: 320px; }`],
})
export class Drives implements OnInit {
  svc = inject(PlacementDriveService);
  college = inject(CollegeService);
  private router = inject(Router);
  private toast = inject(ToastService);
  statuses: DriveStatus[] = ['DRAFT', 'PUBLISHED', 'ONGOING', 'COMPLETED', 'CANCELLED'];
  types: DriveType[] = ['ON_CAMPUS', 'OFF_CAMPUS', 'POOL', 'INTERNSHIP', 'PPO'];
  cols: Column[] = [
    { key: 'title', label: 'Drive', sortable: true }, { key: 'driveType', label: 'Type', hideSm: true },
    { key: 'ctcLpa', label: 'CTC', sortable: true }, { key: 'driveDate', label: 'Date', sortable: true, hideSm: true },
    { key: 'applicants', label: 'Applicants', align: 'center', hideSm: true }, { key: 'offersCount', label: 'Offers', align: 'center', hideSm: true },
    { key: 'status', label: 'Status', sortable: true }, { key: 'actions', label: '', align: 'end' },
  ];
  f: DriveFilter = { q: '', status: null, type: null, deleted: false };
  rows = signal<Drive[]>([]); total = signal(0); page = signal(0); size = signal(20); sort = signal('createdAt'); dir = signal<'asc' | 'desc'>('desc');
  loading = signal(false);
  search$ = new Subject<void>();

  constructor() { this.search$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.reload(0)); }
  ngOnInit(): void { this.reload(0); }

  reload(page = this.page()): void {
    this.loading.set(true);
    this.svc.list(this.f, page, this.size(), this.sort(), this.dir()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(p.page); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
  applicants(d: Drive): number { return Object.values(d.applicationCounts).reduce((a, b) => a + b, 0); }
  open(d: Drive): void { if (!d.deleted) this.router.navigate(['/college/drives', d.id]); }
  restore(d: Drive): void { this.svc.restore(d.id).subscribe(() => { this.toast.success('Drive restored'); this.reload(); }); }
}
