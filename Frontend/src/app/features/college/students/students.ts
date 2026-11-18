import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { StudentService } from '../services/student.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Student, StudentFilter, StudentStatus } from '../../../core/models/college.models';
import { Column, DataTable } from '../../../shared/ui/data-table';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { ErrorState } from '../../../shared/ui/states';
import { StudentForm } from './student-form';
import { StudentDrawer } from './student-drawer';

@Component({
  selector: 'pm-students',
  imports: [FormsModule, RouterLink, DataTable, StatusBadge, ErrorState, StudentForm, StudentDrawer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-stu-bar">
      <div class="pm-stu-search">
        <label for="s-q" class="visually-hidden">Search students</label>
        <input id="s-q" type="search" class="form-control form-control-sm" placeholder="Search roll no, name, e-mail…" [(ngModel)]="f.q" (ngModelChange)="search$.next()" />
      </div>
      <button class="btn btn-sm btn-outline-secondary" (click)="showFilters.set(!showFilters())" [attr.aria-expanded]="showFilters()">
        <i class="bi bi-funnel"></i> Filters</button>
      <div class="form-check form-switch ms-1">
        <input id="s-trash" class="form-check-input" type="checkbox" [(ngModel)]="f.deleted" (change)="reload(0)" />
        <label for="s-trash" class="form-check-label small">Trash</label>
      </div>
      <div class="ms-auto d-flex gap-2 flex-wrap">
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-secondary" (click)="exp('xlsx')"><i class="bi bi-file-earmark-excel"></i> Excel</button>
          <button class="btn btn-outline-secondary" (click)="exp('csv')">CSV</button>
          <button class="btn btn-outline-secondary" (click)="exp('pdf')">PDF</button>
        </div>
        @if (college.canWrite()) {
          <a routerLink="/college/students/import" class="btn btn-sm btn-outline-primary"><i class="bi bi-upload"></i> Import</a>
          <button class="btn btn-sm btn-primary" (click)="openForm(null)"><i class="bi bi-plus-lg"></i> Add student</button>
        }
      </div>
    </div>
    @if (showFilters()) {
      <div class="pm-stu-filters">
        <div><label class="form-label small mb-1" for="sf-batch">Batch</label>
          <select id="sf-batch" class="form-select form-select-sm" [(ngModel)]="f.batchYear" (change)="reload(0)">
            <option [ngValue]="null">All</option>@for (b of college.batches(); track b.id) { <option [ngValue]="b.gradYear">{{ b.gradYear }}</option> }</select></div>
        <div><label class="form-label small mb-1" for="sf-branch">Branch</label>
          <select id="sf-branch" class="form-select form-select-sm" [(ngModel)]="f.branchId" (change)="reload(0)">
            <option [ngValue]="null">All</option>@for (b of college.branches(); track b.id) { <option [ngValue]="b.id">{{ b.code }}</option> }</select></div>
        <div><label class="form-label small mb-1" for="sf-min">CGPA ≥</label>
          <input id="sf-min" type="number" step="0.1" class="form-control form-control-sm" [(ngModel)]="f.minCgpa" (change)="reload(0)" /></div>
        <div><label class="form-label small mb-1" for="sf-max">CGPA ≤</label>
          <input id="sf-max" type="number" step="0.1" class="form-control form-control-sm" [(ngModel)]="f.maxCgpa" (change)="reload(0)" /></div>
        <div><label class="form-label small mb-1" for="sf-bl">Max backlogs</label>
          <input id="sf-bl" type="number" class="form-control form-control-sm" [(ngModel)]="f.maxBacklogs" (change)="reload(0)" /></div>
        <div><label class="form-label small mb-1" for="sf-status">Status</label>
          <select id="sf-status" class="form-select form-select-sm" [(ngModel)]="f.status" (change)="reload(0)">
            <option [ngValue]="null">All</option>@for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }</select></div>
        <div><label class="form-label small mb-1" for="sf-placed">Placement</label>
          <select id="sf-placed" class="form-select form-select-sm" [(ngModel)]="f.placed" (change)="reload(0)">
            <option [ngValue]="null">Any</option><option [ngValue]="true">Placed</option><option [ngValue]="false">Unplaced</option></select></div>
        <div><label class="form-label small mb-1" for="sf-skill">Skill</label>
          <input id="sf-skill" class="form-control form-control-sm" [(ngModel)]="f.skill" (ngModelChange)="search$.next()" /></div>
        <div class="d-flex align-items-end"><button class="btn btn-sm btn-link" (click)="clear()">Clear</button></div>
      </div>
    }

    @if (error()) { <pm-error-state (retry)="reload()" /> }
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [sort]="sort()" [dir]="dir()"
      [loading]="loading()" [clickable]="true" [cell]="cellTpl" (rowClick)="openId.set($event.id)"
      (pageChange)="reload($event)" (sizeChange)="size.set($event); reload(0)" (sortChange)="sort.set($event.sort); dir.set($event.dir); reload(0)"
      [emptyTitle]="f.deleted ? 'Trash is empty' : 'No students yet'" [emptyText]="f.deleted ? '' : 'Add students one by one or import an Excel sheet.'"
      emptyIcon="bi-people">
      @if (!f.deleted && college.canWrite()) { <a pmEmpty routerLink="/college/students/import" class="btn btn-sm btn-primary">Import students</a> }
    </pm-data-table>

    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('name') { <div class="fw-semibold">{{ row.fullName }}</div><div class="small text-body-secondary d-md-none">{{ row.rollNumber }}</div> }
        @case ('status') { <pm-status [value]="row.status" /> }
        @case ('highestCtcLpa') { {{ row.highestCtcLpa ? row.highestCtcLpa + ' LPA' : '-' }} }
        @case ('skills') { <span class="small text-body-secondary">{{ row.skills.slice(0, 3).join(', ') }}</span> }
        @case ('actions') {
          @if (row.deleted) { <button class="btn btn-sm btn-outline-success" (click)="$event.stopPropagation(); restore(row)">Restore</button> }
          @else if (college.canWrite()) {
            <div class="btn-group btn-group-sm" (click)="$event.stopPropagation()">
              <button class="btn btn-outline-secondary" (click)="openForm(row)" aria-label="Edit"><i class="bi bi-pencil"></i></button>
              <button class="btn btn-outline-danger" (click)="remove(row)" aria-label="Delete"><i class="bi bi-trash"></i></button>
            </div>
          }
        }
        @default { {{ row[col.key] ?? '-' }} }
      }
    </ng-template>

    <pm-student-form [open]="formOpen()" [student]="editing()" (closed)="formOpen.set(false)" (saved)="saved()" />
    <pm-student-drawer [studentId]="openId()" (closed)="openId.set(null)" (edit)="editFromDrawer()" (changed)="reload()" />`,
  styles: [`
    .pm-stu-bar { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-bottom: .75rem; }
    .pm-stu-search { flex: 1 1 220px; max-width: 360px; }
    .pm-stu-filters { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: .6rem; padding: .75rem; margin-bottom: .75rem;
      background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); }
  `],
})
export class Students implements OnInit {
  private students = inject(StudentService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private router = inject(Router);
  /** ?open=<id> from global search */
  open = input<string>();
  statuses: StudentStatus[] = ['ACTIVE', 'PLACED', 'DEBARRED', 'OPTED_OUT'];
  cols: Column[] = [
    { key: 'rollNumber', label: 'Roll no', sortable: true, hideSm: true },
    { key: 'name', label: 'Name' },
    { key: 'branchCode', label: 'Branch', hideSm: true },
    { key: 'batchYear', label: 'Batch', sortable: true, hideSm: true },
    { key: 'cgpa', label: 'CGPA', sortable: true },
    { key: 'activeBacklogs', label: 'Backlogs', sortable: true, hideSm: true },
    { key: 'skills', label: 'Skills', hideSm: true },
    { key: 'status', label: 'Status', sortable: true },
    { key: 'highestCtcLpa', label: 'Best CTC', sortable: true, hideSm: true },
    { key: 'actions', label: '', align: 'end' },
  ];
  f: StudentFilter = { q: '', batchYear: null, branchId: null, minCgpa: null, maxCgpa: null, maxBacklogs: null, status: null, placed: null, skill: '', deleted: false };
  rows = signal<Student[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('rollNumber');
  dir = signal<'asc' | 'desc'>('asc');
  loading = signal(false);
  error = signal(false);
  showFilters = signal(false);
  formOpen = signal(false);
  editing = signal<Student | null>(null);
  openId = signal<string | null>(null);
  search$ = new Subject<void>();

  constructor() {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.reload(0));
  }

  ngOnInit(): void {
    this.reload(0);
    if (this.open()) this.openId.set(this.open()!);
  }

  reload(page = this.page()): void {
    this.loading.set(true);
    this.error.set(false);
    this.students.list(this.f, page, this.size(), this.sort(), this.dir()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(p.page); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set(true); },
    });
  }

  clear(): void {
    this.f = { q: '', batchYear: null, branchId: null, minCgpa: null, maxCgpa: null, maxBacklogs: null, status: null, placed: null, skill: '', deleted: false };
    this.reload(0);
  }
  exp(fmt: 'xlsx' | 'csv' | 'pdf'): void { this.students.export(this.f, fmt).subscribe(); }
  openForm(s: Student | null): void { this.editing.set(s); this.formOpen.set(true); }
  editFromDrawer(): void {
    const s = this.rows().find(r => r.id === this.openId());
    if (s) { this.openForm(s); return; }
    this.students.get(this.openId()!).subscribe(d => this.openForm(d.student));
  }
  saved(): void {
    this.formOpen.set(false);
    this.toast.success(this.editing() ? 'Student updated' : 'Student added. A welcome e-mail has been sent.');
    const id = this.openId();
    this.reload();
    if (id) { this.openId.set(null); setTimeout(() => this.openId.set(id)); }
  }
  async remove(s: Student): Promise<void> {
    const r = await this.confirm.ask({ title: `Move ${s.fullName} to trash?`, message: 'You can restore the student from the Trash view.', confirmText: 'Move to trash', tone: 'danger' });
    if (r.ok) this.students.remove(s.id).subscribe(() => { this.toast.success('Moved to trash'); this.reload(); });
  }
  restore(s: Student): void { this.students.restore(s.id).subscribe(() => { this.toast.success('Student restored'); this.reload(); }); }
}
