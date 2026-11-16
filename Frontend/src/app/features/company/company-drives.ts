import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DriveRoomService, RoomDriveCard } from '../../services/drive-room.service';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../shared/ui/states';

/** Drives this recruiter has been invited to, across colleges. */
@Component({
  selector: 'pm-company-drives',
  imports: [DatePipe, RouterLink, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student/student-pages.scss',
  template: `
    @if (loading()) { <pm-skeleton [rows]="5" /> } @else {
      <div class="row g-3">
        @for (d of drives(); track d.driveId) {
          <div class="col-md-6 col-xl-4"><a class="st-card h-100 d-flex flex-column text-decoration-none cd-card" [routerLink]="['/company/drives', d.driveId]">
            <div class="d-flex justify-content-between gap-2 align-items-start"><div class="min-w-0">
              <h2 class="h6 st-h mb-0 text-truncate">{{ d.title }}</h2><div class="small st-muted text-truncate">{{ d.collegeName }}</div></div>
              <pm-status [value]="d.status" /></div>
            <div class="small my-2 st-muted">{{ d.roleTitle }}@if (d.ctcLpa) { · {{ d.ctcLpa }} LPA }@if (d.driveDate) { · {{ d.driveDate | date: 'mediumDate' }} }</div>
            <div class="cd-stats mt-auto">
              <div><strong>{{ d.applicants }}</strong><span>applicants</span></div>
              <div><strong>{{ d.shortlisted }}</strong><span>in process</span></div>
              <div><strong>{{ d.selected }}</strong><span>selected</span></div>
              <div><strong>{{ d.offers }}</strong><span>offers</span></div>
            </div>
          </a></div>
        } @empty {
          <div class="col-12"><pm-empty-state icon="bi-briefcase" title="No drives yet" text="When a college invites you to a campus drive, it appears here." /></div>
        }
      </div>
    }`,
  styles: [`
    .cd-card { color: inherit; transition: transform .15s, box-shadow .15s; }
    .cd-card:hover { transform: translateY(-2px); box-shadow: var(--pm-card-shadow-hover); }
    .cd-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: .5rem; border-top: 1px solid var(--pm-divider); padding-top: .75rem; text-align: center; }
    .cd-stats strong { display: block; font-size: 1.15rem; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .cd-stats span { font-size: .7rem; color: var(--pm-muted); }
  `],
})
export class CompanyDrives implements OnInit {
  private room = inject(DriveRoomService);
  loading = signal(true);
  drives = signal<RoomDriveCard[]>([]);
  ngOnInit(): void { this.room.drives().subscribe({ next: d => { this.drives.set(d); this.loading.set(false); }, error: () => this.loading.set(false) }); }
}
