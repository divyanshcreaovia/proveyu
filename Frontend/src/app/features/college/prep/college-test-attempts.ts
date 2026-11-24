import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AdminTest, CollegeAttempt, PrepService, ProctorLog } from '../../student/services/prep.service';
import { Pager, pageOf } from '../../../shared/ui/pager';
import { ProctorLogView } from '../../student/prep/proctor-log';
import { Modal } from '../../../shared/ui/modal';
import { EmptyState } from '../../../shared/ui/states';

/**
 * College portal > Mock tests > Student attempts: every mock test attempt by the college's students (within the member's branch
 * scope) and, as proof, each time a student left a test, with the time and a screenshot of their screen.
 */
@Component({
  selector: 'pm-college-test-attempts',
  imports: [DatePipe, DecimalPipe, ProctorLogView, Modal, EmptyState, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
      <input type="search" class="form-control form-control-sm" style="max-width: 280px" placeholder="Search student, roll number or test"
        aria-label="Search attempts" [value]="q()" (input)="q.set($any($event.target).value); page.set(0)" />
      <select class="form-select form-select-sm" style="max-width: 280px" aria-label="Filter by test" [value]="testId()" (change)="setTest($any($event.target).value)">
        <option value="">All tests</option>
        @for (t of tests(); track t.id) { <option [value]="t.id">{{ t.title }}{{ t.exam?.mode === 'EXAM' ? ' (exam)' : '' }}</option> }
      </select>
      <div class="form-check form-switch mb-0 ms-1">
        <input class="form-check-input" type="checkbox" id="ta-flagged" [checked]="flagged()" (change)="setFlagged($any($event.target).checked)" />
        <label class="form-check-label small" for="ta-flagged">Only flagged attempts (left the test, or camera / microphone events)</label>
      </div>
      <span class="small text-body-secondary ms-auto">{{ shown().length }} attempt{{ shown().length === 1 ? '' : 's' }}</span>
    </div>
    <p class="small text-body-secondary">Students share their entire screen during a mock test. Each time they leave the test a screenshot is saved with
      the time; after the last warning, leaving again submits the test. Open a row to see the screenshots.</p>

    <div class="table-responsive bg-body border rounded-3">
      <table class="table table-hover align-middle small mb-0">
        <thead><tr><th>Student</th><th>Test</th><th class="d-none d-md-table-cell">Started</th><th>Score</th><th>Proctoring</th></tr></thead>
        <tbody>
          @for (a of pageRows(); track a.attemptId) {
            <tr class="ta-row" tabindex="0" (click)="openAttempt(a)" (keydown.enter)="openAttempt(a)" [attr.aria-label]="'Open attempt of ' + a.studentName">
              <td><strong>{{ a.studentName }}</strong><br><span class="text-body-secondary">{{ a.rollNumber }}{{ a.branchName ? ' · ' + a.branchName : '' }}</span></td>
              <td>{{ a.testTitle }}</td>
              <td class="d-none d-md-table-cell">{{ a.startedAt | date: 'd MMM, h:mm a' }}</td>
              <td>@if (a.submittedAt) { {{ a.score | number: '1.0-1' }}/{{ a.maxScore }} } @else { <span class="badge text-bg-info">In progress</span> }</td>
              <td>
                @if (a.autoSubmitReason === 'TAB_SWITCH') { <span class="badge text-bg-danger">{{ a.tabSwitches }}× · auto-submitted</span> }
                @else if (a.tabSwitches) { <span class="badge text-bg-warning">{{ a.tabSwitches }}× warning{{ a.tabSwitches === 1 ? '' : 's' }}</span> }
                @if (a.aiWarnings) { <span class="badge ta-ai ms-1" title="Camera / microphone events (review the photos)"><i class="bi bi-camera-video me-1"></i>{{ a.aiWarnings }}</span> }
                @else { <span class="text-body-secondary">Never</span> }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5"><pm-empty-state icon="bi-shield-check" [title]="flagged() ? 'No flagged attempts' : 'No attempts yet'"
              [text]="flagged() ? 'Turn off the filter to see every attempt.' : 'Attempts appear here once your students take a mock test.'" /></td></tr>
          }
        </tbody>
      </table>
    </div>
    <pm-pager [total]="shown().length" [(page)]="page" [(size)]="size" />

    <pm-modal [open]="!!sel()" [title]="sel() ? sel()!.studentName + ' · ' + sel()!.testTitle : ''" size="lg" (closed)="close()">
      @if (sel(); as a) {
        <p class="small text-body-secondary mb-2">{{ a.rollNumber }}{{ a.branchName ? ' · ' + a.branchName : '' }} · started {{ a.startedAt | date: 'd MMM y, h:mm a' }}
          @if (a.submittedAt) { · submitted {{ a.submittedAt | date: 'h:mm a' }} · score {{ a.score | number: '1.0-1' }}/{{ a.maxScore }} }</p>
        @if (log(); as l) { <pm-proctor-log [log]="l" [image]="screenshot" [subject]="a.studentName" /> }
        @else { <div class="text-center py-3"><span class="spinner-border spinner-border-sm"></span></div> }
      }
    </pm-modal>`,
  styles: [`.ta-row { cursor: pointer; } .ta-row:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
    .ta-ai { background: #ede9fe; color: #6d28d9; }`],
})
export class CollegeTestAttempts implements OnInit {
  private prep = inject(PrepService);
  rows = signal<CollegeAttempt[]>([]);
  /** The college's tests for the filter; '' = all tests. */
  tests = signal<AdminTest[]>([]);
  testId = signal('');
  page = signal(0);
  size = signal(25);
  flagged = signal(true);
  q = signal('');
  sel = signal<CollegeAttempt | null>(null);
  log = signal<ProctorLog | null>(null);
  readonly screenshot = (eventId: string) => this.prep.collegeScreenshotUrl(eventId);
  shown = computed(() => {
    const q = this.q().trim().toLowerCase();
    return q ? this.rows().filter(a => `${a.studentName} ${a.rollNumber} ${a.testTitle}`.toLowerCase().includes(q)) : this.rows();
  });
  pageRows = computed(() => pageOf(this.shown(), this.page(), this.size()));

  ngOnInit(): void {
    this.load();
    this.prep.collegeList().subscribe(t => this.tests.set(t));
  }
  setFlagged(on: boolean): void { this.flagged.set(on); this.load(); }
  setTest(id: string): void { this.testId.set(id); this.load(); }
  private load(): void {
    this.page.set(0);
    this.prep.collegeAttempts(this.flagged(), this.testId() || null).subscribe(r => this.rows.set(r));
  }

  openAttempt(a: CollegeAttempt): void {
    this.sel.set(a);
    this.log.set(null);
    this.prep.collegeProctorLog(a.attemptId).subscribe(l => this.log.set(l));
  }
  close(): void { this.sel.set(null); this.log.set(null); }
}
