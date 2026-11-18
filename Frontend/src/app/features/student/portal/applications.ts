import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { StudentPortalService } from '../services/student-portal.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { PortalApplication, PortalRound } from '../../../core/models/college.models';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { Modal } from '../../../shared/ui/modal';

/** The student's applications with a round-by-round progress timeline. */
@Component({
  selector: 'pm-student-applications',
  imports: [DatePipe, StatusBadge, EmptyState, Skeleton, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    @if (loading()) { <pm-skeleton [rows]="5" /> } @else {
      @for (a of apps(); track a.id) {
        <div class="st-row"><span class="flex-grow-1 small"><strong>{{ a.companyName }}</strong> · {{ a.driveTitle }}<br>
          <span class="st-muted">Applied {{ a.appliedAt | date: 'mediumDate' }} @if (a.currentRound) { · in round {{ a.currentRound }} }</span></span>
          <pm-status [value]="a.status" />
          <button class="btn btn-sm btn-outline-secondary" (click)="openRounds(a)">Track rounds</button>
          @if (a.status === 'APPLIED' || a.status === 'SHORTLISTED' || a.status === 'WAITLISTED') {
            <button class="btn btn-sm btn-outline-danger" (click)="withdraw(a)">Withdraw</button> }
        </div>
      } @empty { <pm-empty-state icon="bi-list-check" title="No applications yet" text="Apply to an open drive and track every round here." /> }
    }
    <pm-modal [open]="!!sel()" [title]="(sel()?.companyName ?? '') + ' · selection process'" (closed)="sel.set(null)">
      @if (rounds() === null) { <pm-skeleton [rows]="3" /> } @else {
        <ol class="st-tl">
          <li><span class="st-tl-dot st-tl-pass"><i class="bi bi-check"></i></span><div class="small fw-semibold">Applied</div>
            <div class="small st-muted">{{ sel()?.appliedAt | date: 'medium' }}</div></li>
          @for (r of rounds(); track r.roundId) {
            <li><span class="st-tl-dot" [class.st-tl-pass]="r.result === 'PASS'" [class.st-tl-fail]="r.result === 'FAIL' || r.result === 'ABSENT'"
                [class.st-tl-now]="r.status === 'ONGOING' && (!r.result || r.result === 'PENDING')">{{ r.seq }}</span>
              <div class="small fw-semibold d-flex gap-2 align-items-center">{{ r.name }} <span class="st-muted fw-normal">{{ r.roundType }}</span>
                @if (r.result && r.result !== 'PENDING') { <pm-status [value]="r.result" /> } @else { <pm-status [value]="r.status" /> }</div>
              @if (r.scheduledAt) { <div class="small st-muted">{{ r.scheduledAt | date: 'medium' }}</div> }</li>
          } @empty { <li><span class="st-tl-dot"><i class="bi bi-hourglass"></i></span><div class="small st-muted">The company hasn't announced rounds yet.</div></li> }
          <li><span class="st-tl-dot" [class.st-tl-pass]="sel()?.status === 'SELECTED'" [class.st-tl-fail]="sel()?.status === 'REJECTED'"><i class="bi bi-flag"></i></span>
            <div class="small fw-semibold">Result <pm-status [value]="sel()?.status" /></div></li>
        </ol>
      }
    </pm-modal>`,
})
export class StudentApplications implements OnInit {
  private portal = inject(StudentPortalService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  loading = signal(true);
  apps = signal<PortalApplication[]>([]);
  sel = signal<PortalApplication | null>(null);
  rounds = signal<PortalRound[] | null>(null);

  ngOnInit(): void { this.load(); }
  load(): void { this.portal.applications().subscribe({ next: a => { this.apps.set(a); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  openRounds(a: PortalApplication): void {
    this.sel.set(a);
    this.rounds.set(null);
    this.portal.rounds(a.id).subscribe(r => this.rounds.set(r));
  }
  async withdraw(a: PortalApplication): Promise<void> {
    const r = await this.confirm.ask({ title: 'Withdraw application?', message: `${a.companyName} · ${a.driveTitle}`, tone: 'danger', confirmText: 'Withdraw' });
    if (r.ok) this.portal.withdraw(a.id).subscribe(() => { this.toast.success('Application withdrawn'); this.load(); });
  }
}
