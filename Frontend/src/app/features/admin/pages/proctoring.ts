import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AdminService, FlaggedAttempt } from '../services/admin.service';
import { ProctorLog } from '../../student/services/prep.service';
import { ProctorLogView } from '../../student/prep/proctor-log';
import { Modal } from '../../../shared/ui/modal';
import { EmptyState } from '../../../shared/ui/states';

/** Mock test attempts in which a student left the test, across all colleges, with the screenshots as proof. */
@Component({
  selector: 'pm-admin-proctoring',
  imports: [DatePipe, DecimalPipe, ProctorLogView, Modal, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
      <input type="search" class="form-control form-control-sm" style="max-width: 320px" placeholder="Search college, student, roll or test"
        aria-label="Search attempts" [value]="q()" (input)="q.set($any($event.target).value)" />
      <div class="form-check form-switch mb-0 ms-1">
        <input class="form-check-input" type="checkbox" id="ap-auto" [checked]="autoOnly()" (change)="autoOnly.set($any($event.target).checked)" />
        <label class="form-check-label small" for="ap-auto">Only auto-submitted</label>
      </div>
      <span class="small text-body-secondary ms-auto">{{ shown().length }} attempt{{ shown().length === 1 ? '' : 's' }}</span>
    </div>
    <p class="small text-body-secondary">Students share their entire screen during a mock test; each time they leave it a screenshot is saved with the time.
      The warning limit and how long screenshots are kept are in Settings.</p>
    <div class="table-responsive bg-body border rounded-3">
      <table class="table table-hover align-middle small mb-0">
        <thead><tr><th>Student</th><th class="d-none d-md-table-cell">College</th><th>Test</th><th class="d-none d-lg-table-cell">Started</th><th>Left</th></tr></thead>
        <tbody>
          @for (a of shown(); track a.attemptId) {
            <tr class="ap-row" tabindex="0" (click)="open(a)" (keydown.enter)="open(a)">
              <td><strong>{{ a.studentName }}</strong><br><span class="text-body-secondary">{{ a.rollNumber }}</span></td>
              <td class="d-none d-md-table-cell">{{ a.collegeName || '-' }}</td>
              <td>{{ a.testTitle }}<br><span class="text-body-secondary">@if (a.submittedAt) { {{ a.score | number: '1.0-1' }}/{{ a.maxScore }} } @else { In progress }</span></td>
              <td class="d-none d-lg-table-cell">{{ a.startedAt | date: 'd MMM, h:mm a' }}</td>
              <td>@if (a.autoSubmitReason === 'TAB_SWITCH') { <span class="badge text-bg-danger">{{ a.tabSwitches }}× · auto-submitted</span> }
                @else if (a.tabSwitches) { <span class="badge text-bg-warning">{{ a.tabSwitches }}× warning{{ a.tabSwitches === 1 ? '' : 's' }}</span> }
                @if (a.aiWarnings) { <span class="badge ms-1" style="background:#ede9fe;color:#6d28d9" title="Camera / microphone events"><i class="bi bi-camera-video me-1"></i>{{ a.aiWarnings }}</span> }</td>
            </tr>
          } @empty { <tr><td colspan="5"><pm-empty-state icon="bi-shield-check" title="No student left a test" text="Flagged attempts appear here." /></td></tr> }
        </tbody>
      </table>
    </div>
    <pm-modal [open]="!!sel()" [title]="sel() ? sel()!.studentName + ' · ' + sel()!.testTitle : ''" size="lg" (closed)="sel.set(null)">
      @if (sel(); as a) {
        <p class="small text-body-secondary mb-2">{{ a.collegeName }} · {{ a.rollNumber }} · started {{ a.startedAt | date: 'd MMM y, h:mm a' }}</p>
        @if (log(); as l) { <pm-proctor-log [log]="l" [image]="screenshot" [subject]="a.studentName" /> }
        @else { <div class="text-center py-3"><span class="spinner-border spinner-border-sm"></span></div> }
      }
    </pm-modal>`,
  styles: [`.ap-row { cursor: pointer; } .ap-row:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }`],
})
export class AdminProctoringPage implements OnInit {
  private svc = inject(AdminService);
  rows = signal<FlaggedAttempt[]>([]);
  q = signal('');
  autoOnly = signal(false);
  sel = signal<FlaggedAttempt | null>(null);
  log = signal<ProctorLog | null>(null);
  readonly screenshot = (id: string) => this.svc.screenshotUrl(id);
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.rows().filter(a => (!this.autoOnly() || a.autoSubmitReason === 'TAB_SWITCH')
      && (!q || [a.studentName, a.rollNumber, a.collegeName, a.testTitle].join(' ').toLowerCase().includes(q)));
  });

  ngOnInit(): void { this.svc.flagged().subscribe(r => this.rows.set(r)); }
  open(a: FlaggedAttempt): void {
    this.sel.set(a);
    this.log.set(null);
    this.svc.proctorLog(a.attemptId).subscribe(l => this.log.set(l));
  }
}
