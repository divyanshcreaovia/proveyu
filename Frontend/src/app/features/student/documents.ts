import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentPortalService } from '../../services/student-portal.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../shared/ui/confirm-dialog';
import { DOC_TYPE_LABELS, DocType, StudentDocument } from '../../core/models/college.models';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../shared/ui/states';
import { FileUpload } from '../../shared/ui/file-upload';

/**
 * Document locker: upload once, download any time. The same files are visible to the placement cell, which verifies them,
 * so students don't have to re-send marksheets for every drive.
 */
@Component({
  selector: 'pm-student-documents',
  imports: [DatePipe, DecimalPipe, FormsModule, StatusBadge, EmptyState, Skeleton, FileUpload],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './student-pages.scss',
  template: `
    <div class="row g-3">
      <div class="col-lg-8 order-2 order-lg-1">
        <div class="st-filters">
          <select class="form-select form-select-sm" aria-label="Filter by type" [ngModel]="filter()" (ngModelChange)="filter.set($event)">
            <option value="">All documents ({{ docs().length }})</option>
            @for (t of types; track t) { <option [value]="t">{{ labels[t] }}</option> }
          </select>
          <span class="small st-muted ms-auto">{{ verified() }} of {{ docs().length }} verified by your placement cell</span>
        </div>
        @if (loading()) { <pm-skeleton [rows]="5" /> } @else {
          @for (d of shown(); track d.id) {
            <div class="st-row">
              <i class="bi fs-4" [class]="icon(d)" aria-hidden="true"></i>
              <span class="flex-grow-1 small min-w-0"><strong>{{ labels[d.docType] }}</strong>@if (d.label) { · {{ d.label }} }<br>
                <span class="st-muted text-truncate d-inline-block" style="max-width: 100%">{{ d.fileName }} · {{ d.sizeBytes / 1024 | number: '1.0-0' }} KB · {{ d.createdAt | date: 'mediumDate' }}</span>
                @if (d.remarks) { <br><span class="text-danger">{{ d.remarks }}</span> }</span>
              <pm-status [value]="d.verificationStatus" />
              <button class="btn btn-sm btn-outline-secondary" (click)="portal.previewDocument(d.id)" aria-label="View"><i class="bi bi-eye"></i></button>
              <button class="btn btn-sm btn-outline-secondary" (click)="download(d)" aria-label="Download"><i class="bi bi-download"></i></button>
              @if (d.verificationStatus !== 'VERIFIED') {
                <button class="btn btn-sm btn-outline-danger" (click)="remove(d)" aria-label="Delete"><i class="bi bi-trash"></i></button> }
            </div>
          } @empty { <pm-empty-state icon="bi-folder2-open" title="Your locker is empty" text="Upload your resume, marksheets and certificates once - use them for every drive." /> }
        }
      </div>
      <div class="col-lg-4 order-1 order-lg-2">
        <div class="st-card">
          <h2 class="h6 st-h">Add a document</h2>
          <label class="form-label small fw-semibold" for="dl-type">Type</label>
          <select id="dl-type" class="form-select form-select-sm mb-2" [(ngModel)]="docType">
            @for (t of types; track t) { <option [value]="t">{{ labels[t] }}</option> }
          </select>
          <label class="form-label small fw-semibold" for="dl-label">Name (optional)</label>
          <input id="dl-label" class="form-control form-control-sm mb-3" maxlength="150" [(ngModel)]="label" placeholder="e.g. Semester 5, AWS certificate" />
          <pm-file-upload accept=".pdf,.doc,.docx,.png,.jpg,.jpeg" [maxMb]="10" [disabled]="busy()" hint="PDF, Word or image · max 10 MB" (fileSelected)="upload($event)" />
          <p class="small st-muted mt-2 mb-0"><i class="bi bi-shield-check"></i> Only you and your placement cell can see these files.
            Verified documents can only be removed by the placement cell.</p>
        </div>
      </div>
    </div>`,
})
export class StudentDocuments implements OnInit {
  portal = inject(StudentPortalService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  labels = DOC_TYPE_LABELS;
  types = Object.keys(DOC_TYPE_LABELS) as DocType[];
  docType: DocType = 'RESUME';
  label = '';
  busy = signal(false);
  loading = signal(true);
  filter = signal<DocType | ''>('');
  docs = signal<StudentDocument[]>([]);
  shown = computed(() => this.docs().filter(d => !this.filter() || d.docType === this.filter()));
  verified = computed(() => this.docs().filter(d => d.verificationStatus === 'VERIFIED').length);

  ngOnInit(): void { this.load(); }
  load(): void { this.portal.documents().subscribe({ next: d => { this.docs.set(d); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  icon(d: StudentDocument): string {
    return d.contentType.startsWith('image/') ? 'bi-file-earmark-image text-info' : d.contentType.includes('pdf') ? 'bi-file-earmark-pdf text-danger' : 'bi-file-earmark-text text-primary';
  }
  upload(f: File): void {
    this.busy.set(true);
    this.portal.upload(this.docType, f, this.label).subscribe({
      next: () => { this.busy.set(false); this.label = ''; this.toast.success('Saved to your locker'); this.load(); },
      error: () => this.busy.set(false),
    });
  }
  download(d: StudentDocument): void { this.portal.downloadDocument(d).subscribe(); }
  async remove(d: StudentDocument): Promise<void> {
    const r = await this.confirm.ask({ title: 'Delete document?', message: `${this.labels[d.docType]} · ${d.fileName}`, tone: 'danger', confirmText: 'Delete' });
    if (r.ok) this.portal.deleteDocument(d.id).subscribe(() => { this.toast.success('Document deleted'); this.load(); });
  }
}
