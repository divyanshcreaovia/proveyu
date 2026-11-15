import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { InterviewScheduleService } from '../../../../services/interview-schedule.service';
import { ApplicationService } from '../../../../services/application.service';
import { RoundService } from '../../../../services/round.service';
import { CollegeService } from '../../../../services/college.service';
import { ToastService } from '../../../../services/toast.service';
import { Application, Drive, Round, Slot, SlotStatus } from '../../../../core/models/college.models';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { EmptyState } from '../../../../shared/ui/states';
import { SlotForm } from '../../interviews/slot-form';

@Component({
  selector: 'pm-drive-interviews-tab',
  imports: [DatePipe, StatusBadge, EmptyState, SlotForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (college.canWrite()) {
      <div class="d-flex gap-2 mb-3 flex-wrap">
        <button class="btn btn-sm btn-primary" [disabled]="!candidates().length" (click)="openForm(null, false)"><i class="bi bi-calendar-plus"></i> Schedule interview</button>
        <button class="btn btn-sm btn-outline-primary" [disabled]="!candidates().length" (click)="openForm(null, true)">Bulk schedule all in process ({{ candidates().length }})</button>
      </div>
    }
    <div class="pm-it-list">
      @for (s of slots(); track s.id) {
        <div class="pm-it-row">
          <div class="pm-it-time"><strong>{{ s.startsAt | date: 'dd MMM' }}</strong><span>{{ s.startsAt | date: 'shortTime' }}–{{ s.endsAt | date: 'shortTime' }}</span></div>
          <div class="flex-grow-1 min-w-0 small"><strong>{{ s.studentName }}</strong> <span class="text-body-secondary">{{ s.rollNumber }}</span><br>
            <span class="text-body-secondary">{{ s.roundName ?? 'Interview' }} · {{ s.panelName ?? 'No panel' }} · {{ s.room ?? '' }}
              @if (s.meetingLink) { <a [href]="s.meetingLink" target="_blank" rel="noopener">link</a> } @if (s.rescheduleCount) { · rescheduled {{ s.rescheduleCount }}x }</span></div>
          <pm-status [value]="s.status" />
          @if (college.canWrite() && s.status === 'SCHEDULED') {
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary" (click)="openForm(s, false)" aria-label="Reschedule"><i class="bi bi-pencil"></i></button>
              <button class="btn btn-outline-secondary" (click)="remind(s)" aria-label="Send reminder"><i class="bi bi-bell"></i></button>
              <button class="btn btn-outline-success" (click)="status(s, 'COMPLETED')" aria-label="Mark completed"><i class="bi bi-check2"></i></button>
              <button class="btn btn-outline-warning" (click)="status(s, 'NO_SHOW')" aria-label="No show"><i class="bi bi-person-x"></i></button>
              <button class="btn btn-outline-danger" (click)="status(s, 'CANCELLED')" aria-label="Cancel"><i class="bi bi-x"></i></button>
            </div>
          }
        </div>
      } @empty { <pm-empty-state icon="bi-calendar3" title="No interviews scheduled" text="Schedule interviews for students who are in process." /> }
    </div>
    <pm-slot-form [open]="formOpen()" [driveId]="drive().id" [slot]="editing()" [bulk]="bulkMode()" [applicants]="candidates()" [rounds]="rounds()"
      (closed)="formOpen.set(false)" (saved)="formOpen.set(false); load()" />`,
  styles: [`
    .pm-it-row { display: flex; gap: .75rem; align-items: center; flex-wrap: wrap; padding: .65rem .8rem; background: var(--pm-card-bg, #fff);
      border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 14px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); margin-bottom: .5rem; }
    .pm-it-time { display: flex; flex-direction: column; min-width: 92px; font-size: .8rem; }
  `],
})
export class DriveInterviewsTab implements OnInit {
  private svc = inject(InterviewScheduleService);
  private apps = inject(ApplicationService);
  private roundsSvc = inject(RoundService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  drive = input.required<Drive>();
  slots = signal<Slot[]>([]);
  candidates = signal<Application[]>([]);
  rounds = signal<Round[]>([]);
  formOpen = signal(false);
  editing = signal<Slot | null>(null);
  bulkMode = signal(false);

  ngOnInit(): void {
    this.load();
    this.apps.list(this.drive().id, null, '', 0, 500).subscribe(p =>
      this.candidates.set(p.content.filter(a => a.status === 'IN_PROCESS' || a.status === 'SHORTLISTED')));
    this.roundsSvc.list(this.drive().id).subscribe(r => this.rounds.set(r));
  }
  load(): void { this.svc.forDrive(this.drive().id).subscribe(s => this.slots.set(s)); }
  openForm(s: Slot | null, bulk: boolean): void { this.editing.set(s); this.bulkMode.set(bulk); this.formOpen.set(true); }
  status(s: Slot, st: SlotStatus): void { this.svc.setStatus(s.id, st).subscribe(() => { this.toast.success('Interview updated'); this.load(); }); }
  remind(s: Slot): void { this.svc.remind(s.id).subscribe(() => this.toast.success('Reminder sent')); }
}
