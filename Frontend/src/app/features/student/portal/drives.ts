import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentPortalService } from '../services/student-portal.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { PortalDrive } from '../../../core/models/college.models';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { Modal } from '../../../shared/ui/modal';

type View = 'open' | 'all' | 'applied';

/** Campus drives published by the student's college, with live eligibility and one-click apply. */
@Component({
  selector: 'pm-student-drives',
  imports: [DatePipe, FormsModule, StatusBadge, EmptyState, Skeleton, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <div class="st-filters">
      <div class="btn-group btn-group-sm" role="group" aria-label="Filter drives">
        @for (v of views; track v.key) {
          <button type="button" class="btn" [class.btn-primary]="view() === v.key" [class.btn-outline-secondary]="view() !== v.key" (click)="view.set(v.key)">{{ v.label }}</button>
        }
      </div>
      <input type="search" class="form-control form-control-sm" placeholder="Search company or role…" aria-label="Search drives" [ngModel]="q()" (ngModelChange)="q.set($event)" />
    </div>
    @if (loading()) { <pm-skeleton [rows]="6" /> } @else {
      <div class="row g-3">
        @for (d of shown(); track d.id) {
          <div class="col-md-6 col-xl-4"><article class="st-card h-100 d-flex flex-column">
            <div class="d-flex justify-content-between gap-2"><div class="min-w-0"><h2 class="h6 st-h mb-0 text-truncate">{{ d.companyName }}</h2>
              <div class="small st-muted">{{ d.roleTitle }}</div></div>
              @if (d.tierName) { <span class="st-chip align-self-start">{{ d.tierName }}</span> }</div>
            <div class="small my-2"><i class="bi bi-cash"></i> {{ pay(d) }} · {{ d.driveType.replace('_', ' ') }} · {{ d.location ?? d.mode }}</div>
            @if (d.registrationDeadline) { <div class="small st-muted"><i class="bi bi-clock"></i> Apply by {{ d.registrationDeadline | date: 'medium' }}</div> }
            @if (!d.eligible) { <div class="small text-danger mt-2"><i class="bi bi-x-circle"></i> {{ d.reasons.join('; ') }}</div> }
            <div class="d-flex gap-2 mt-auto pt-3 align-items-center flex-wrap">
              <button class="btn btn-sm btn-outline-secondary" (click)="detail.set(d)">Details</button>
              @if (d.applicationStatus && d.applicationStatus !== 'WITHDRAWN') { <pm-status [value]="d.applicationStatus" /> }
              @if (d.canApply) { <button class="btn btn-sm btn-primary ms-auto" (click)="apply(d)">Apply</button> }
            </div>
          </article></div>
        } @empty { <div class="col-12"><pm-empty-state icon="bi-briefcase" title="No drives here" text="You'll get a notification when a drive you're eligible for is published." /></div> }
      </div>
    }
    <pm-modal [open]="!!detail()" [title]="(detail()?.companyName ?? '') + ' · ' + (detail()?.roleTitle ?? '')" size="lg" (closed)="detail.set(null)">
      @if (detail(); as d) {
        <p class="small st-pre">{{ d.jobDescription || 'No description provided.' }}</p>
        @if (d.skills.length) { <p>@for (s of d.skills; track s) { <span class="st-chip me-1 mb-1">{{ s }}</span> }</p> }
        <dl class="row small mb-0">
          <dt class="col-4">Package</dt><dd class="col-8">{{ pay(d) }}</dd>
          <dt class="col-4">Venue</dt><dd class="col-8">{{ d.mode }} · {{ d.venue ?? 'TBD' }}</dd>
          <dt class="col-4">Drive date</dt><dd class="col-8">{{ d.driveDate ? (d.driveDate | date: 'mediumDate') : 'TBD' }}</dd>
          <dt class="col-4">Bond</dt><dd class="col-8">{{ d.bondMonths ? d.bondMonths + ' months' : 'None' }}</dd>
          <dt class="col-4">Eligibility</dt><dd class="col-8">@if (d.eligible) { <span class="text-success">You are eligible</span> } @else { <span class="text-danger">{{ d.reasons.join('; ') }}</span> }</dd>
        </dl>
      }
      <div pmFooter>@if (detail()?.canApply) { <button class="btn btn-primary" (click)="apply(detail()!)">Apply now</button> }</div>
    </pm-modal>`,
})
export class StudentDrives implements OnInit {
  private portal = inject(StudentPortalService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  views: { key: View; label: string }[] = [{ key: 'open', label: 'Open for me' }, { key: 'applied', label: 'Applied' }, { key: 'all', label: 'All' }];
  view = signal<View>('open');
  q = signal('');
  loading = signal(true);
  drives = signal<PortalDrive[]>([]);
  detail = signal<PortalDrive | null>(null);
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.drives()
      .filter(d => this.view() === 'all' || (this.view() === 'open' ? d.canApply : !!d.applicationStatus && d.applicationStatus !== 'WITHDRAWN'))
      .filter(d => !q || d.companyName.toLowerCase().includes(q) || d.roleTitle.toLowerCase().includes(q));
  });

  ngOnInit(): void { this.load(); }
  load(): void {
    this.portal.drives().subscribe({
      next: d => {
        this.drives.set(d);
        // nothing open to apply to: show everything instead of an empty "Open for me" list
        if (this.loading() && !d.some(x => x.canApply)) this.view.set(d.some(x => x.applicationStatus) ? 'applied' : 'all');
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
  pay(d: PortalDrive): string { return d.ctcLpa ? `${d.ctcLpa} LPA` : d.stipendMonthly ? `₹${d.stipendMonthly}/month` : 'Not disclosed'; }
  async apply(d: PortalDrive): Promise<void> {
    const r = await this.confirm.ask({ title: `Apply to ${d.companyName}?`, message: `${d.roleTitle}${d.bondMonths ? ' · includes a ' + d.bondMonths + '-month bond' : ''}.` });
    if (!r.ok) return;
    this.portal.apply(d.id).subscribe(() => { this.toast.success('Application submitted'); this.detail.set(null); this.load(); });
  }
}
