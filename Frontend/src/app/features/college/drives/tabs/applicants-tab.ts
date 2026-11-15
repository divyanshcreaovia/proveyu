import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApplicationService } from '../../../../services/application.service';
import { CollegeService } from '../../../../services/college.service';
import { ToastService } from '../../../../services/toast.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog';
import { Application, ApplicationStatus, Drive } from '../../../../core/models/college.models';
import { Column, DataTable } from '../../../../shared/ui/data-table';
import { StatusBadge } from '../../../../shared/ui/status-badge';

@Component({
  selector: 'pm-applicants-tab',
  imports: [FormsModule, DatePipe, DataTable, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
      <select class="form-select form-select-sm w-auto" aria-label="Status" [(ngModel)]="status" (change)="load(0)">
        <option [ngValue]="null">All ({{ total() }})</option>@for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }</select>
      <input type="search" class="form-control form-control-sm w-auto" placeholder="Search…" aria-label="Search applicants" [(ngModel)]="q" (change)="load(0)" />
      <div class="btn-group btn-group-sm"><button class="btn btn-outline-secondary" (click)="svc.export(drive().id, 'xlsx').subscribe()">Excel</button>
        <button class="btn btn-outline-secondary" (click)="svc.export(drive().id, 'pdf').subscribe()">PDF</button></div>
      @if (college.canWrite()) {
        <div class="ms-auto d-flex flex-wrap gap-2">
          <span class="small text-body-secondary align-self-center">{{ selected().length }} selected</span>
          <button class="btn btn-sm btn-outline-primary" [disabled]="!selected().length" (click)="bulk('SHORTLISTED')">Shortlist</button>
          <button class="btn btn-sm btn-outline-warning" [disabled]="!selected().length" (click)="bulk('WAITLISTED')">Waitlist</button>
          <button class="btn btn-sm btn-outline-danger" [disabled]="!selected().length" (click)="bulk('REJECTED')">Reject</button>
        </div>
      }
    </div>
    <div class="alert alert-light border small py-2"><i class="bi bi-info-circle"></i> Students apply from their portal (Campus Drives) or you can register them from the
      <strong>Eligibility</strong> tab. Shortlisted applicants enter round 1 when you start it.</div>
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [loading]="loading()" [selectable]="college.canWrite()"
      [(selected)]="selected" [cell]="cellTpl" (pageChange)="load($event)" (sizeChange)="size.set($event); load(0)"
      emptyTitle="No applicants yet" emptyText="Publish the drive so eligible students can apply, or register students from the Eligibility tab." emptyIcon="bi-person-lines-fill" />
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('student') { <div class="fw-semibold">{{ row.studentName }}</div><div class="small text-body-secondary">{{ row.rollNumber }} · {{ row.email }}</div> }
        @case ('status') { <pm-status [value]="row.status" /> @if (row.currentRound) { <small class="text-body-secondary ms-1">R{{ row.currentRound }}</small> } }
        @case ('source') { <span class="small">{{ row.source }}</span> }
        @case ('appliedAt') { <span class="small">{{ row.appliedAt | date: 'mediumDate' }}</span> }
        @default { {{ row[col.key] ?? '-' }} }
      }
    </ng-template>`,
})
export class ApplicantsTab implements OnInit {
  svc = inject(ApplicationService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  drive = input.required<Drive>();
  changed = output<void>();
  statuses: ApplicationStatus[] = ['APPLIED', 'SHORTLISTED', 'WAITLISTED', 'IN_PROCESS', 'SELECTED', 'REJECTED', 'WITHDRAWN'];
  cols: Column[] = [{ key: 'student', label: 'Student' }, { key: 'branchCode', label: 'Branch', hideSm: true }, { key: 'cgpa', label: 'CGPA', hideSm: true },
    { key: 'status', label: 'Status' }, { key: 'source', label: 'Source', hideSm: true }, { key: 'appliedAt', label: 'Applied', hideSm: true }];
  status: ApplicationStatus | null = null; q = '';
  rows = signal<Application[]>([]); total = signal(0); page = signal(0); size = signal(50); loading = signal(false);
  selected = signal<string[]>([]);

  ngOnInit(): void { this.load(0); }
  load(page = this.page()): void {
    this.loading.set(true);
    this.svc.list(this.drive().id, this.status, this.q, page, this.size()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(page); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
  async bulk(status: ApplicationStatus): Promise<void> {
    const r = await this.confirm.ask({ title: `${status.toLowerCase()} ${this.selected().length} applicant(s)?`, message: 'Students are notified in-app (and by e-mail when shortlisted).',
      inputLabel: 'Remarks (optional)', tone: status === 'REJECTED' ? 'danger' : 'primary' });
    if (!r.ok) return;
    this.svc.bulkStatus(this.selected(), status, r.value || undefined).subscribe(res => {
      this.toast.success(`${res.updated} updated${res.skipped.length ? `, ${res.skipped.length} skipped` : ''}`);
      this.selected.set([]); this.load(); this.changed.emit();
    });
  }
}
