import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CompanyService } from '../services/company.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { AppError } from '../../../core/api';
import { Company, CompanyDetail, MouStatus, RecruiterOption } from '../../../core/models/college.models';
import { Column, DataTable } from '../../../shared/ui/data-table';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { Skeleton } from '../../../shared/ui/states';

@Component({
  selector: 'pm-companies',
  imports: [FormsModule, ReactiveFormsModule, DatePipe, RouterLink, DataTable, Modal, StatusBadge, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-co-bar">
      <input type="search" class="form-control form-control-sm pm-co-q" placeholder="Search companies…" aria-label="Search companies"
             [(ngModel)]="q" (ngModelChange)="search$.next()" />
      <select class="form-select form-select-sm w-auto" aria-label="MoU status" [(ngModel)]="mou" (change)="reload(0)">
        <option [ngValue]="null">All MoU</option>@for (m of mous; track m) { <option [ngValue]="m">{{ m }}</option> }</select>
      <div class="form-check form-switch"><input id="co-trash" class="form-check-input" type="checkbox" [(ngModel)]="deleted" (change)="reload(0)" />
        <label for="co-trash" class="form-check-label small">Trash</label></div>
      <div class="ms-auto d-flex gap-2">
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-secondary" (click)="svc.export('xlsx', { q, mouStatus: mou }).subscribe()">Excel</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('csv', { q, mouStatus: mou }).subscribe()">CSV</button>
          <button class="btn btn-outline-secondary" (click)="svc.export('pdf', { q, mouStatus: mou }).subscribe()">PDF</button>
        </div>
        @if (college.canWrite()) { <button class="btn btn-sm btn-primary" (click)="openForm(null)"><i class="bi bi-plus-lg"></i> Add company</button> }
      </div>
    </div>
    <pm-data-table [columns]="cols" [rows]="rows()" [total]="total()" [page]="page()" [size]="size()" [sort]="sort()" [dir]="dir()" [loading]="loading()"
      [clickable]="true" [cell]="cellTpl" (rowClick)="show($event.id)" (pageChange)="reload($event)" (sizeChange)="size.set($event); reload(0)"
      (sortChange)="sort.set($event.sort); dir.set($event.dir); reload(0)" emptyIcon="bi-building" emptyTitle="No companies"
      emptyText="Add recruiters that visit your campus to start creating drives." />
    <ng-template #cellTpl let-row let-col="col">
      @switch (col.key) {
        @case ('name') { <div class="fw-semibold">{{ row.name }}</div><div class="small text-body-secondary">{{ row.industry ?? '' }}</div> }
        @case ('mouStatus') { <pm-status [value]="row.mouStatus" /> }
        @case ('avgRating') { @if (row.avgRating) { <span class="text-warning"><i class="bi bi-star-fill"></i></span> {{ row.avgRating }} } @else { - } }
        @case ('contact') { <span class="small">{{ row.primaryContactName ?? '-' }}</span> }
        @case ('actions') {
          @if (row.deleted) { <button class="btn btn-sm btn-outline-success" (click)="$event.stopPropagation(); restore(row)">Restore</button> }
          @else if (college.canWrite()) {
            <div class="btn-group btn-group-sm" (click)="$event.stopPropagation()">
              <button class="btn btn-outline-secondary" (click)="openForm(row)" aria-label="Edit"><i class="bi bi-pencil"></i></button>
              <button class="btn btn-outline-danger" (click)="remove(row)" aria-label="Delete"><i class="bi bi-trash"></i></button></div>
          }
        }
        @default { {{ row[col.key] ?? '-' }} }
      }
    </ng-template>

    <!-- add / edit -->
    <pm-modal [open]="formOpen()" [title]="editing() ? 'Edit company' : 'Add company'" (closed)="formOpen.set(false)">
      <form [formGroup]="form" id="co-form" (ngSubmit)="save()" class="row g-3" novalidate>
        <div class="col-12"><label class="form-label small fw-semibold" for="co-name">Name *</label>
          <input id="co-name" class="form-control" formControlName="name" [class.is-invalid]="form.controls.name.touched && form.controls.name.invalid" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="co-ind">Industry</label><input id="co-ind" class="form-control" formControlName="industry" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="co-loc">HQ location</label><input id="co-loc" class="form-control" formControlName="hqLocation" /></div>
        <div class="col-12"><label class="form-label small fw-semibold" for="co-web">Website</label><input id="co-web" class="form-control" formControlName="website" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="co-mou">MoU status</label>
          <select id="co-mou" class="form-select" formControlName="mouStatus">@for (m of mous; track m) { <option [value]="m">{{ m }}</option> }</select></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="co-exp">MoU expiry</label><input id="co-exp" type="date" class="form-control" formControlName="mouExpiry" /></div>
        <div class="col-12"><label class="form-label small fw-semibold" for="co-rec">Link PROVEYU recruiter</label>
          @if (form.value.linkedRecruiterId) {
            <div class="d-flex align-items-center gap-2"><span class="badge text-bg-primary">{{ linkedName() }}</span>
              <button type="button" class="btn btn-sm btn-link" (click)="form.patchValue({ linkedRecruiterId: null }); linkedName.set('')">Remove</button></div>
          } @else {
            <input id="co-rec" class="form-control" placeholder="Search recruiter by name or e-mail" (input)="rec$.next($any($event.target).value)" />
            @for (r of recruiters(); track r.userId) {
              <button type="button" class="list-group-item list-group-item-action small py-1 px-2 border" (click)="pickRecruiter(r)">{{ r.fullName }} · {{ r.email }}</button>
            }
          }
        </div>
        <div class="col-12"><label class="form-label small fw-semibold" for="co-notes">Notes</label><textarea id="co-notes" rows="2" class="form-control" formControlName="notes"></textarea></div>
      </form>
      <div pmFooter><button class="btn btn-outline-secondary" (click)="formOpen.set(false)">Cancel</button>
        <button type="submit" form="co-form" class="btn btn-primary" [disabled]="busy()">Save</button></div>
    </pm-modal>

    <!-- detail drawer -->
    <pm-modal [open]="!!detailId()" variant="drawer" [title]="detail()?.company?.name ?? 'Company'" (closed)="detailId.set(null)">
      @if (!detail()) { <pm-skeleton [rows]="8" /> } @else {
        @let d = detail()!;
        <div class="d-flex flex-wrap gap-2 align-items-center mb-3"><pm-status [value]="d.company.mouStatus" />
          <span class="small text-body-secondary">{{ d.company.industry }} · {{ d.company.hqLocation }}</span>
          @if (d.company.website) { <a [href]="d.company.website" target="_blank" rel="noopener" class="small">Website</a> }
          @if (d.company.linkedRecruiterName) { <span class="badge text-bg-light border"><i class="bi bi-link-45deg"></i> {{ d.company.linkedRecruiterName }}</span> }
        </div>
        @if (college.canWrite()) {
          <div class="d-flex gap-2 mb-3 flex-wrap">
            <a class="btn btn-sm btn-primary" routerLink="/college/drives/new" [queryParams]="{ companyId: d.company.id }"><i class="bi bi-plus"></i> New drive</a>
            <button class="btn btn-sm btn-outline-primary" (click)="invite(d)"><i class="bi bi-envelope"></i> Invite to PROVEYU</button>
          </div>
        }
        <h3 class="h6 mt-2">Contact persons</h3>
        @for (c of d.contacts; track c.id) {
          <div class="d-flex justify-content-between small border-bottom py-2">
            <span><strong>{{ c.name }}</strong> @if (c.primaryContact) { <span class="badge text-bg-success">Primary</span> }<br>
              <span class="text-body-secondary">{{ c.designation }} · {{ c.email }} · {{ c.phone }}</span></span>
            @if (college.canWrite()) { <button class="btn btn-sm btn-link text-danger" (click)="deleteContact(c.id)" aria-label="Remove contact"><i class="bi bi-x-lg"></i></button> }
          </div>
        } @empty { <p class="small text-body-secondary">No contacts yet.</p> }
        @if (college.canWrite()) {
          <form class="row g-2 mt-1" (ngSubmit)="addContact()">
            <div class="col-6"><input class="form-control form-control-sm" placeholder="Name *" aria-label="Contact name" [(ngModel)]="contact.name" name="cn" required /></div>
            <div class="col-6"><input class="form-control form-control-sm" placeholder="Designation" aria-label="Designation" [(ngModel)]="contact.designation" name="cd" /></div>
            <div class="col-6"><input type="email" class="form-control form-control-sm" placeholder="E-mail" aria-label="E-mail" [(ngModel)]="contact.email" name="ce" /></div>
            <div class="col-6"><input class="form-control form-control-sm" placeholder="Phone" aria-label="Phone" [(ngModel)]="contact.phone" name="cp" /></div>
            <div class="col-8 form-check ms-2"><input id="cprim" type="checkbox" class="form-check-input" [(ngModel)]="contact.primaryContact" name="cprim" />
              <label for="cprim" class="form-check-label small">Primary contact</label></div>
            <div class="col text-end"><button class="btn btn-sm btn-outline-primary" [disabled]="!contact.name">Add</button></div>
          </form>
        }
        <h3 class="h6 mt-4">Visit history</h3>
        @for (v of d.visits; track v.driveId) {
          <a class="d-flex justify-content-between small border-bottom py-2 text-decoration-none" [routerLink]="['/college/drives', v.driveId]">
            <span>{{ v.title }} · {{ v.roleTitle }}<br><span class="text-body-secondary">{{ v.driveDate ?? 'date TBD' }} · {{ v.ctcLpa ?? '-' }} LPA · {{ v.applicants }} applicants · {{ v.offers }} offers</span></span>
            <pm-status [value]="v.status" /></a>
        } @empty { <p class="small text-body-secondary">No drives yet.</p> }
        <h3 class="h6 mt-4">Feedback & rating</h3>
        @for (f of d.feedback; track f.id) {
          <div class="small border-bottom py-2"><span class="text-warning">@for (s of [1,2,3,4,5]; track s) { <i class="bi" [class.bi-star-fill]="s <= f.rating" [class.bi-star]="s > f.rating"></i> }</span>
            {{ f.comment }}<br><span class="text-body-secondary">{{ f.createdByName }} · {{ f.createdAt | date: 'mediumDate' }} @if (f.driveTitle) { · {{ f.driveTitle }} }</span></div>
        } @empty { <p class="small text-body-secondary">No feedback yet.</p> }
        @if (college.canWrite()) {
          <div class="mt-2">
            <div class="mb-1" role="radiogroup" aria-label="Rating">@for (s of [1,2,3,4,5]; track s) {
              <button type="button" class="btn btn-sm btn-link text-warning p-0 me-1" role="radio" [attr.aria-checked]="rating === s" [attr.aria-label]="s + ' stars'" (click)="rating = s">
                <i class="bi" [class.bi-star-fill]="s <= rating" [class.bi-star]="s > rating"></i></button> }</div>
            <textarea class="form-control form-control-sm mb-2" rows="2" placeholder="How did the drive go?" aria-label="Feedback comment" [(ngModel)]="comment"></textarea>
            <button class="btn btn-sm btn-outline-primary" [disabled]="!rating" (click)="feedback()">Save feedback</button>
          </div>
        }
      }
    </pm-modal>`,
  styles: [`.pm-co-bar { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; margin-bottom: .75rem; } .pm-co-q { flex: 1 1 200px; max-width: 320px; }`],
})
export class Companies implements OnInit {
  svc = inject(CompanyService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  open = input<string>();
  mous: MouStatus[] = ['NONE', 'IN_PROGRESS', 'SIGNED', 'EXPIRED'];
  cols: Column[] = [
    { key: 'name', label: 'Company', sortable: true }, { key: 'mouStatus', label: 'MoU', sortable: true },
    { key: 'drivesCount', label: 'Drives', align: 'center', hideSm: true }, { key: 'offersCount', label: 'Offers', align: 'center', hideSm: true },
    { key: 'avgRating', label: 'Rating', hideSm: true }, { key: 'contact', label: 'Primary contact', hideSm: true }, { key: 'actions', label: '', align: 'end' },
  ];
  q = ''; mou: MouStatus | null = null; deleted = false;
  rows = signal<Company[]>([]); total = signal(0); page = signal(0); size = signal(20); sort = signal('name'); dir = signal<'asc' | 'desc'>('asc');
  loading = signal(false); busy = signal(false);
  formOpen = signal(false); editing = signal<Company | null>(null);
  detailId = signal<string | null>(null); detail = signal<CompanyDetail | null>(null);
  recruiters = signal<RecruiterOption[]>([]); linkedName = signal('');
  contact = { name: '', designation: '', email: '', phone: '', primaryContact: false };
  rating = 0; comment = '';
  search$ = new Subject<void>();
  rec$ = new Subject<string>();
  form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(200)]], industry: [''], website: [''], hqLocation: [''],
    mouStatus: ['NONE' as MouStatus], mouExpiry: [''], linkedRecruiterId: [null as string | null], notes: [''],
  });

  constructor() {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.reload(0));
    this.rec$.pipe(debounceTime(300), switchMap(q => q.trim().length < 2 ? of([]) : this.svc.searchRecruiters(q)), takeUntilDestroyed())
      .subscribe(r => this.recruiters.set(r));
  }

  ngOnInit(): void { this.reload(0); if (this.open()) this.show(this.open()!); }

  reload(page = this.page()): void {
    this.loading.set(true);
    this.svc.list({ q: this.q, mouStatus: this.mou, deleted: this.deleted }, page, this.size(), this.sort(), this.dir()).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.page.set(p.page); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
  show(id: string): void { this.detailId.set(id); this.detail.set(null); this.svc.get(id).subscribe(d => this.detail.set(d)); }
  openForm(c: Company | null): void {
    this.editing.set(c);
    this.recruiters.set([]);
    this.linkedName.set(c?.linkedRecruiterName ?? '');
    this.form.reset(c ? { ...c, industry: c.industry ?? '', website: c.website ?? '', hqLocation: c.hqLocation ?? '', mouExpiry: c.mouExpiry ?? '',
      linkedRecruiterId: c.linkedRecruiterId ?? null, notes: c.notes ?? '' } : { mouStatus: 'NONE', linkedRecruiterId: null });
    this.formOpen.set(true);
  }
  pickRecruiter(r: RecruiterOption): void { this.form.patchValue({ linkedRecruiterId: r.userId }); this.linkedName.set(r.fullName); this.recruiters.set([]); }
  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const body = { ...v, name: v.name!, mouExpiry: v.mouExpiry || null, industry: v.industry || null, website: v.website || null,
      hqLocation: v.hqLocation || null, notes: v.notes || null };
    this.busy.set(true);
    const c = this.editing();
    (c ? this.svc.update(c.id, body) : this.svc.create(body)).subscribe({
      next: () => { this.busy.set(false); this.formOpen.set(false); this.toast.success('Company saved'); this.reload(); if (this.detailId()) this.show(this.detailId()!); },
      error: (_: AppError) => this.busy.set(false),
    });
  }
  async remove(c: Company): Promise<void> {
    const r = await this.confirm.ask({ title: `Move ${c.name} to trash?`, message: 'Existing drives and offers are kept.', tone: 'danger', confirmText: 'Move to trash' });
    if (r.ok) this.svc.remove(c.id).subscribe(() => { this.toast.success('Moved to trash'); this.reload(); });
  }
  restore(c: Company): void { this.svc.restore(c.id).subscribe(() => { this.toast.success('Restored'); this.reload(); }); }
  addContact(): void {
    this.svc.addContact(this.detailId()!, this.contact).subscribe(() => {
      this.contact = { name: '', designation: '', email: '', phone: '', primaryContact: false };
      this.show(this.detailId()!); this.reload();
    });
  }
  deleteContact(id: string): void { this.svc.deleteContact(id).subscribe(() => this.show(this.detailId()!)); }
  feedback(): void {
    this.svc.addFeedback(this.detailId()!, { rating: this.rating, comment: this.comment }).subscribe(() => {
      this.rating = 0; this.comment = ''; this.toast.success('Feedback saved'); this.show(this.detailId()!); this.reload();
    });
  }
  async invite(d: CompanyDetail): Promise<void> {
    const r = await this.confirm.ask({ title: `Invite ${d.company.name}`, message: 'Send a campus-recruitment invitation e-mail with a PROVEYU sign-up link.',
      inputLabel: 'Recipient e-mail', inputRequired: true, confirmText: 'Send invite' });
    if (r.ok) this.svc.invite(d.company.id, r.value).subscribe(() => { this.toast.success('Invitation sent'); this.show(d.company.id); });
  }
}
