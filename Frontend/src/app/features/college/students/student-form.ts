import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CollegeService } from '../../../services/college.service';
import { StudentService } from '../../../services/student.service';
import { AppError } from '../../../core/api';
import { Student } from '../../../core/models/college.models';
import { Modal } from '../../../shared/ui/modal';

/** Add / edit student (typed reactive form, server field errors mapped back onto controls). */
@Component({
  selector: 'pm-student-form',
  imports: [ReactiveFormsModule, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="open()" [title]="student() ? 'Edit ' + student()!.fullName : 'Add student'" size="lg" (closed)="closed.emit()">
      <form [formGroup]="form" id="pm-sf-form" (ngSubmit)="save()" novalidate class="row g-3">
        @for (f of fields; track f.key) {
          <div [class]="f.col">
            <label class="form-label small fw-semibold" [for]="'sf-' + f.key">{{ f.label }}@if (f.required) { * }</label>
            @if (f.key === 'branchId') {
              <select [id]="'sf-' + f.key" class="form-select" [formControlName]="f.key" [class.is-invalid]="bad(f.key)">
                <option value="">Select</option>
                @for (b of college.branches(); track b.id) { <option [value]="b.id">{{ b.code }} - {{ b.name }}</option> }
              </select>
            } @else if (f.key === 'gender') {
              <select [id]="'sf-' + f.key" class="form-select" [formControlName]="f.key">
                <option value="">-</option><option>Male</option><option>Female</option><option>Other</option>
              </select>
            } @else {
              <input [id]="'sf-' + f.key" class="form-control" [type]="f.type ?? 'text'" [formControlName]="f.key" [attr.step]="f.step ?? null"
                     [class.is-invalid]="bad(f.key)" />
            }
            <div class="invalid-feedback">{{ serverErrors()[f.key] || 'Invalid value' }}</div>
          </div>
        }
        @if (error()) { <div class="col-12"><div class="alert alert-danger small mb-0" role="alert">{{ error() }}</div></div> }
      </form>
      <div pmFooter>
        <button type="button" class="btn btn-outline-secondary" (click)="closed.emit()">Cancel</button>
        <button type="submit" form="pm-sf-form" class="btn btn-primary" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Save</button>
      </div>
    </pm-modal>`,
})
export class StudentForm {
  college = inject(CollegeService);
  private students = inject(StudentService);
  open = input(false);
  student = input<Student | null>(null);
  closed = output<void>();
  saved = output<Student>();
  busy = signal(false);
  error = signal('');
  serverErrors = signal<Record<string, string>>({});

  fields: { key: string; label: string; col: string; type?: string; step?: string; required?: boolean }[] = [
    { key: 'rollNumber', label: 'Roll number', col: 'col-md-4', required: true },
    { key: 'firstName', label: 'First name', col: 'col-md-4', required: true },
    { key: 'lastName', label: 'Last name', col: 'col-md-4' },
    { key: 'email', label: 'E-mail', col: 'col-md-6', type: 'email', required: true },
    { key: 'phone', label: 'Phone', col: 'col-md-3', type: 'tel' },
    { key: 'gender', label: 'Gender', col: 'col-md-3' },
    { key: 'branchId', label: 'Branch', col: 'col-md-4', required: true },
    { key: 'batchYear', label: 'Batch (grad year)', col: 'col-md-4', type: 'number', required: true },
    { key: 'degree', label: 'Degree', col: 'col-md-4' },
    { key: 'cgpa', label: 'CGPA', col: 'col-md-3', type: 'number', step: '0.01' },
    { key: 'tenthPercent', label: '10th %', col: 'col-md-3', type: 'number', step: '0.1' },
    { key: 'twelfthPercent', label: '12th %', col: 'col-md-3', type: 'number', step: '0.1' },
    { key: 'activeBacklogs', label: 'Active backlogs', col: 'col-md-3', type: 'number' },
    { key: 'gapYears', label: 'Gap years', col: 'col-md-3', type: 'number' },
    { key: 'dob', label: 'Date of birth', col: 'col-md-3', type: 'date' },
    { key: 'skills', label: 'Skills (comma separated)', col: 'col-md-6' },
    { key: 'linkedinUrl', label: 'LinkedIn URL', col: 'col-md-6', type: 'url' },
    { key: 'githubUrl', label: 'GitHub URL', col: 'col-md-6', type: 'url' },
  ];

  form = inject(FormBuilder).group({
    rollNumber: ['', [Validators.required, Validators.maxLength(50)]],
    firstName: ['', Validators.required], lastName: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(/^[0-9+\- ]{7,20}$/)], gender: [''], dob: [''],
    branchId: ['', Validators.required],
    batchYear: [new Date().getFullYear() + 1 as number | null, [Validators.required, Validators.min(2000), Validators.max(2100)]],
    degree: ['B.Tech'],
    cgpa: [null as number | null, [Validators.min(0), Validators.max(10)]],
    tenthPercent: [null as number | null, [Validators.min(0), Validators.max(100)]],
    twelfthPercent: [null as number | null, [Validators.min(0), Validators.max(100)]],
    activeBacklogs: [0 as number | null, Validators.min(0)], gapYears: [0 as number | null, Validators.min(0)],
    skills: [''], linkedinUrl: [''], githubUrl: [''],
  });

  constructor() {
    effect(() => {
      const s = this.student();
      if (!this.open()) return;
      this.error.set(''); this.serverErrors.set({});
      if (s) {
        this.form.reset({ ...s, lastName: s.lastName ?? '', phone: s.phone ?? '', gender: s.gender ?? '', dob: s.dob ?? '', branchId: s.branchId ?? '',
          degree: s.degree ?? '', cgpa: s.cgpa ?? null, tenthPercent: s.tenthPercent ?? null, twelfthPercent: s.twelfthPercent ?? null,
          skills: s.skills.join(', '), linkedinUrl: s.linkedinUrl ?? '', githubUrl: s.githubUrl ?? '' });
      } else {
        this.form.reset({ batchYear: new Date().getFullYear() + 1, degree: 'B.Tech', activeBacklogs: 0, gapYears: 0, branchId: '' });
      }
    });
  }

  bad(key: string): boolean {
    const c = this.form.get(key);
    return (!!c && c.touched && c.invalid) || !!this.serverErrors()[key];
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const body = {
      rollNumber: v.rollNumber!, firstName: v.firstName!, lastName: v.lastName || null, email: v.email!, phone: v.phone || null,
      gender: v.gender || null, dob: v.dob || null, branchId: v.branchId!, batchYear: Number(v.batchYear), degree: v.degree || null,
      cgpa: v.cgpa, tenthPercent: v.tenthPercent, twelfthPercent: v.twelfthPercent, activeBacklogs: v.activeBacklogs ?? 0, gapYears: v.gapYears ?? 0,
      skills: (v.skills ?? '').split(',').map(x => x.trim()).filter(Boolean), linkedinUrl: v.linkedinUrl || null, githubUrl: v.githubUrl || null,
    };
    this.busy.set(true);
    const s = this.student();
    (s ? this.students.update(s.id, body) : this.students.create(body)).subscribe({
      next: r => { this.busy.set(false); this.saved.emit(r); },
      error: (e: AppError) => { this.busy.set(false); this.serverErrors.set(e.fieldErrors); this.error.set(Object.keys(e.fieldErrors).length ? 'Please fix the highlighted fields.' : e.message); },
    });
  }
}
