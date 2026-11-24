import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminTest, PrepService } from '../../student/services/prep.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { FileUpload } from '../../../shared/ui/file-upload';
import { EmptyState } from '../../../shared/ui/states';
import { AppError } from '../../../core/api';
import { CollegeTestAttempts } from './college-test-attempts';
import { ExamMonitorView, ExamSettingsDialog } from './college-exams';
import { Pager, pageOf } from '../../../shared/ui/pager';

const EXAMPLE = `{
  "title": "Infosys drive practice - 12 Dec",
  "description": "Aptitude and coding practice before the Infosys drive.",
  "companyTag": "Infosys",
  "level": "MEDIUM",
  "durationMinutes": 60,
  "sections": [
    {
      "name": "Quantitative aptitude",
      "questions": [
        {
          "type": "MCQ",
          "text": "A train 150 m long passes a pole in 15 seconds. What is its speed in km/h?",
          "options": ["30 km/h", "36 km/h", "40 km/h", "54 km/h"],
          "answer": 1,
          "explanation": "150 / 15 = 10 m/s = 36 km/h."
        }
      ]
    },
    {
      "name": "Coding",
      "questions": [
        {
          "type": "CODING",
          "title": "Sum of digits",
          "text": "Read N and print the sum of its digits.\\nInput: one integer N.\\nOutput: the sum.",
          "marks": 10,
          "samples": [ { "input": "1234", "output": "10" } ],
          "tests":   [ { "input": "999", "output": "27" }, { "input": "7", "output": "7" } ]
        }
      ]
    }
  ]
}`;

/**
 * College portal > Mock tests: the placement team uploads timed tests for its own students (e.g. before a drive), with the file
 * format explained on the page, and publishes them. Students see them under Preparation > Mock tests > College assessments.
 */
@Component({
  selector: 'pm-college-mock-tests',
  imports: [DatePipe, FileUpload, EmptyState, CollegeTestAttempts, ExamSettingsDialog, ExamMonitorView, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="nav nav-tabs mb-3" role="tablist">
      <li class="nav-item" role="presentation"><button class="nav-link" role="tab" [class.active]="tab() === 'tests'" [attr.aria-selected]="tab() === 'tests'"
        (click)="tab.set('tests')"><i class="bi bi-journal-code me-1"></i>Tests &amp; exams</button></li>
      <li class="nav-item" role="presentation"><button class="nav-link" role="tab" [class.active]="tab() === 'attempts'" [attr.aria-selected]="tab() === 'attempts'"
        (click)="tab.set('attempts')"><i class="bi bi-shield-check me-1"></i>Student attempts &amp; proctoring</button></li>
    </ul>
    @if (tab() === 'attempts') { <pm-college-test-attempts /> } @else if (monitorFor(); as mt) { <pm-exam-monitor [test]="mt" (back)="monitorFor.set(null); load()" /> } @else {
    <section class="bg-body border rounded-3 p-3 mb-3">
      <h2 class="h5 fw-bold mb-1">Tests and exams for your students</h2>
      <p class="small text-body-secondary mb-3">Upload a test, then in <strong>Settings</strong> keep it a <strong>practice test</strong> (open any time, retakes allowed) or make it an
        <strong>exam</strong> such as a sessional: one attempt, a fixed time window, only the branches and batches you choose, shuffled questions, and results hidden
        until you release them. Students see them under <em>College tests</em>; <strong>Monitor</strong> shows who is writing, gives extra time, marks written
        answers and downloads the marks register.</p>
      <ol class="mt-steps">
        <li><span>1</span> <a href="samples/mock-test-template.json" download="mock-test-template.json">Download the template</a></li>
        <li><span>2</span> Edit it (format below)</li>
        <li><span>3</span> Upload: it is saved as a draft</li>
        <li><span>4</span> Check it, then Publish</li>
      </ol>
    </section>

    <div class="row g-3 mb-3">
      @if (college.canWrite()) {
        <div class="col-lg-5">
          <div class="bg-body border rounded-3 p-3 h-100">
            <h3 class="h6 fw-bold">Upload a test</h3>
            <p class="small text-body-secondary">One JSON file per test, up to 2 MB. If the file has mistakes, you will see exactly which question to fix.</p>
            <pm-file-upload accept=".json" [maxMb]="2" [disabled]="busy()" label="Choose a test JSON file or drag it here" (fileSelected)="upload($event)" />
            @if (error()) { <div class="alert alert-danger small mt-2 mb-0" role="alert"><strong>Could not upload.</strong> {{ error() }}</div> }
          </div>
        </div>
      }
      <div [class]="college.canWrite() ? 'col-lg-7' : 'col-12'">
        <div class="bg-body border rounded-3 p-3 h-100 small">
          <h3 class="h6 fw-bold">File format</h3>
          <table class="table table-sm mb-2 mt-fmt">
            <tbody>
              <tr><th><code>title</code></th><td>Required. Name students see.</td></tr>
              <tr><th><code>durationMinutes</code></th><td>Required, 5 to 300. The timer; the test submits itself when it ends.</td></tr>
              <tr><th><code>description</code>, <code>companyTag</code></th><td>Optional. Shown on the test card (e.g. "Infosys").</td></tr>
              <tr><th><code>level</code></th><td>Optional label: EASY, MEDIUM or HARD.</td></tr>
              <tr><th><code>sections</code></th><td>Required. Each has a <code>name</code> and a list of <code>questions</code>.</td></tr>
            </tbody>
          </table>
          <details class="mb-2" open>
            <summary class="fw-semibold">MCQ question</summary>
            <ul class="ps-3 mb-0 mt-1">
              <li><code>"type": "MCQ"</code>, <code>text</code>, and 2 to 6 <code>options</code>.</li>
              <li><code>answer</code> is the position of the correct option <strong>counting from 0</strong>: first option = 0, second = 1, and so on.</li>
              <li>Optional: <code>marks</code> (default 1, no negative marking) and <code>explanation</code> (shown in the result).</li>
            </ul>
          </details>
          <details class="mb-2">
            <summary class="fw-semibold">Coding question</summary>
            <ul class="ps-3 mb-0 mt-1">
              <li><code>"type": "CODING"</code>, <code>title</code>, <code>text</code> (say the input and output format clearly), optional <code>marks</code> (default 10).</li>
              <li><code>samples</code>: examples students see and can run. <code>tests</code>: hidden tests used for marks (at least one), each <code>{{ '{' }} "input": ..., "output": ... {{ '}' }}</code>.</li>
              <li>Students can answer in Python, Java, C++ or JavaScript. Optional <code>starterCode</code> per language: <code>python</code>, <code>java</code>, <code>cpp</code>, <code>javascript</code>.</li>
              <li>Marks are given in proportion to hidden tests passed. Extra spaces at line ends are ignored.</li>
            </ul>
          </details>
          <details class="mb-2">
            <summary class="fw-semibold">Written (descriptive) question</summary>
            <ul class="ps-3 mb-0 mt-1">
              <li><code>"type": "TEXT"</code>, <code>text</code>, optional <code>title</code> and <code>marks</code> (default 5).</li>
              <li>Students type the answer (pasting is off). A teacher marks it in <strong>Monitor → Mark</strong>; <code>explanation</code> is shown to the teacher as the model answer.</li>
            </ul>
          </details>
          <details class="mb-2">
            <summary class="fw-semibold">Question pool</summary>
            <ul class="ps-3 mb-0 mt-1">
              <li>Add <code>"pick": 10</code> to a section: each student gets 10 random questions of that section. All its questions need the same marks.</li>
            </ul>
          </details>
          <details>
            <summary class="fw-semibold">Example file</summary>
            <pre class="mt-example mt-1"><code>{{ example }}</code></pre>
          </details>
          <p class="text-body-secondary mt-2 mb-0">Students never see the answers or hidden tests during the test. Questions are numbered automatically.</p>
        </div>
      </div>
    </div>

    <div class="table-responsive bg-body border rounded-3">
      <table class="table align-middle small mb-0">
        <thead><tr><th>Test</th><th class="d-none d-md-table-cell">Questions</th><th>Students</th><th class="d-none d-sm-table-cell">Average</th><th>Status</th>
          @if (college.canWrite()) { <th></th> }</tr></thead>
        <tbody>
          @for (t of pageRows(); track t.id) {
            <tr>
              <td><strong>{{ t.title }}</strong>@if (t.companyTag) { <span class="badge text-bg-light border ms-1">{{ t.companyTag }}</span> }
                @if (t.exam?.mode === 'EXAM') { <span class="badge ms-1" style="background:#e0e7ff;color:#3730a3">Exam</span> }
                @else { <span class="badge text-bg-light border ms-1">Practice</span> }<br>
                <span class="text-body-secondary">{{ t.durationMinutes }} min · {{ t.totalMarks }} marks · uploaded {{ t.createdAt | date: 'mediumDate' }}</span>
                @if (t.exam?.mode === 'EXAM') {
                  <br><span class="text-body-secondary"><i class="bi bi-calendar-event me-1"></i>{{ t.exam?.startsAt | date: 'd MMM, h:mm a' }} – {{ t.exam?.endsAt | date: 'd MMM, h:mm a' }}
                    · results {{ t.exam?.resultsReleased ? 'released' : 'hidden' }}</span>
                }</td>
              <td class="d-none d-md-table-cell">{{ t.questionCount - t.codingCount }} MCQ/written · {{ t.codingCount }} coding</td>
              <td>{{ t.students ?? 0 }}<span class="text-body-secondary d-none d-lg-inline"> ({{ t.attempts }} attempts)</span></td>
              <td class="d-none d-sm-table-cell">{{ t.averagePercent === null || t.averagePercent === undefined ? '-' : t.averagePercent + '%' }}</td>
              <td>@if (t.published) { <span class="badge text-bg-success">Published</span> } @else { <span class="badge text-bg-secondary">Draft</span> }</td>
              @if (college.canWrite()) {
                <td class="text-end text-nowrap">
                  <button class="btn btn-sm btn-outline-primary me-1" (click)="monitorFor.set(t)" title="Live monitor, marking and marks register"><i class="bi bi-display me-1"></i>Monitor</button>
                  <button class="btn btn-sm btn-outline-secondary me-1" (click)="settingsFor.set(t)" title="Practice or exam settings"><i class="bi bi-gear me-1"></i>Settings</button>
                  <button class="btn btn-sm me-1" [class.btn-success]="!t.published" [class.btn-outline-secondary]="t.published" (click)="toggle(t)">
                    {{ t.published ? 'Unpublish' : 'Publish' }}</button>
                  <button class="btn btn-sm btn-outline-secondary me-1" (click)="download(t)" title="Download JSON" [attr.aria-label]="'Download ' + t.title"><i class="bi bi-download"></i></button>
                  <button class="btn btn-sm btn-outline-danger" (click)="remove(t)" title="Delete" [attr.aria-label]="'Delete ' + t.title"><i class="bi bi-trash"></i></button>
                </td>
              }
            </tr>
          } @empty {
            <tr><td [attr.colspan]="college.canWrite() ? 6 : 5"><pm-empty-state icon="bi-journal-code" title="No tests uploaded yet"
              text="Download the template, edit it and upload it here." /></td></tr>
          }
        </tbody>
      </table>
    </div>
    <pm-pager [total]="rows().length" [(page)]="page" [(size)]="size" [sizes]="[10, 25, 50]" />
    }
    <pm-exam-settings [test]="settingsFor()" (saved)="settingsFor.set(null); load()" (closed)="settingsFor.set(null)" />`,
  styles: [`
    .mt-steps { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: .5rem 1.25rem; font-size: .85rem; }
    .mt-steps li { display: flex; align-items: center; gap: .4rem; }
    .mt-steps span { width: 1.5rem; height: 1.5rem; border-radius: 50%; display: grid; place-items: center; font-size: .75rem; font-weight: 700;
      background: var(--bs-primary); color: #fff; }
    /* Field names are case-sensitive JSON keys: never let the global table header style uppercase them. */
    .mt-fmt th { white-space: nowrap; width: 1%; padding-right: 1rem; text-transform: none !important; letter-spacing: normal !important;
      font-size: .8rem; font-weight: 600; }
    .mt-example { max-height: 320px; overflow: auto; background: var(--bs-tertiary-bg); border-radius: .5rem; padding: .75rem; font-size: .75rem; }
    summary { cursor: pointer; }
  `],
})
export class CollegeMockTests implements OnInit {
  private prep = inject(PrepService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly example = EXAMPLE;
  /** Tests (upload / publish) or Student attempts (scores and proof of leaving a test). */
  tab = signal<'tests' | 'attempts'>('tests');
  rows = signal<AdminTest[]>([]);
  settingsFor = signal<AdminTest | null>(null);
  page = signal(0);
  size = signal(10);
  pageRows = computed(() => pageOf(this.rows(), this.page(), this.size()));
  monitorFor = signal<AdminTest | null>(null);
  busy = signal(false);
  error = signal('');

  ngOnInit(): void { this.load(); }
  load(): void { this.prep.collegeList().subscribe(r => this.rows.set(r)); }

  upload(f: File): void {
    this.busy.set(true);
    this.error.set('');
    this.prep.collegeUpload(f).subscribe({
      next: t => { this.busy.set(false); this.toast.success(`"${t.title}" uploaded as a draft. Check it, then press Publish.`); this.load(); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  async toggle(t: AdminTest): Promise<void> {
    if (!t.published) {
      const r = await this.confirm.ask({
        title: `Publish "${t.title}"?`, confirmText: 'Publish', tone: 'primary',
        message: t.exam?.mode === 'EXAM'
          ? `The exam's students see it under College tests now and can start it between ${new Date(t.exam.startsAt!).toLocaleString()} and ${new Date(t.exam.endsAt!).toLocaleString()} (one attempt).`
          : `All your college's students will see it under College tests and can start it right away (${t.durationMinutes} minutes).`,
      });
      if (!r.ok) return;
    }
    this.prep.collegePublish(t.id, !t.published).subscribe(() => {
      this.toast.success(t.published ? 'Hidden from your students' : 'Published to your students');
      this.load();
    });
  }

  download(t: AdminTest): void { this.prep.collegeDownload(t).subscribe(); }

  async remove(t: AdminTest): Promise<void> {
    const r = await this.confirm.ask({ title: `Delete "${t.title}"?`, tone: 'danger', confirmText: 'Delete',
      message: `${t.attempts} attempt(s) by your students and their results are deleted too. This cannot be undone.` });
    if (r.ok) this.prep.collegeDelete(t.id).subscribe(() => { this.toast.success('Test deleted'); this.load(); });
  }
}
