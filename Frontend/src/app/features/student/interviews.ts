import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { StudentPortalService } from '../../services/student-portal.service';
import { FileService } from '../../services/file.service';
import { PortalInterview } from '../../core/models/college.models';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../shared/ui/states';

/** Interview slots the placement cell scheduled for the student, with join links and calendar export. */
@Component({
  selector: 'pm-student-interviews',
  imports: [DatePipe, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './student-pages.scss',
  template: `
    @if (loading()) { <pm-skeleton [rows]="5" /> } @else {
      <h2 class="h6 st-h mb-2">Upcoming</h2>
      @for (i of upcoming(); track i.id) {
        <div class="st-row">
          <div class="text-center px-2" style="min-width: 64px"><div class="small st-muted">{{ i.startsAt | date: 'MMM' }}</div><div class="h4 st-h mb-0">{{ i.startsAt | date: 'd' }}</div></div>
          <span class="flex-grow-1 small"><strong>{{ i.companyName }}</strong> · {{ i.roundName ?? 'Interview' }}<br>
            <span class="st-muted">{{ i.startsAt | date: 'EEE, h:mm a' }} – {{ i.endsAt | date: 'h:mm a' }} · {{ i.room ?? 'Online' }} @if (i.panelName) { · {{ i.panelName }} }</span></span>
          <pm-status [value]="i.status" />
          @if (i.meetingLink) { <a class="btn btn-sm btn-primary" [href]="i.meetingLink" target="_blank" rel="noopener">Join</a> }
          <button class="btn btn-sm btn-outline-secondary" (click)="addToCalendar(i)" title="Download calendar invite"><i class="bi bi-calendar-plus"></i> Add to calendar</button>
        </div>
      } @empty { <pm-empty-state icon="bi-calendar3" title="No upcoming interviews" text="When the placement cell schedules you, it shows up here and you get a reminder a day before." /> }
      @if (past().length) {
        <h2 class="h6 st-h mt-4 mb-2">Past</h2>
        @for (i of past(); track i.id) {
          <div class="st-row"><span class="flex-grow-1 small"><strong>{{ i.companyName }}</strong> · {{ i.roundName ?? 'Interview' }}<br>
            <span class="st-muted">{{ i.startsAt | date: 'medium' }}</span></span><pm-status [value]="i.status" /></div>
        }
      }
    }`,
})
export class StudentInterviews implements OnInit {
  private portal = inject(StudentPortalService);
  private files = inject(FileService);
  loading = signal(true);
  all = signal<PortalInterview[]>([]);
  upcoming = computed(() => this.all().filter(i => i.status === 'SCHEDULED' && new Date(i.endsAt) > new Date()));
  past = computed(() => this.all().filter(i => !this.upcoming().includes(i)).reverse());

  ngOnInit(): void { this.portal.interviews().subscribe({ next: i => { this.all.set(i); this.loading.set(false); }, error: () => this.loading.set(false) }); }

  /** Builds a standard .ics file so the student can add the slot to Google/Outlook/Apple calendar. */
  addToCalendar(i: PortalInterview): void {
    const fmt = (s: string) => new Date(s).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    const esc = (s: string) => s.replace(/[\;,]/g, m => '\\' + m).replace(/\n/g, '\\n');
    const ics = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Campus Placements//EN', 'BEGIN:VEVENT', `UID:${i.id}`, `DTSTAMP:${fmt(new Date().toISOString())}`,
      `DTSTART:${fmt(i.startsAt)}`, `DTEND:${fmt(i.endsAt)}`, `SUMMARY:${esc(`${i.companyName} - ${i.roundName ?? 'Interview'}`)}`,
      `LOCATION:${esc(i.meetingLink ?? i.room ?? '')}`, `DESCRIPTION:${esc(i.driveTitle)}`, 'END:VEVENT', 'END:VCALENDAR'].join('\r\n');
    this.files.save(new Blob([ics], { type: 'text/calendar' }), `interview-${i.companyName.replace(/\W+/g, '-')}.ics`);
  }
}
