import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InterviewScheduleService } from '../../../services/interview-schedule.service';
import { ToastService } from '../../../services/toast.service';
import { Slot } from '../../../core/models/college.models';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { Modal } from '../../../shared/ui/modal';

/** Interview calendar (month / week) across all drives. */
@Component({
  selector: 'pm-interviews',
  imports: [DatePipe, RouterLink, StatusBadge, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <div class="btn-group btn-group-sm">
        <button class="btn btn-outline-secondary" (click)="shift(-1)" aria-label="Previous"><i class="bi bi-chevron-left"></i></button>
        <button class="btn btn-outline-secondary" (click)="today()">Today</button>
        <button class="btn btn-outline-secondary" (click)="shift(1)" aria-label="Next"><i class="bi bi-chevron-right"></i></button>
      </div>
      <h2 class="h6 m-0 fw-bold">{{ heading() }}</h2>
      <div class="btn-group btn-group-sm ms-auto" role="group" aria-label="View">
        <button class="btn" [class.btn-primary]="view() === 'month'" [class.btn-outline-primary]="view() !== 'month'" (click)="setView('month')">Month</button>
        <button class="btn" [class.btn-primary]="view() === 'week'" [class.btn-outline-primary]="view() !== 'week'" (click)="setView('week')">Week</button>
      </div>
    </div>
    <div class="pm-cal" [class.pm-cal-week]="view() === 'week'" role="grid" aria-label="Interview calendar">
      @for (d of ['Mon','Tue','Wed','Thu','Fri','Sat','Sun']; track d) { <div class="pm-cal-dow d-none d-md-block" role="columnheader">{{ d }}</div> }
      @for (day of days(); track day.key) {
        <div class="pm-cal-day" [class.pm-cal-out]="!day.inMonth" [class.pm-cal-today]="day.today" role="gridcell" [attr.aria-label]="day.date | date: 'fullDate'">
          <div class="pm-cal-num"><span class="d-md-none">{{ day.date | date: 'EEE' }} </span>{{ day.date | date: 'd' }}</div>
          @for (s of day.slots; track s.id) {
            <button type="button" class="pm-cal-ev pm-cal-{{ s.status }}" (click)="sel.set(s)">
              {{ s.startsAt | date: 'shortTime' }} {{ s.studentName }} · {{ s.companyName }}</button>
          }
        </div>
      }
    </div>
    <pm-modal [open]="!!sel()" title="Interview" (closed)="sel.set(null)">
      @if (sel(); as s) {
        <dl class="small mb-0">
          <dt>Student</dt><dd>{{ s.studentName }} ({{ s.rollNumber }})</dd>
          <dt>Drive</dt><dd><a [routerLink]="['/college/drives', s.driveId]" (click)="sel.set(null)">{{ s.companyName }} · {{ s.driveTitle }}</a></dd>
          <dt>When</dt><dd>{{ s.startsAt | date: 'medium' }} – {{ s.endsAt | date: 'shortTime' }}</dd>
          <dt>Round / panel</dt><dd>{{ s.roundName ?? '-' }} · {{ s.panelName ?? '-' }} {{ s.panelMembers ? '(' + s.panelMembers + ')' : '' }}</dd>
          <dt>Where</dt><dd>{{ s.room ?? '' }} @if (s.meetingLink) { <a [href]="s.meetingLink" target="_blank" rel="noopener">{{ s.meetingLink }}</a> }</dd>
          <dt>Status</dt><dd><pm-status [value]="s.status" /> @if (s.reminderSentAt) { <small class="text-body-secondary">reminder sent {{ s.reminderSentAt | date: 'short' }}</small> }</dd>
        </dl>
      }
      <div pmFooter>@if (sel()?.status === 'SCHEDULED') { <button class="btn btn-sm btn-outline-primary" (click)="remind()"><i class="bi bi-bell"></i> Send reminder</button> }
        <a class="btn btn-sm btn-primary" [routerLink]="['/college/drives', sel()?.driveId]">Manage in drive</a></div>
    </pm-modal>`,
  styles: [`
    .pm-cal { display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px; background: var(--pm-card-border, #e2e8f0); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; overflow: hidden; box-shadow: var(--pm-card-shadow); }
    @media (max-width: 767.98px) { .pm-cal { grid-template-columns: 1fr; } .pm-cal-out { display: none; } }
    .pm-cal-dow { background: var(--pm-table-head, #f8fafc); color: var(--pm-muted, #64748b); letter-spacing: .05em; font-size: .72rem; font-weight: 600; text-align: center; padding: .35rem; text-transform: uppercase; }
    .pm-cal-day { background: var(--pm-card-bg, #fff); min-height: 104px; padding: .3rem; display: flex; flex-direction: column; gap: 2px; }
    .pm-cal-week .pm-cal-day { min-height: 340px; }
    .pm-cal-out { background: var(--bs-tertiary-bg); opacity: .6; }
    .pm-cal-today .pm-cal-num { background: #2563eb; color: #fff; border-radius: 999px; padding: 0 .4rem; align-self: flex-start; }
    .pm-cal-num { font-size: .75rem; font-weight: 600; }
    .pm-cal-ev { all: unset; font-size: .7rem; padding: 1px 4px; border-radius: 4px; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      background: rgba(37,99,235,.14); color: #1d4ed8; box-sizing: border-box; }
    .pm-cal-ev:focus-visible { outline: 2px solid #2563eb; }
    .pm-cal-COMPLETED { background: rgba(22,163,74,.15); color: #15803d; } .pm-cal-CANCELLED, .pm-cal-NO_SHOW { background: rgba(225,29,72,.12); color: #be123c; text-decoration: line-through; }
  `],
})
export class Interviews implements OnInit {
  private svc = inject(InterviewScheduleService);
  private toast = inject(ToastService);
  view = signal<'month' | 'week'>('month');
  anchor = signal(new Date());
  slots = signal<Slot[]>([]);
  sel = signal<Slot | null>(null);

  range = computed(() => {
    const a = this.anchor();
    let start: Date, end: Date;
    if (this.view() === 'month') {
      const first = new Date(a.getFullYear(), a.getMonth(), 1);
      start = new Date(first); start.setDate(1 - ((first.getDay() + 6) % 7));
      end = new Date(start); end.setDate(start.getDate() + 42);
    } else {
      start = new Date(a.getFullYear(), a.getMonth(), a.getDate() - ((a.getDay() + 6) % 7));
      end = new Date(start); end.setDate(start.getDate() + 7);
    }
    return { start, end };
  });
  heading = computed(() => this.anchor().toLocaleDateString(undefined, this.view() === 'month' ? { month: 'long', year: 'numeric' } : { day: 'numeric', month: 'short', year: 'numeric' })
    + (this.view() === 'week' ? ' (week)' : ''));
  days = computed(() => {
    const { start, end } = this.range();
    const out: { key: string; date: Date; inMonth: boolean; today: boolean; slots: Slot[] }[] = [];
    const todayKey = new Date().toDateString();
    for (const d = new Date(start); d < end; d.setDate(d.getDate() + 1)) {
      const date = new Date(d);
      out.push({ key: date.toDateString(), date, inMonth: this.view() === 'week' || date.getMonth() === this.anchor().getMonth(), today: date.toDateString() === todayKey,
        slots: this.slots().filter(s => new Date(s.startsAt).toDateString() === date.toDateString()) });
    }
    return out;
  });

  ngOnInit(): void { this.load(); }
  load(): void { const { start, end } = this.range(); this.svc.calendar(start, end).subscribe(s => this.slots.set(s)); }
  shift(n: number): void {
    const a = new Date(this.anchor());
    if (this.view() === 'month') a.setMonth(a.getMonth() + n); else a.setDate(a.getDate() + 7 * n);
    this.anchor.set(a); this.load();
  }
  today(): void { this.anchor.set(new Date()); this.load(); }
  setView(v: 'month' | 'week'): void { this.view.set(v); this.load(); }
  remind(): void { this.svc.remind(this.sel()!.id).subscribe(s => { this.sel.set(s); this.toast.success('Reminder sent'); }); }
}
