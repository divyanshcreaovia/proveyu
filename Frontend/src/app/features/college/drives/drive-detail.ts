import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { PlacementDriveService } from '../../../services/placement-drive.service';
import { CollegeService } from '../../../services/college.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Drive, DriveStatus } from '../../../core/models/college.models';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { Skeleton, ErrorState } from '../../../shared/ui/states';
import { EligibilityTab } from './tabs/eligibility-tab';
import { ApplicantsTab } from './tabs/applicants-tab';
import { RoundsTab } from './tabs/rounds-tab';
import { DriveInterviewsTab } from './tabs/interviews-tab';
import { DriveOffersTab } from './tabs/offers-tab';
import { CompanyAccessTab } from './tabs/company-access-tab';

const NEXT: Record<DriveStatus, { to: DriveStatus; label: string; tone: string }[]> = {
  DRAFT: [{ to: 'PUBLISHED', label: 'Publish', tone: 'primary' }, { to: 'CANCELLED', label: 'Cancel drive', tone: 'outline-danger' }],
  PUBLISHED: [{ to: 'ONGOING', label: 'Mark ongoing', tone: 'primary' }, { to: 'DRAFT', label: 'Unpublish', tone: 'outline-secondary' },
    { to: 'CANCELLED', label: 'Cancel drive', tone: 'outline-danger' }],
  ONGOING: [{ to: 'COMPLETED', label: 'Complete drive', tone: 'success' }, { to: 'CANCELLED', label: 'Cancel drive', tone: 'outline-danger' }],
  COMPLETED: [], CANCELLED: [],
};

@Component({
  selector: 'pm-drive-detail',
  imports: [RouterLink, DatePipe, StatusBadge, Skeleton, ErrorState, EligibilityTab, ApplicantsTab, RoundsTab, DriveInterviewsTab, DriveOffersTab, CompanyAccessTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/college/drives" class="small"><i class="bi bi-arrow-left"></i> All drives</a>
    @if (failed()) { <pm-error-state (retry)="load()" /> }
    @else if (!drive()) { <pm-skeleton [rows]="8" /> }
    @else {
      @let d = drive()!;
      <header class="pm-dd-head">
        <div class="min-w-0">
          <h2 class="h5 fw-bold mb-1 text-truncate">{{ d.title }}</h2>
          <div class="small text-body-secondary d-flex flex-wrap gap-2 align-items-center">
            <pm-status [value]="d.status" /><span>{{ d.companyName }}</span>·<span>{{ d.roleTitle }}</span>·
            <span>{{ d.ctcLpa ? d.ctcLpa + ' LPA' : '' }}{{ d.stipendMonthly ? ' ₹' + d.stipendMonthly + '/mo' : '' }}</span>
            @if (d.tierName) { <span class="badge text-bg-light border">{{ d.tierName }}</span> }
            @if (d.registrationDeadline) { ·<span>Apply by {{ d.registrationDeadline | date: 'medium' }}</span> }
          </div>
        </div>
        @if (college.canWrite()) {
          <div class="d-flex flex-wrap gap-2">
            @if (d.status !== 'COMPLETED' && d.status !== 'CANCELLED') { <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/college/drives', d.id, 'edit']"><i class="bi bi-pencil"></i> Edit</a> }
            <button class="btn btn-sm btn-outline-secondary" (click)="clone()"><i class="bi bi-copy"></i> Clone</button>
            @for (n of next(d.status); track n.to) { <button class="btn btn-sm btn-{{ n.tone }}" (click)="transition(n.to)">{{ n.label }}</button> }
            @if (d.status !== 'ONGOING') { <button class="btn btn-sm btn-outline-danger" (click)="remove()" aria-label="Delete drive"><i class="bi bi-trash"></i></button> }
          </div>
        }
      </header>
      <ul class="nav nav-tabs small mb-3 flex-nowrap overflow-auto" role="tablist">
        @for (t of tabs; track t) {
          <li class="nav-item"><button class="nav-link text-nowrap" role="tab" [class.active]="tab() === t" [attr.aria-selected]="tab() === t" (click)="tab.set(t)">{{ t }}</button></li>
        }
      </ul>
      @switch (tab()) {
        @case ('Overview') {
          <div class="row g-3">
            <div class="col-lg-8"><section class="pm-dd-card">
              <h3 class="h6">Job description</h3><p class="small pm-dd-pre">{{ d.jobDescription || 'Not provided.' }}</p>
              @if (d.skills.length) { <h3 class="h6">Skills</h3><p>@for (s of d.skills; track s) { <span class="badge rounded-pill text-bg-secondary me-1">{{ s }}</span> }</p> }
              @if (d.ctcBreakup) { <h3 class="h6">CTC breakup</h3><p class="small pm-dd-pre">{{ d.ctcBreakup }}</p> }
            </section></div>
            <div class="col-lg-4"><section class="pm-dd-card small">
              <dl class="mb-0">
                <dt>Type</dt><dd>{{ d.driveType.replace('_', ' ') }}</dd>
                <dt>Mode / venue</dt><dd>{{ d.mode }} · {{ d.venue || 'TBD' }}</dd>
                <dt>Location</dt><dd>{{ d.location || '-' }}</dd>
                <dt>Drive date</dt><dd>{{ d.driveDate || 'TBD' }}</dd>
                <dt>Bond</dt><dd>{{ d.bondMonths ? d.bondMonths + ' months' : 'None' }} {{ d.bondDetails ?? '' }}</dd>
                <dt>Eligibility</dt><dd>CGPA ≥ {{ d.eligibility.minCgpa ?? 'any' }} · 10th ≥ {{ d.eligibility.minTenth ?? 'any' }} · 12th ≥ {{ d.eligibility.minTwelfth ?? 'any' }}
                  · backlogs ≤ {{ d.eligibility.maxActiveBacklogs ?? 'any' }} · batches {{ d.eligibility.batchYears?.length ? d.eligibility.batchYears!.join(', ') : 'all' }}
                  · branches {{ branchCodes(d) }}</dd>
                <dt>Pipeline</dt><dd>@for (e of counts(d); track e[0]) { <span class="me-2">{{ e[0] }}: <strong>{{ e[1] }}</strong></span> } @empty { No applicants yet }</dd>
                <dt>Rounds / offers</dt><dd>{{ d.roundsCount }} rounds · {{ d.offersCount }} offers</dd>
              </dl>
            </section></div>
          </div>
        }
        @case ('Eligibility') { <pm-eligibility-tab [drive]="d" /> }
        @case ('Applicants') { <pm-applicants-tab [drive]="d" (changed)="load()" /> }
        @case ('Rounds') { <pm-rounds-tab [drive]="d" (changed)="load()" /> }
        @case ('Interviews') { <pm-drive-interviews-tab [drive]="d" /> }
        @case ('Offers') { <pm-drive-offers-tab [drive]="d" (changed)="load()" /> }
        @case ('Company access') { <pm-company-access-tab [drive]="d" /> }
      }
    }`,
  styles: [`
    .pm-dd-head { display: flex; flex-wrap: wrap; gap: .75rem; justify-content: space-between; align-items: flex-start; margin: .5rem 0 1rem; }
    .pm-dd-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; height: 100%; }
    .pm-dd-pre { white-space: pre-wrap; } dt { color: var(--bs-secondary-color); font-weight: 500; } dd { margin-bottom: .6rem; }
  `],
})
export class DriveDetail implements OnInit {
  private svc = inject(PlacementDriveService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private router = inject(Router);
  id = input.required<string>();
  drive = signal<Drive | null>(null);
  failed = signal(false);
  tabs = ['Overview', 'Eligibility', 'Applicants', 'Rounds', 'Interviews', 'Offers', 'Company access'];
  tab = signal('Overview');

  ngOnInit(): void { this.load(); }
  load(): void {
    this.failed.set(false);
    this.svc.get(this.id()).subscribe({ next: d => this.drive.set(d), error: () => this.failed.set(true) });
  }
  next(s: DriveStatus) { return NEXT[s]; }
  counts(d: Drive): [string, number][] { return Object.entries(d.applicationCounts); }
  branchCodes(d: Drive): string {
    const ids = d.eligibility.branchIds ?? [];
    return ids.length ? this.college.branches().filter(b => ids.includes(b.id)).map(b => b.code).join(', ') : 'all';
  }

  async transition(to: DriveStatus): Promise<void> {
    const msgs: Partial<Record<DriveStatus, string>> = {
      PUBLISHED: 'Eligible students will be notified in-app and by e-mail and can start applying.',
      CANCELLED: 'Applicants will be notified. This cannot be undone.', COMPLETED: 'No further changes to rounds will be possible.',
    };
    const r = await this.confirm.ask({ title: `${to === 'DRAFT' ? 'Unpublish' : 'Move to ' + to.toLowerCase()}?`, message: msgs[to] ?? 'Change the drive status.',
      tone: to === 'CANCELLED' ? 'danger' : 'primary', inputLabel: to === 'CANCELLED' ? 'Reason (shared with applicants)' : undefined, inputRequired: to === 'CANCELLED' });
    if (!r.ok) return;
    this.svc.transition(this.id(), to, r.value || undefined).subscribe(d => { this.drive.set(d); this.toast.success(`Drive is now ${d.status.toLowerCase()}`); });
  }
  clone(): void { this.svc.clone(this.id()).subscribe(d => { this.toast.success('Drive cloned as draft'); this.router.navigate(['/college/drives', d.id, 'edit']); }); }
  async remove(): Promise<void> {
    const r = await this.confirm.ask({ title: 'Move drive to trash?', message: 'You can restore it from the Trash view.', tone: 'danger', confirmText: 'Move to trash' });
    if (r.ok) this.svc.remove(this.id()).subscribe(() => { this.toast.success('Drive moved to trash'); this.router.navigateByUrl('/college/drives'); });
  }
}
