import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OfferService, OfferFilter } from '../../../services/offer.service';
import { CollegeService } from '../../../services/college.service';
import { Offer, OfferStatus } from '../../../core/models/college.models';
import { Column, DataTable } from '../../../shared/ui/data-table';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { OfferForm } from './offer-form';
import { OfferActions } from './offer-actions';

@Component({
  selector: 'pm-offers',
  imports: [FormsModule, DatePipe, RouterLink, DataTable, StatusBadge, OfferForm, OfferActions],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
      <input type="search" class="form-control form-control-sm w-auto" placeholder="Student or company…" aria-label="Search offers" [(ngModel)]="f.q" (change)="load(0)" />
      <select class="form-select form-select-sm w-auto" aria-label="Status" [(ngModel)]="f.status" (change)="load(0)">
        <option [ngValue]="null">All statuses</option>@for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }</select>
      <select class="form-select form-select-sm w-auto" aria-label="Batch" [(ngModel)]="f.batchYear" (change)="load(0)">
        <option [ngValue]="null">All batches</option>@for (b of college.batches(); track b.id) { <option [ngValue]="b.gradYear">{{ b.gradYear }}</option> }</select>
      <div class="ms-auto d-flex gap-2">
        <div class="btn-group btn-group-sm"><button class="btn btn-outline-secondary" (click)="svc.export('xlsx', f).subscribe()">Excel</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('csv', f).subscribe()">CSV</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('pdf', f).subscribe()">PDF</button></div>
        @if (college.canWrite()) { <button class="btn btn-sm btn-primary" (click)="formOpen.set(true)"><i class="bi bi-plus-lg"></i> Off-campus / manual offer</button> }
      </div>
    </div>
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [loading]="loading()" [cell]="cellTpl"
      (pageChange)="load($event)" (sizeChange)="size.set($event); load(0)" emptyIcon="bi-award" emptyTitle="No offers yet" emptyText="Offers recorded in drives appear here." />
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('student') { <div class="fw-semibold">{{ row.studentName }}</div><div class="small text-body-secondary">{{ row.rollNumber }} · {{ row.branchCode }} · {{ row.batchYear }}</div> }
        @case ('company') { <div>{{ row.companyName }}</div><div class="small text-body-secondary">@if (row.driveId) { <a [routerLink]="['/college/drives', row.driveId]">{{ row.driveTitle }}</a> } @else { Off-campus } · {{ row.roleTitle }}</div> }
        @case ('ctc') { {{ row.ctcLpa }} LPA @if (row.tierName) { <span class="badge text-bg-light border">{{ row.tierName }}</span> } }
        @case ('dates') { <span class="small">{{ row.offerDate | date: 'mediumDate' }}@if (row.joiningDate) { → {{ row.joiningDate | date: 'mediumDate' }} }</span> }
        @case ('status') { <pm-status [value]="row.status" /> }
        @case ('actions') { <pm-offer-actions [offer]="row" (changed)="load()" /> }
      }
    </ng-template>
    <pm-offer-form [open]="formOpen()" (closed)="formOpen.set(false)" (saved)="formOpen.set(false); load(0)" />`,
})
export class Offers implements OnInit {
  svc = inject(OfferService);
  college = inject(CollegeService);
  statuses: OfferStatus[] = ['OFFERED', 'ACCEPTED', 'DECLINED', 'JOINED', 'REVOKED'];
  cols: Column[] = [{ key: 'student', label: 'Student' }, { key: 'company', label: 'Company / role' }, { key: 'ctc', label: 'CTC' },
    { key: 'dates', label: 'Offer → joining', hideSm: true }, { key: 'status', label: 'Status' }, { key: 'actions', label: '', align: 'end' }];
  f: OfferFilter = { q: '', status: null, batchYear: null };
  rows = signal<Offer[]>([]); total = signal(0); page = signal(0); size = signal(20); loading = signal(false);
  formOpen = signal(false);

  ngOnInit(): void { this.load(0); }
  load(page = this.page()): void {
    this.loading.set(true);
    this.svc.list(this.f, page, this.size()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(page); this.loading.set(false); }, error: () => this.loading.set(false),
    });
  }
}
