import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentPortalService } from '../../../../services/student-portal.service';
import { ToastService } from '../../../../services/toast.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog';
import { AppError } from '../../../../core/api';
import { DocType, Offer, PortalApplication, PortalDrive, PortalProfile, StudentDocument } from '../../../../core/models/college.models';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../../shared/ui/states';
import { Modal } from '../../../../shared/ui/modal';
import { FileUpload } from '../../../../shared/ui/file-upload';

/** Student self-service: eligible campus drives, applications, offers and document vault. */
@Component({
  selector: 'pm-campus-drives',
  imports: [DatePipe, FormsModule, StatusBadge, EmptyState, Skeleton, Modal, FileUpload],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-cd p-3 p-md-4">
      @if (notLinked()) {
        <pm-empty-state icon="bi-mortarboard" title="Your account isn't linked to a college yet"
          text="Campus drives appear here once your placement cell adds you with this e-mail address." />
      } @else if (!profile()) { <pm-skeleton [rows]="6" /> } @else {
        <header class="mb-3"><h1 class="h4 fw-bold mb-1">Campus drives</h1>
          <p class="small text-body-secondary mb-0">{{ profile()!.collegeName }} · {{ profile()!.rollNumber }} · {{ profile()!.branch }} · Batch {{ profile()!.batchYear }}
            · CGPA {{ profile()!.cgpa ?? '-' }} · <pm-status [value]="profile()!.status" /></p></header>
        <ul class="nav nav-pills small mb-3" role="tablist">
          @for (t of tabs; track t) { <li class="nav-item"><button class="nav-link" role="tab" [class.active]="tab() === t" (click)="tab.set(t)">{{ t }}
            @if (t === 'Offers' && pendingOffers()) { <span class="badge text-bg-danger ms-1">{{ pendingOffers() }}</span> }</button></li> }
        </ul>
        @switch (tab()) {
          @case ('Drives') {
            <div class="row g-3">
              @for (d of drives(); track d.id) {
                <div class="col-md-6 col-xl-4"><article class="pm-cd-card h-100">
                  <div class="d-flex justify-content-between gap-2"><div class="min-w-0"><h2 class="h6 fw-bold mb-0 text-truncate">{{ d.companyName }}</h2>
                    <div class="small text-body-secondary">{{ d.roleTitle }}</div></div>
                    @if (d.tierName) { <span class="badge text-bg-light border align-self-start">{{ d.tierName }}</span> }</div>
                  <div class="small my-2"><i class="bi bi-cash"></i> {{ d.ctcLpa ? d.ctcLpa + ' LPA' : ('₹' + d.stipendMonthly + '/month') }}
                    · {{ d.driveType.replace('_', ' ') }} · {{ d.location ?? d.mode }}</div>
                  @if (d.registrationDeadline) { <div class="small text-body-secondary"><i class="bi bi-clock"></i> Apply by {{ d.registrationDeadline | date: 'medium' }}</div> }
                  @if (!d.eligible) { <div class="small text-danger mt-2"><i class="bi bi-x-circle"></i> {{ d.reasons.join('; ') }}</div> }
                  <div class="d-flex gap-2 mt-3 align-items-center flex-wrap">
                    <button class="btn btn-sm btn-outline-secondary" (click)="detail.set(d)">Details</button>
                    @if (d.applicationStatus && d.applicationStatus !== 'WITHDRAWN') { <pm-status [value]="d.applicationStatus" /> }
                    @if (d.canApply) { <button class="btn btn-sm btn-primary ms-auto" (click)="apply(d)">Apply</button> }
                  </div>
                </article></div>
              } @empty { <div class="col-12"><pm-empty-state icon="bi-briefcase" title="No open drives right now" text="You'll be notified when a drive you're eligible for is published." /></div> }
            </div>
          }
          @case ('My applications') {
            @for (a of apps(); track a.id) {
              <div class="pm-cd-row"><span class="flex-grow-1 small"><strong>{{ a.companyName }}</strong> · {{ a.driveTitle }}<br>
                <span class="text-body-secondary">Applied {{ a.appliedAt | date: 'mediumDate' }} @if (a.currentRound) { · in round {{ a.currentRound }} }</span></span>
                <pm-status [value]="a.status" />
                @if (a.status === 'APPLIED' || a.status === 'SHORTLISTED' || a.status === 'WAITLISTED') { <button class="btn btn-sm btn-outline-danger" (click)="withdraw(a)">Withdraw</button> }
              </div>
            } @empty { <pm-empty-state icon="bi-list-check" title="No applications yet" /> }
          }
          @case ('Offers') {
            @for (o of offers(); track o.id) {
              <div class="pm-cd-row"><span class="flex-grow-1 small"><strong>{{ o.companyName }}</strong> · {{ o.roleTitle }} · {{ o.ctcLpa }} LPA<br>
                <span class="text-body-secondary">Offered {{ o.offerDate | date: 'mediumDate' }} @if (o.joiningDate) { · joining {{ o.joiningDate | date: 'mediumDate' }} }</span></span>
                <pm-status [value]="o.status" />
                @if (o.status === 'OFFERED') {
                  <button class="btn btn-sm btn-success" (click)="respond(o, true)">Accept</button>
                  <button class="btn btn-sm btn-outline-secondary" (click)="respond(o, false)">Decline</button>
                }
              </div>
            } @empty { <pm-empty-state icon="bi-award" title="No offers yet" text="Keep going - your offers will show up here." /> }
          }
          @case ('Documents') {
            <div class="row g-3"><div class="col-lg-7">
              @for (d of docs(); track d.id) {
                <div class="pm-cd-row small"><span class="flex-grow-1">{{ d.docType }} · {{ d.fileName }}@if (d.remarks) { <br><span class="text-body-secondary">{{ d.remarks }}</span> }</span>
                  <pm-status [value]="d.verificationStatus" /></div>
              } @empty { <pm-empty-state icon="bi-folder2-open" title="No documents uploaded" /> }</div>
              <div class="col-lg-5"><div class="pm-cd-card"><label class="form-label small fw-semibold" for="cd-type">Document type</label>
                <select id="cd-type" class="form-select form-select-sm mb-2" [(ngModel)]="docType">
                  @for (t of docTypes; track t) { <option [value]="t">{{ t.replace('_', ' ') }}</option> }</select>
                <pm-file-upload accept=".pdf,.png,.jpg,.jpeg" [maxMb]="10" (fileSelected)="upload($event)" />
                <p class="small text-body-secondary mt-2 mb-0">Your placement cell verifies uploaded documents.</p></div></div>
            </div>
          }
        }
      }
    </div>
    <pm-modal [open]="!!detail()" [title]="(detail()?.companyName ?? '') + ' · ' + (detail()?.roleTitle ?? '')" size="lg" (closed)="detail.set(null)">
      @if (detail(); as d) {
        <p class="small pm-cd-pre">{{ d.jobDescription || 'No description provided.' }}</p>
        @if (d.skills.length) { <p>@for (s of d.skills; track s) { <span class="badge rounded-pill text-bg-secondary me-1">{{ s }}</span> }</p> }
        <dl class="row small mb-0"><dt class="col-4">Venue</dt><dd class="col-8">{{ d.mode }} · {{ d.venue ?? 'TBD' }}</dd>
          <dt class="col-4">Drive date</dt><dd class="col-8">{{ d.driveDate ?? 'TBD' }}</dd>
          <dt class="col-4">Bond</dt><dd class="col-8">{{ d.bondMonths ? d.bondMonths + ' months' : 'None' }}</dd></dl>
      }
      <div pmFooter>@if (detail()?.canApply) { <button class="btn btn-primary" (click)="apply(detail()!)">Apply now</button> }</div>
    </pm-modal>`,
  styles: [`
    .pm-cd-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }
    .pm-cd-row { display: flex; gap: .75rem; align-items: center; flex-wrap: wrap; background: #fff; border: 1px solid #e2e8f0;
      border-radius: 14px; padding: 1rem 1.25rem; margin-bottom: .75rem; box-shadow: 0 4px 16px rgba(0,0,0,.02); }
    .pm-cd-card h2 { color: #0f172a; font-weight: 800; }
    .btn { border-radius: 50px; font-weight: 600; }
    .btn-primary { background: #16122b; border-color: #16122b; } .btn-primary:hover { background: #262045; border-color: #262045; }
    .pm-cd-pre { white-space: pre-wrap; }
  `],
})
export class CampusDrives implements OnInit {
  private svc = inject(StudentPortalService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  tabs = ['Drives', 'My applications', 'Offers', 'Documents'];
  tab = signal('Drives');
  profile = signal<PortalProfile | null>(null);
  notLinked = signal(false);
  drives = signal<PortalDrive[]>([]);
  apps = signal<PortalApplication[]>([]);
  offers = signal<Offer[]>([]);
  docs = signal<StudentDocument[]>([]);
  detail = signal<PortalDrive | null>(null);
  pendingOffers = signal(0);
  docTypes: DocType[] = ['RESUME', 'MARKSHEET_10', 'MARKSHEET_12', 'MARKSHEET_SEM', 'ID_PROOF', 'OTHER'];
  docType: DocType = 'RESUME';

  ngOnInit(): void {
    this.svc.profile().subscribe({
      next: p => { this.profile.set(p); this.loadAll(); },
      error: (e: AppError) => { if (e.code === 'NOT_A_COLLEGE_STUDENT') this.notLinked.set(true); },
    });
  }
  loadAll(): void {
    this.svc.drives().subscribe(d => this.drives.set(d));
    this.svc.applications().subscribe(a => this.apps.set(a));
    this.svc.offers().subscribe(o => { this.offers.set(o); this.pendingOffers.set(o.filter(x => x.status === 'OFFERED').length); });
    this.svc.documents().subscribe(d => this.docs.set(d));
  }
  async apply(d: PortalDrive): Promise<void> {
    const r = await this.confirm.ask({ title: `Apply to ${d.companyName}?`, message: `${d.roleTitle}${d.bondMonths ? ' · includes a ' + d.bondMonths + '-month bond' : ''}.` });
    if (!r.ok) return;
    this.svc.apply(d.id).subscribe(() => { this.toast.success('Application submitted'); this.detail.set(null); this.loadAll(); });
  }
  async withdraw(a: PortalApplication): Promise<void> {
    const r = await this.confirm.ask({ title: 'Withdraw application?', message: `${a.companyName} · ${a.driveTitle}`, tone: 'danger', confirmText: 'Withdraw' });
    if (r.ok) this.svc.withdraw(a.id).subscribe(() => { this.toast.success('Application withdrawn'); this.loadAll(); });
  }
  async respond(o: Offer, accept: boolean): Promise<void> {
    const r = await this.confirm.ask({ title: `${accept ? 'Accept' : 'Decline'} offer from ${o.companyName}?`,
      message: accept ? 'Depending on your college policy, accepting may limit further applications.' : 'This cannot be undone.', tone: accept ? 'primary' : 'danger' });
    if (r.ok) this.svc.respond(o.id, accept).subscribe(() => { this.toast.success(accept ? 'Offer accepted. Congratulations!' : 'Offer declined'); this.loadAll(); this.ngOnInit(); });
  }
  upload(f: File): void { this.svc.upload(this.docType, f).subscribe(() => { this.toast.success('Uploaded for verification'); this.svc.documents().subscribe(d => this.docs.set(d)); }); }
}
