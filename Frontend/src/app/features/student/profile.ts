import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StudentPortalService } from '../../services/student-portal.service';
import { ToastService } from '../../services/toast.service';
import { StatusBadge } from '../../shared/ui/status-badge';

const URL_RE = /^$|^https?:\/\/\S+$/;

/** Academic record (read-only, owned by the placement cell) + contact details and skills the student maintains. */
@Component({
  selector: 'pm-student-profile',
  imports: [ReactiveFormsModule, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './student-pages.scss',
  template: `
    @let p = portal.me()!;
    <div class="row g-3">
      <div class="col-lg-5">
        <section class="st-card h-100">
          <h2 class="h6 st-h mb-3">Academic record</h2>
          <dl class="row small mb-2">
            <dt class="col-5 st-muted fw-normal">Name</dt><dd class="col-7">{{ p.name }}</dd>
            <dt class="col-5 st-muted fw-normal">Roll number</dt><dd class="col-7">{{ p.rollNumber }}</dd>
            <dt class="col-5 st-muted fw-normal">E-mail</dt><dd class="col-7 text-break">{{ p.email }}</dd>
            <dt class="col-5 st-muted fw-normal">College</dt><dd class="col-7">{{ p.collegeName }}</dd>
            <dt class="col-5 st-muted fw-normal">Programme</dt><dd class="col-7">{{ p.degree ?? '-' }} · {{ p.branch ?? '-' }}</dd>
            <dt class="col-5 st-muted fw-normal">Batch</dt><dd class="col-7">{{ p.batchYear }}</dd>
            <dt class="col-5 st-muted fw-normal">CGPA</dt><dd class="col-7">{{ p.cgpa ?? '-' }}</dd>
            <dt class="col-5 st-muted fw-normal">10th / 12th</dt><dd class="col-7">{{ p.tenthPercent ?? '-' }}% / {{ p.twelfthPercent ?? '-' }}%</dd>
            <dt class="col-5 st-muted fw-normal">Active backlogs</dt><dd class="col-7">{{ p.activeBacklogs }}</dd>
            <dt class="col-5 st-muted fw-normal">Placement status</dt><dd class="col-7"><pm-status [value]="p.status" /></dd>
          </dl>
          <p class="small st-muted mb-0"><i class="bi bi-info-circle"></i> Something wrong here? Ask your placement cell to correct it.</p>
        </section>
      </div>
      <div class="col-lg-7">
        <form class="st-card" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <h2 class="h6 st-h mb-3">Contact & skills</h2>
          <div class="mb-3">
            <label for="sp-phone" class="form-label small fw-semibold">Phone</label>
            <input id="sp-phone" class="form-control" formControlName="phone" inputmode="tel" autocomplete="tel"
                   [class.is-invalid]="form.controls.phone.invalid" />
            <div class="invalid-feedback">7–15 digits.</div>
          </div>
          <div class="mb-3">
            <label for="sp-skills" class="form-label small fw-semibold">Skills</label>
            <input id="sp-skills" class="form-control" formControlName="skills" placeholder="Java, SQL, React" />
            <div class="form-text">Comma separated. Companies' required skills are matched against these.</div>
          </div>
          <div class="mb-3">
            <label for="sp-li" class="form-label small fw-semibold">LinkedIn URL</label>
            <input id="sp-li" type="url" class="form-control" formControlName="linkedinUrl" placeholder="https://linkedin.com/in/…" [class.is-invalid]="form.controls.linkedinUrl.invalid" />
            <div class="invalid-feedback">Must start with https://</div>
          </div>
          <div class="mb-4">
            <label for="sp-gh" class="form-label small fw-semibold">GitHub / portfolio URL</label>
            <input id="sp-gh" type="url" class="form-control" formControlName="githubUrl" placeholder="https://github.com/…" [class.is-invalid]="form.controls.githubUrl.invalid" />
            <div class="invalid-feedback">Must start with https://</div>
          </div>
          <button class="btn btn-primary" [disabled]="busy() || form.invalid || form.pristine">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } Save changes</button>
        </form>
      </div>
    </div>`,
})
export class StudentProfile implements OnInit {
  portal = inject(StudentPortalService);
  private toast = inject(ToastService);
  busy = signal(false);
  form = inject(FormBuilder).nonNullable.group({
    phone: ['', Validators.pattern(/^$|^[+0-9 ()-]{7,15}$/)],
    skills: [''],
    linkedinUrl: ['', [Validators.maxLength(300), Validators.pattern(URL_RE)]],
    githubUrl: ['', [Validators.maxLength(300), Validators.pattern(URL_RE)]],
  });

  ngOnInit(): void { this.reset(); }
  reset(): void {
    const p = this.portal.me()!;
    this.form.reset({ phone: p.phone ?? '', skills: p.skills.join(', '), linkedinUrl: p.linkedinUrl ?? '', githubUrl: p.githubUrl ?? '' });
  }
  save(): void {
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.portal.updateProfile({ ...v, skills: v.skills.split(',').map(s => s.trim()).filter(Boolean) }).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Profile updated'); this.reset(); },
      error: () => this.busy.set(false),
    });
  }
}
