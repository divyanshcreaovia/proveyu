import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StudentService } from '../../../services/student.service';
import { CollegeService } from '../../../services/college.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { DOC_TYPE_LABELS, DocType, StudentDetail, StudentStatus } from '../../../core/models/college.models';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { FileUpload } from '../../../shared/ui/file-upload';
import { Skeleton } from '../../../shared/ui/states';

/** Student profile drawer: academics, skills, documents (verification), PROVEYU passport, applications and offers. */
@Component({
  selector: 'pm-student-drawer',
  imports: [Modal, StatusBadge, FileUpload, Skeleton, DatePipe, DecimalPipe, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="!!studentId()" variant="drawer" [title]="d()?.student?.fullName ?? 'Student'" (closed)="closed.emit()">
      @if (!d()) { <pm-skeleton [rows]="10" /> }
      @else {
        @let s = d()!.student;
        <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
          <span class="badge text-bg-light border">{{ s.rollNumber }}</span><pm-status [value]="s.status" />
          <span class="small text-body-secondary">{{ s.branchCode }} · Batch {{ s.batchYear }} · {{ s.email }}</span>
        </div>
        @if (college.canWrite()) {
          <div class="d-flex flex-wrap gap-2 mb-3">
            <button class="btn btn-sm btn-outline-primary" (click)="edit.emit()"><i class="bi bi-pencil"></i> Edit</button>
            @if (s.status !== 'DEBARRED') { <button class="btn btn-sm btn-outline-danger" (click)="setStatus('DEBARRED')">Debar</button> }
            @if (s.status !== 'OPTED_OUT') { <button class="btn btn-sm btn-outline-secondary" (click)="setStatus('OPTED_OUT')">Mark opted-out</button> }
            @if (s.status === 'DEBARRED' || s.status === 'OPTED_OUT') { <button class="btn btn-sm btn-outline-success" (click)="setStatus('ACTIVE')">Re-activate</button> }
          </div>
        }
        <ul class="nav nav-tabs small mb-3" role="tablist">
          @for (t of tabs; track t) {
            <li class="nav-item"><button class="nav-link" role="tab" [class.active]="tab() === t" [attr.aria-selected]="tab() === t" (click)="tab.set(t)">{{ t }}</button></li>
          }
        </ul>
        @switch (tab()) {
          @case ('Academics') {
            <dl class="row small pm-sd-dl">
              <dt class="col-5">CGPA</dt><dd class="col-7">{{ s.cgpa ?? '-' }}</dd>
              <dt class="col-5">10th / 12th</dt><dd class="col-7">{{ s.tenthPercent ?? '-' }}% / {{ s.twelfthPercent ?? '-' }}%</dd>
              <dt class="col-5">Active backlogs</dt><dd class="col-7">{{ s.activeBacklogs }}</dd>
              <dt class="col-5">Gap years</dt><dd class="col-7">{{ s.gapYears }}</dd>
              <dt class="col-5">Degree</dt><dd class="col-7">{{ s.degree ?? '-' }}</dd>
              <dt class="col-5">Phone</dt><dd class="col-7">{{ s.phone ?? '-' }}</dd>
              <dt class="col-5">Highest CTC</dt><dd class="col-7">{{ s.highestCtcLpa ? s.highestCtcLpa + ' LPA' : '-' }}</dd>
              <dt class="col-5">Skills</dt><dd class="col-7">@for (k of s.skills; track k) { <span class="badge rounded-pill text-bg-secondary me-1">{{ k }}</span> } @empty { - }</dd>
              <dt class="col-5">Links</dt><dd class="col-7">
                @if (s.linkedinUrl) { <a [href]="s.linkedinUrl" target="_blank" rel="noopener">LinkedIn</a> }
                @if (s.githubUrl) { · <a [href]="s.githubUrl" target="_blank" rel="noopener">GitHub</a> } @if (!s.linkedinUrl && !s.githubUrl) { - }</dd>
              <dt class="col-5">Portal account</dt><dd class="col-7">{{ s.hasAccount ? 'Linked' : 'Not linked' }}</dd>
            </dl>
          }
          @case ('Passport') {
            @let p = d()!.passport;
            <div class="mb-3"><div class="d-flex justify-content-between small mb-1"><span>Profile completeness</span><strong>{{ p.profileCompleteness }}%</strong></div>
              <div class="progress" role="progressbar" [attr.aria-valuenow]="p.profileCompleteness" aria-valuemin="0" aria-valuemax="100">
                <div class="progress-bar" [style.width.%]="p.profileCompleteness"></div></div>
              @if (p.missingProfileItems.length) { <div class="small text-body-secondary mt-1">Missing: {{ p.missingProfileItems.join(', ') }}</div> }
            </div>
            <h3 class="h6">PROVEYU verified scores @if (p.averagePercent !== null && p.averagePercent !== undefined) { <small class="text-body-secondary">(avg {{ p.averagePercent | number: '1.0-1' }}%)</small> }</h3>
            @for (x of p.verifiedScores; track $index) {
              <div class="d-flex justify-content-between small border-bottom py-1"><span>{{ x.skill }}</span>
                <span>{{ x.score }}/{{ x.maxScore }} <pm-status [value]="x.passed ? 'PASS' : 'FAIL'" /></span></div>
            } @empty { <p class="small text-body-secondary">No PROVEYU assessments taken yet. Encourage the student to book a slot from the candidate portal.</p> }
            @if (p.headline) { <p class="small mt-3 mb-1"><strong>Headline:</strong> {{ p.headline }}</p> }
            @if (p.resumeUrl) { <p class="small"><i class="bi bi-file-earmark-person"></i> Resume available in the PROVEYU candidate profile (resume builder).</p> }
          }
          @case ('Documents') {
            @for (doc of d()!.documents; track doc.id) {
              <div class="pm-sd-doc">
                <div class="min-w-0"><div class="small fw-semibold text-truncate">{{ label(doc.docType) }}@if (doc.label) { ({{ doc.label }}) } · {{ doc.fileName }}</div>
                  <div class="small text-body-secondary">{{ doc.createdAt | date: 'mediumDate' }} @if (doc.remarks) { · {{ doc.remarks }} }</div></div>
                <pm-status [value]="doc.verificationStatus" />
                <div class="btn-group btn-group-sm">
                  <button class="btn btn-outline-secondary" (click)="students.openDocument(doc.id)" aria-label="View document"><i class="bi bi-eye"></i></button>
                  @if (college.canWrite()) {
                    <button class="btn btn-outline-success" (click)="verify(doc.id, 'VERIFIED')" aria-label="Verify"><i class="bi bi-check2"></i></button>
                    <button class="btn btn-outline-danger" (click)="verify(doc.id, 'REJECTED')" aria-label="Reject"><i class="bi bi-x"></i></button>
                  }
                </div>
              </div>
            } @empty { <p class="small text-body-secondary">No documents uploaded.</p> }
            @if (college.canWrite()) {
              <div class="mt-3 border-top pt-3">
                <label for="sd-doctype" class="form-label small fw-semibold">Upload document</label>
                <select id="sd-doctype" class="form-select form-select-sm mb-2" [(ngModel)]="docType">
                  @for (t of docTypes; track t) { <option [value]="t">{{ label(t) }}</option> }
                </select>
                <pm-file-upload accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" (fileSelected)="upload($event)" />
              </div>
            }
          }
          @case ('Placement') {
            <h3 class="h6">Applications</h3>
            @for (a of d()!.applications; track a.applicationId) {
              <div class="d-flex justify-content-between small border-bottom py-2"><a [routerLink]="['/college/drives', a.driveId]">{{ a.companyName }} · {{ a.driveTitle }}</a>
                <pm-status [value]="a.status" /></div>
            } @empty { <p class="small text-body-secondary">No applications.</p> }
            <h3 class="h6 mt-3">Offers</h3>
            @for (o of d()!.offers; track o.offerId) {
              <div class="d-flex justify-content-between small border-bottom py-2"><span>{{ o.companyName }} · {{ o.roleTitle }} · {{ o.ctcLpa }} LPA</span><pm-status [value]="o.status" /></div>
            } @empty { <p class="small text-body-secondary">No offers.</p> }
          }
        }
      }
    </pm-modal>`,
  styles: [`.pm-sd-dl dt { font-weight: 500; color: var(--bs-secondary-color); }
    .pm-sd-doc { display: flex; align-items: center; gap: .75rem; padding: .5rem 0; border-bottom: 1px solid var(--bs-border-color); }
    .pm-sd-doc > div:first-child { flex: 1; }`],
})
export class StudentDrawer {
  students = inject(StudentService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  studentId = input<string | null>(null);
  closed = output<void>();
  edit = output<void>();
  changed = output<void>();
  d = signal<StudentDetail | null>(null);
  tabs = ['Academics', 'Passport', 'Documents', 'Placement'];
  tab = signal('Academics');
  docTypes = Object.keys(DOC_TYPE_LABELS) as DocType[];
  docType: DocType = 'RESUME';

  constructor() {
    effect(() => {
      const id = this.studentId();
      this.d.set(null);
      if (id) this.reload(id);
    });
  }

  reload(id = this.studentId()!): void { this.students.get(id).subscribe(d => this.d.set(d)); }

  label(t: string): string {
    return DOC_TYPE_LABELS[t as DocType] ?? t;
  }

  async setStatus(status: StudentStatus): Promise<void> {
    const r = await this.confirm.ask({ title: `Set status to ${status.replace('_', ' ').toLowerCase()}?`, message: 'This is recorded in the audit log.',
      inputLabel: 'Reason', inputRequired: true, tone: status === 'DEBARRED' ? 'danger' : 'primary' });
    if (!r.ok) return;
    this.students.changeStatus(this.studentId()!, status, r.value).subscribe(() => { this.toast.success('Status updated'); this.reload(); this.changed.emit(); });
  }

  async verify(docId: string, status: 'VERIFIED' | 'REJECTED'): Promise<void> {
    let remarks: string | undefined;
    if (status === 'REJECTED') {
      const r = await this.confirm.ask({ title: 'Reject document?', message: 'The student will need to upload it again.', inputLabel: 'Reason', inputRequired: true, tone: 'danger' });
      if (!r.ok) return;
      remarks = r.value;
    }
    this.students.verifyDocument(docId, status, remarks).subscribe(() => { this.toast.success('Document ' + status.toLowerCase()); this.reload(); });
  }

  upload(file: File): void {
    this.students.uploadDocument(this.studentId()!, this.docType, file).subscribe(() => { this.toast.success('Document uploaded'); this.reload(); });
  }
}
