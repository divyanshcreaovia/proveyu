import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { StudentPortalService } from '../../services/student-portal.service';
import { PortalAnnouncement } from '../../core/models/college.models';
import { EmptyState, Skeleton } from '../../shared/ui/states';

/** Announcements the placement cell sent to this student, newest first, with read tracking. */
@Component({
  selector: 'pm-student-announcements',
  imports: [DatePipe, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './student-pages.scss',
  template: `
    <div class="d-flex justify-content-end mb-2">
      @if (portal.unreadAnnouncements()) { <button class="btn btn-sm btn-outline-secondary" (click)="readAll()"><i class="bi bi-check2-all"></i> Mark all as read</button> }
    </div>
    @if (loading()) { <pm-skeleton [rows]="5" /> } @else {
      @for (a of items(); track a.id) {
        <article class="st-row d-block" [class.st-row-unread]="!a.read" (click)="open(a)" (keydown.enter)="open(a)" tabindex="0" role="button" [attr.aria-expanded]="expanded() === a.id">
          <div class="d-flex gap-2 align-items-start"><h2 class="h6 mb-1 flex-grow-1" [class.st-h]="!a.read">{{ a.title }}</h2>
            <small class="st-muted text-nowrap">{{ a.createdAt | date: 'medium' }}</small></div>
          <p class="small mb-1 st-pre" [class.text-truncate]="expanded() !== a.id">{{ a.body }}</p>
          <small class="st-muted">From {{ a.fromName }}</small>
        </article>
      } @empty { <pm-empty-state icon="bi-megaphone" title="No announcements yet" text="Notices from your placement cell will appear here." /> }
      @if (hasMore()) { <div class="text-center"><button class="btn btn-sm btn-outline-secondary" (click)="more()">Load more</button></div> }
    }`,
})
export class StudentAnnouncements implements OnInit {
  portal = inject(StudentPortalService);
  loading = signal(true);
  items = signal<PortalAnnouncement[]>([]);
  expanded = signal<string | null>(null);
  hasMore = signal(false);
  private page = 0;

  ngOnInit(): void { this.fetch(); }
  fetch(): void {
    this.portal.announcements(this.page, 20).subscribe({
      next: p => { this.items.update(l => [...l, ...p.content]); this.hasMore.set(p.page + 1 < p.totalPages); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
  more(): void { this.page++; this.fetch(); }
  open(a: PortalAnnouncement): void {
    this.expanded.set(this.expanded() === a.id ? null : a.id);
    if (!a.read) {
      this.portal.markAnnouncementRead(a.id).subscribe();
      this.items.update(l => l.map(x => x.id === a.id ? { ...x, read: true } : x));
    }
  }
  readAll(): void {
    this.portal.markAllAnnouncementsRead().subscribe();
    this.items.update(l => l.map(x => ({ ...x, read: true })));
  }
}
