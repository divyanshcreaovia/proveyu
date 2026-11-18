import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InterviewScheduleService } from '../services/interview-schedule.service';
import { ToastService } from '../../../core/services/toast.service';
import { AppError } from '../../../core/api';
import { Application, Conflict, Round, Slot } from '../../../core/models/college.models';
import { Modal } from '../../../shared/ui/modal';

const local = (iso?: string) => { if (!iso) return ''; const d = new Date(iso); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); };

/** Schedule (single or bulk back-to-back) / reschedule interviews with live conflict detection. */
@Component({
  selector: 'pm-slot-form',
  imports: [FormsModule, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="open()" [title]="slot() ? 'Reschedule interview' : (bulk() ? 'Bulk schedule ' + applicants().length + ' interview(s)' : 'Schedule interview')" size="lg" (closed)="closed.emit()">
      <form id="pm-slot-form" class="row g-3" (ngSubmit)="save()">
        @if (!slot() && !bulk()) {
          <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-app">Student *</label>
            <select id="sl-app" class="form-select" [(ngModel)]="m.applicationId" name="app" required (change)="check()">
              <option value="">Select</option>@for (a of applicants(); track a.id) { <option [value]="a.id">{{ a.rollNumber }} - {{ a.studentName }}</option> }</select></div>
        }
        @if (!slot()) {
          <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-round">Round</label>
            <select id="sl-round" class="form-select" [(ngModel)]="m.roundId" name="round"><option value="">-</option>
              @for (r of rounds(); track r.id) { <option [value]="r.id">R{{ r.seq }} {{ r.name }}</option> }</select></div>
        }
        <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-start">{{ bulk() ? 'First slot starts' : 'Starts' }} *</label>
          <input id="sl-start" type="datetime-local" class="form-control" [(ngModel)]="m.start" name="start" required (change)="autoEnd(); check()" /></div>
        @if (bulk()) {
          <div class="col-md-3"><label class="form-label small fw-semibold" for="sl-dur">Minutes each *</label>
            <input id="sl-dur" type="number" min="5" class="form-control" [(ngModel)]="m.duration" name="dur" /></div>
          <div class="col-md-3"><label class="form-label small fw-semibold" for="sl-gap">Gap (min)</label>
            <input id="sl-gap" type="number" min="0" class="form-control" [(ngModel)]="m.gap" name="gap" /></div>
        } @else {
          <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-end">Ends *</label>
            <input id="sl-end" type="datetime-local" class="form-control" [(ngModel)]="m.end" name="end" required (change)="check()" /></div>
        }
        <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-panel">Panel name</label>
          <input id="sl-panel" class="form-control" [(ngModel)]="m.panelName" name="panel" (change)="check()" placeholder="Panel A" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-members">Panel members</label>
          <input id="sl-members" class="form-control" [(ngModel)]="m.panelMembers" name="members" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-room">Room</label>
          <input id="sl-room" class="form-control" [(ngModel)]="m.room" name="room" (change)="check()" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="sl-link">Online meeting link</label>
          <input id="sl-link" type="url" class="form-control" [(ngModel)]="m.meetingLink" name="link" /></div>
        @if (conflicts().length) {
          <div class="col-12"><div class="alert alert-warning small mb-0" role="alert"><strong>Conflicts:</strong>
            <ul class="mb-0">@for (c of conflicts(); track $index) { <li>{{ c.type }}: {{ c.message }}</li> }</ul></div></div>
        }
        @if (skipped().length) { <div class="col-12"><div class="alert alert-info small mb-0">Skipped: {{ skipped().join(' | ') }}</div></div> }
      </form>
      <div pmFooter><button class="btn btn-outline-secondary" (click)="closed.emit()">Cancel</button>
        <button type="submit" form="pm-slot-form" class="btn btn-primary" [disabled]="busy()">Save</button></div>
    </pm-modal>`,
})
export class SlotForm {
  private svc = inject(InterviewScheduleService);
  private toast = inject(ToastService);
  open = input(false);
  driveId = input('');
  slot = input<Slot | null>(null);
  applicants = input<Application[]>([]);
  rounds = input<Round[]>([]);
  bulk = input(false);
  closed = output<void>();
  saved = output<void>();
  conflicts = signal<Conflict[]>([]);
  skipped = signal<string[]>([]);
  busy = signal(false);
  m = { applicationId: '', roundId: '', start: '', end: '', duration: 30, gap: 5, panelName: '', panelMembers: '', room: '', meetingLink: '' };

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const s = this.slot();
      this.conflicts.set([]); this.skipped.set([]);
      const ongoing = this.rounds().find(r => r.status === 'ONGOING');
      this.m = s ? { applicationId: s.applicationId, roundId: s.roundId ?? '', start: local(s.startsAt), end: local(s.endsAt), duration: 30, gap: 5,
          panelName: s.panelName ?? '', panelMembers: s.panelMembers ?? '', room: s.room ?? '', meetingLink: s.meetingLink ?? '' }
        : { applicationId: '', roundId: ongoing?.id ?? '', start: '', end: '', duration: 30, gap: 5, panelName: '', panelMembers: '', room: '', meetingLink: '' };
    });
  }

  autoEnd(): void {
    if (this.m.start && !this.m.end && !this.bulk()) this.m.end = local(new Date(new Date(this.m.start).getTime() + 30 * 60000).toISOString());
  }

  private body() {
    return { driveId: this.slot()?.driveId ?? this.driveId(), roundId: this.m.roundId || null, applicationId: this.m.applicationId,
      startsAt: new Date(this.m.start).toISOString(), endsAt: new Date(this.m.end).toISOString(), panelName: this.m.panelName || null,
      panelMembers: this.m.panelMembers || null, room: this.m.room || null, meetingLink: this.m.meetingLink || null };
  }

  check(): void {
    if (this.bulk() || !this.m.applicationId || !this.m.start || !this.m.end) return;
    if (this.slot()) return; // server re-checks on save, excluding this slot
    this.svc.check(this.body()).subscribe({ next: c => this.conflicts.set(c), error: () => this.conflicts.set([]) });
  }

  save(): void {
    if (!this.m.start || (!this.bulk() && (!this.m.end || (!this.slot() && !this.m.applicationId)))) { this.toast.warning('Fill the required fields'); return; }
    this.busy.set(true);
    const done = () => { this.busy.set(false); this.toast.success('Interview(s) scheduled'); this.saved.emit(); };
    const fail = (e: AppError) => { this.busy.set(false); if (Array.isArray(e.data)) this.conflicts.set(e.data as Conflict[]); };
    if (this.bulk()) {
      this.svc.bulk({ driveId: this.driveId(), roundId: this.m.roundId || null, applicationIds: this.applicants().map(a => a.id), startAt: new Date(this.m.start).toISOString(),
        durationMinutes: this.m.duration, gapMinutes: this.m.gap, panelName: this.m.panelName || null, panelMembers: this.m.panelMembers || null,
        room: this.m.room || null, meetingLink: this.m.meetingLink || null }).subscribe({
        next: r => { this.busy.set(false); this.toast.success(`${r.created.length} scheduled`); if (r.skipped.length) { this.skipped.set(r.skipped); } else this.saved.emit(); },
        error: fail,
      });
    } else if (this.slot()) {
      this.svc.reschedule(this.slot()!.id, this.body()).subscribe({ next: done, error: fail });
    } else {
      this.svc.schedule(this.body()).subscribe({ next: done, error: fail });
    }
  }
}
