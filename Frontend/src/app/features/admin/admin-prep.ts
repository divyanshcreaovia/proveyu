import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminTest, PrepService } from '../../services/prep.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../shared/ui/confirm-dialog';
import { FileUpload } from '../../shared/ui/file-upload';
import { EmptyState } from '../../shared/ui/states';
import { AppError } from '../../core/api';

/** Platform ADMIN: upload mock tests (JSON) and publish them to every student's Preparation tab. */
@Component({
  selector: 'pm-admin-prep',
  imports: [DatePipe, RouterLink, FileUpload, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container py-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div class="d-flex align-items-center gap-3">
          <h1 class="h4 fw-bold m-0">Mock tests</h1>
          <a routerLink="/admin/colleges" class="small">College registrations</a>
        </div>
        <button class="btn btn-sm btn-outline-secondary" (click)="auth.logout()">Sign out</button>
      </div>

      <div class="row g-3 mb-3">
        <div class="col-lg-5">
          <div class="bg-body border rounded-3 p-3 h-100">
            <h2 class="h6 fw-bold">Upload a test</h2>
            <p class="small text-body-secondary">Upload one JSON file per test. It is saved as a draft; publish it when ready and every student sees it under Preparation.</p>
            <pm-file-upload accept=".json" [maxMb]="2" [disabled]="busy()" label="Choose a test JSON file or drag it here" (fileSelected)="upload($event)" />
            @if (error()) { <div class="alert alert-danger small mt-2 mb-0" role="alert">{{ error() }}</div> }
          </div>
        </div>
        <div class="col-lg-7">
          <div class="bg-body border rounded-3 p-3 h-100 small">
            <h2 class="h6 fw-bold">File format</h2>
            <p class="mb-2">Start from the sample: <a href="samples/mock-test-template.json" download="mock-test-template.json" class="fw-semibold">download template</a>
              (a complete 45-minute test with 17 MCQs and 2 coding problems).</p>
            <ul class="mb-0 ps-3">
              <li><code>title</code>, <code>durationMinutes</code> (5–300), optional <code>description</code> and <code>companyTag</code>.</li>
              <li><code>sections</code>: each has a <code>name</code> and <code>questions</code>.</li>
              <li>MCQ: <code>type: "MCQ"</code>, <code>text</code>, 2–6 <code>options</code>, <code>answer</code> = index of the correct option starting at 0, optional <code>marks</code> (default 1) and <code>explanation</code>.</li>
              <li>Coding: <code>type: "CODING"</code>, <code>title</code>, <code>text</code>, <code>marks</code> (default 10), <code>starterCode</code> for python / java / cpp / javascript,
                visible <code>samples</code> and hidden <code>tests</code> as <code>{{ '{' }}"input", "output"{{ '}' }}</code>. Marks are given in proportion to hidden tests passed.</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="table-responsive bg-body border rounded-3">
        <table class="table align-middle small mb-0">
          <thead><tr><th>Test</th><th class="d-none d-md-table-cell">Questions</th><th class="d-none d-md-table-cell">Attempts</th><th>Status</th><th></th></tr></thead>
          <tbody>
            @for (t of rows(); track t.id) {
              <tr>
                <td><strong>{{ t.title }}</strong>@if (t.companyTag) { <span class="badge text-bg-light border ms-1">{{ t.companyTag }}</span> }<br>
                  <span class="text-body-secondary">{{ t.durationMinutes }} min · {{ t.totalMarks }} marks · uploaded {{ t.createdAt | date: 'mediumDate' }}</span></td>
                <td class="d-none d-md-table-cell">{{ t.questionCount - t.codingCount }} MCQ · {{ t.codingCount }} coding</td>
                <td class="d-none d-md-table-cell">{{ t.attempts }}</td>
                <td>@if (t.published) { <span class="badge text-bg-success">Published</span> } @else { <span class="badge text-bg-secondary">Draft</span> }</td>
                <td class="text-end text-nowrap">
                  <button class="btn btn-sm me-1" [class.btn-success]="!t.published" [class.btn-outline-secondary]="t.published" (click)="toggle(t)">
                    {{ t.published ? 'Unpublish' : 'Publish' }}</button>
                  <button class="btn btn-sm btn-outline-secondary me-1" (click)="download(t)" title="Download JSON" aria-label="Download JSON"><i class="bi bi-download"></i></button>
                  <button class="btn btn-sm btn-outline-danger" (click)="remove(t)" title="Delete" aria-label="Delete"><i class="bi bi-trash"></i></button>
                </td>
              </tr>
            } @empty { <tr><td colspan="5"><pm-empty-state icon="bi-journal-code" title="No tests uploaded yet" text="Download the template, edit it and upload it here." /></td></tr> }
          </tbody>
        </table>
      </div>
    </div>`,
})
export class AdminPrep implements OnInit {
  private prep = inject(PrepService);
  auth = inject(AuthService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  rows = signal<AdminTest[]>([]);
  busy = signal(false);
  error = signal('');

  ngOnInit(): void { this.load(); }
  load(): void { this.prep.adminList().subscribe(r => this.rows.set(r)); }

  upload(f: File): void {
    this.busy.set(true);
    this.error.set('');
    this.prep.adminUpload(f).subscribe({
      next: t => { this.busy.set(false); this.toast.success(`"${t.title}" uploaded as a draft`); this.load(); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
  toggle(t: AdminTest): void {
    this.prep.adminPublish(t.id, !t.published).subscribe(() => { this.toast.success(t.published ? 'Hidden from students' : 'Published to students'); this.load(); });
  }
  download(t: AdminTest): void { this.prep.adminDownload(t).subscribe(); }
  async remove(t: AdminTest): Promise<void> {
    const r = await this.confirm.ask({ title: `Delete "${t.title}"?`, message: `${t.attempts} student attempt(s) and their results are deleted too.`, tone: 'danger', confirmText: 'Delete' });
    if (r.ok) this.prep.adminDelete(t.id).subscribe(() => { this.toast.success('Test deleted'); this.load(); });
  }
}
