import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditLogService } from '../../../services/audit-log.service';
import { AuditEntry } from '../../../core/models/college.models';
import { Column, DataTable } from '../../../shared/ui/data-table';

@Component({
  selector: 'pm-audit-log',
  imports: [FormsModule, DatePipe, DataTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap gap-2 mb-3">
      <input type="search" class="form-control form-control-sm w-auto" placeholder="Search summary / actor" aria-label="Search" [(ngModel)]="f.q" (change)="load(0)" />
      <select class="form-select form-select-sm w-auto" aria-label="Entity" [(ngModel)]="f.entityType" (change)="load(0)">
        <option value="">All entities</option>@for (e of entities; track e) { <option [value]="e">{{ e }}</option> }</select>
      <input class="form-control form-control-sm w-auto" placeholder="Action e.g. OFFER_CREATED" aria-label="Action" [(ngModel)]="f.action" (change)="load(0)" />
      <label class="small align-self-center" for="au-from">From</label><input id="au-from" type="date" class="form-control form-control-sm w-auto" [(ngModel)]="from" (change)="load(0)" />
      <label class="small align-self-center" for="au-to">To</label><input id="au-to" type="date" class="form-control form-control-sm w-auto" [(ngModel)]="to" (change)="load(0)" />
    </div>
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [loading]="loading()" [cell]="cellTpl"
      (pageChange)="load($event)" (sizeChange)="size.set($event); load(0)" emptyIcon="bi-journal-text" emptyTitle="No audit entries" />
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('createdAt') { <span class="small text-nowrap">{{ row.createdAt | date: 'medium' }}</span> }
        @case ('action') { <code class="small">{{ row.action }}</code> }
        @case ('summary') { <div class="small">{{ row.summary }}</div>@if (row.details) { <div class="small text-body-secondary pm-au-pre">{{ row.details }}</div> } }
        @default { <span class="small">{{ row[col.key] ?? '-' }}</span> }
      }
    </ng-template>`,
  styles: [`.pm-au-pre { white-space: pre-wrap; max-width: 520px; }`],
})
export class AuditLog implements OnInit {
  private svc = inject(AuditLogService);
  entities = ['STUDENT', 'IMPORT', 'COMPANY', 'DRIVE', 'APPLICATION', 'OFFER', 'MEMBER', 'COLLEGE', 'BRANCH', 'BATCH', 'ANNOUNCEMENT', 'REPORT'];
  cols: Column[] = [{ key: 'createdAt', label: 'When' }, { key: 'actorName', label: 'Who' }, { key: 'action', label: 'Action', hideSm: true },
    { key: 'entityType', label: 'Entity', hideSm: true }, { key: 'summary', label: 'What' }];
  f = { q: '', entityType: '', action: '' };
  from = ''; to = '';
  rows = signal<AuditEntry[]>([]); total = signal(0); page = signal(0); size = signal(50); loading = signal(false);

  ngOnInit(): void { this.load(0); }
  load(page = this.page()): void {
    this.loading.set(true);
    const from = this.from ? new Date(this.from).toISOString() : undefined;
    const to = this.to ? new Date(new Date(this.to).getTime() + 86400000).toISOString() : undefined;
    this.svc.search({ ...this.f, from, to }, page, this.size()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(page); this.loading.set(false); }, error: () => this.loading.set(false),
    });
  }
}
