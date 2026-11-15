import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PlacementDriveService } from '../../../services/placement-drive.service';
import { CompanyService } from '../../../services/company.service';
import { CollegeService } from '../../../services/college.service';
import { ToastService } from '../../../services/toast.service';
import { AppError } from '../../../core/api';
import { Company, DriveMode, DriveRequest, DriveType } from '../../../core/models/college.models';
import { Stepper } from '../../../shared/ui/stepper';
import { Skeleton } from '../../../shared/ui/states';

const csv = (s: string | null | undefined) => (s ?? '').split(',').map(x => x.trim()).filter(Boolean);

@Component({
  selector: 'pm-drive-form',
  imports: [ReactiveFormsModule, RouterLink, Stepper, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="id() ? ['/college/drives', id()] : ['/college/drives']" class="small"><i class="bi bi-arrow-left"></i> Back</a>
    <section class="pm-df-card mt-2">
      @if (loadingDrive()) { <pm-skeleton [rows]="8" /> } @else {
        <pm-stepper [steps]="steps" [current]="step()" (go)="step.set($event)" />
        <form [formGroup]="form" (ngSubmit)="next()" novalidate>
          @switch (step()) {
            @case (0) {
              <div class="row g-3">
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-co">Company *</label>
                  <select id="df-co" class="form-select" formControlName="companyId" [class.is-invalid]="bad('companyId')">
                    <option value="">Select company</option>@for (c of companies(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }</select>
                  @if (!companies().length) { <div class="form-text"><a routerLink="/college/companies">Add a company first</a></div> }</div>
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-type">Drive type *</label>
                  <select id="df-type" class="form-select" formControlName="driveType">@for (t of types; track t) { <option [value]="t">{{ t.replace('_', ' ') }}</option> }</select></div>
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-title">Drive title *</label>
                  <input id="df-title" class="form-control" formControlName="title" placeholder="e.g. Acme Graduate Hiring 2026" [class.is-invalid]="bad('title')" /></div>
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-role">Role *</label>
                  <input id="df-role" class="form-control" formControlName="roleTitle" [class.is-invalid]="bad('roleTitle')" /></div>
                <div class="col-12"><label class="form-label small fw-semibold" for="df-jd">Job description</label>
                  <textarea id="df-jd" rows="5" class="form-control" formControlName="jobDescription"></textarea></div>
                <div class="col-12"><label class="form-label small fw-semibold" for="df-skills">Skills (comma separated)</label>
                  <input id="df-skills" class="form-control" formControlName="skills" placeholder="Java, SQL, React" /></div>
              </div>
            }
            @case (1) {
              <div class="row g-3">
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-ctc">CTC (LPA)</label>
                  <input id="df-ctc" type="number" step="0.01" min="0" class="form-control" formControlName="ctcLpa" />
                  <div class="form-text">Tier: <strong>{{ autoTier() ?? 'n/a' }}</strong> (auto from CTC unless overridden)</div></div>
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-stip">Stipend / month (₹)</label>
                  <input id="df-stip" type="number" min="0" class="form-control" formControlName="stipendMonthly" /></div>
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-tier">Tier override</label>
                  <select id="df-tier" class="form-select" formControlName="tierId"><option value="">Auto</option>
                    @for (t of college.tiers(); track t.id) { <option [value]="t.id">{{ t.name }} (≥ {{ t.minCtcLpa }} LPA)</option> }</select></div>
                <div class="col-12"><label class="form-label small fw-semibold" for="df-break">CTC breakup</label>
                  <textarea id="df-break" rows="3" class="form-control" formControlName="ctcBreakup" placeholder="Fixed 8L + Variable 1L + Joining bonus 50K"></textarea></div>
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-loc">Job location</label><input id="df-loc" class="form-control" formControlName="location" /></div>
                <div class="col-md-3"><label class="form-label small fw-semibold" for="df-bond">Bond (months)</label><input id="df-bond" type="number" min="0" class="form-control" formControlName="bondMonths" /></div>
                <div class="col-md-5"><label class="form-label small fw-semibold" for="df-bondd">Bond details</label><input id="df-bondd" class="form-control" formControlName="bondDetails" /></div>
              </div>
            }
            @case (2) {
              <div class="row g-3" formGroupName="eligibility">
                <div class="col-6 col-md-4"><label class="form-label small fw-semibold" for="df-cgpa">Min CGPA</label><input id="df-cgpa" type="number" step="0.1" class="form-control" formControlName="minCgpa" /></div>
                <div class="col-6 col-md-4"><label class="form-label small fw-semibold" for="df-10">Min 10th %</label><input id="df-10" type="number" class="form-control" formControlName="minTenth" /></div>
                <div class="col-6 col-md-4"><label class="form-label small fw-semibold" for="df-12">Min 12th %</label><input id="df-12" type="number" class="form-control" formControlName="minTwelfth" /></div>
                <div class="col-6 col-md-4"><label class="form-label small fw-semibold" for="df-bl">Max active backlogs</label><input id="df-bl" type="number" min="0" class="form-control" formControlName="maxActiveBacklogs" /></div>
                <div class="col-6 col-md-4"><label class="form-label small fw-semibold" for="df-gap">Max gap years</label><input id="df-gap" type="number" min="0" class="form-control" formControlName="maxGapYears" /></div>
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-req">Required skills</label><input id="df-req" class="form-control" formControlName="requiredSkills" placeholder="comma separated" /></div>
                <fieldset class="col-md-6"><legend class="form-label small fw-semibold">Eligible branches <span class="text-body-secondary fw-normal">(none = all)</span></legend>
                  @for (b of college.branches(); track b.id) {
                    <div class="form-check form-check-inline"><input class="form-check-input" type="checkbox" [id]="'br' + b.id" [checked]="branchIds().includes(b.id)" (change)="toggle(branchIds, b.id)" />
                      <label class="form-check-label small" [for]="'br' + b.id">{{ b.code }}</label></div>
                  }</fieldset>
                <fieldset class="col-md-6"><legend class="form-label small fw-semibold">Eligible batches <span class="text-body-secondary fw-normal">(none = all)</span></legend>
                  @for (b of college.batches(); track b.id) {
                    <div class="form-check form-check-inline"><input class="form-check-input" type="checkbox" [id]="'ba' + b.id" [checked]="batches().includes(b.gradYear)" (change)="toggle(batches, b.gradYear)" />
                      <label class="form-check-label small" [for]="'ba' + b.id">{{ b.gradYear }}</label></div>
                  }</fieldset>
                <div class="col-12"><div class="alert alert-light border small mb-0"><i class="bi bi-info-circle"></i> The college placement policy (one-offer / dream rules) is applied on top of these criteria. You can preview the eligible list after saving.</div></div>
              </div>
            }
            @case (3) {
              <div class="row g-3">
                <div class="col-md-4"><label class="form-label small fw-semibold" for="df-mode">Mode</label>
                  <select id="df-mode" class="form-select" formControlName="mode">@for (m of modes; track m) { <option [value]="m">{{ m }}</option> }</select></div>
                <div class="col-md-8"><label class="form-label small fw-semibold" for="df-venue">Venue / meeting link</label><input id="df-venue" class="form-control" formControlName="venue" /></div>
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-dl">Registration deadline</label>
                  <input id="df-dl" type="datetime-local" class="form-control" formControlName="registrationDeadline" /></div>
                <div class="col-md-6"><label class="form-label small fw-semibold" for="df-date">Drive date</label><input id="df-date" type="date" class="form-control" formControlName="driveDate" /></div>
              </div>
            }
          }
          @if (error()) { <div class="alert alert-danger small mt-3" role="alert">{{ error() }}</div> }
          <div class="d-flex justify-content-between mt-4">
            <button type="button" class="btn btn-outline-secondary" [disabled]="step() === 0" (click)="step.set(step() - 1)">Back</button>
            <div class="d-flex gap-2">
              @if (step() < 3) { <button type="button" class="btn btn-outline-primary" (click)="save()" [disabled]="busy()">Save draft</button> }
              <button type="submit" class="btn btn-primary" [disabled]="busy()">{{ step() < 3 ? 'Next' : (id() ? 'Save changes' : 'Create drive') }}</button>
            </div>
          </div>
        </form>
      }
    </section>`,
  styles: [`.pm-df-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.75rem; max-width: 980px; }`],
})
export class DriveForm implements OnInit {
  private drives = inject(PlacementDriveService);
  private companiesSvc = inject(CompanyService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private router = inject(Router);
  id = input<string>();
  companyId = input<string>();
  steps = ['Basics', 'Compensation', 'Eligibility', 'Schedule'];
  types: DriveType[] = ['ON_CAMPUS', 'OFF_CAMPUS', 'POOL', 'INTERNSHIP', 'PPO'];
  modes: DriveMode[] = ['OFFLINE', 'ONLINE', 'HYBRID'];
  step = signal(0);
  busy = signal(false);
  error = signal('');
  loadingDrive = signal(false);
  companies = signal<Company[]>([]);
  branchIds = signal<string[]>([]);
  batches = signal<number[]>([]);
  private ctc = signal<number | null>(null);
  autoTier = computed(() => {
    const c = this.ctc();
    if (c === null) return null;
    return [...this.college.tiers()].sort((a, b) => b.tierRank - a.tierRank).find(t => c >= t.minCtcLpa)?.name ?? null;
  });

  form = inject(FormBuilder).group({
    companyId: ['', Validators.required], title: ['', [Validators.required, Validators.maxLength(200)]], roleTitle: ['', Validators.required],
    driveType: ['ON_CAMPUS' as DriveType], jobDescription: [''], skills: [''],
    ctcLpa: [null as number | null, Validators.min(0)], stipendMonthly: [null as number | null, Validators.min(0)], tierId: [''], ctcBreakup: [''],
    location: [''], bondMonths: [0 as number | null], bondDetails: [''],
    eligibility: inject(FormBuilder).group({
      minCgpa: [null as number | null], minTenth: [null as number | null], minTwelfth: [null as number | null],
      maxActiveBacklogs: [null as number | null], maxGapYears: [null as number | null], requiredSkills: [''],
    }),
    mode: ['OFFLINE' as DriveMode], venue: [''], registrationDeadline: [''], driveDate: [''],
  });

  ngOnInit(): void {
    this.companiesSvc.all().subscribe(c => this.companies.set(c));
    this.form.controls.ctcLpa.valueChanges.subscribe(v => this.ctc.set(v === null || v === undefined || (v as unknown) === '' ? null : Number(v)));
    if (this.companyId()) this.form.patchValue({ companyId: this.companyId()! });
    if (this.id()) {
      this.loadingDrive.set(true);
      this.drives.get(this.id()!).subscribe(d => {
        const dl = d.registrationDeadline ? new Date(d.registrationDeadline) : null;
        const local = dl ? new Date(dl.getTime() - dl.getTimezoneOffset() * 60000).toISOString().slice(0, 16) : '';
        this.form.patchValue({
          companyId: d.companyId, title: d.title, roleTitle: d.roleTitle, driveType: d.driveType, jobDescription: d.jobDescription ?? '',
          skills: d.skills.join(', '), ctcLpa: d.ctcLpa ?? null, stipendMonthly: d.stipendMonthly ?? null, tierId: d.tierId ?? '',
          ctcBreakup: d.ctcBreakup ?? '', location: d.location ?? '', bondMonths: d.bondMonths, bondDetails: d.bondDetails ?? '',
          mode: d.mode ?? 'OFFLINE', venue: d.venue ?? '', registrationDeadline: local, driveDate: d.driveDate ?? '',
          eligibility: { ...d.eligibility, requiredSkills: (d.eligibility.requiredSkills ?? []).join(', ') } as never,
        });
        this.branchIds.set(d.eligibility.branchIds ?? []);
        this.batches.set(d.eligibility.batchYears ?? []);
        this.ctc.set(d.ctcLpa ?? null);
        this.loadingDrive.set(false);
      });
    }
  }

  bad(k: string): boolean { const c = this.form.get(k); return !!c && c.touched && c.invalid; }
  toggle<T>(sig: ReturnType<typeof signal<T[]>>, v: T): void { sig.update(list => list.includes(v) ? list.filter(x => x !== v) : [...list, v]); }

  next(): void {
    if (this.step() === 0) {
      ['companyId', 'title', 'roleTitle'].forEach(k => this.form.get(k)!.markAsTouched());
      if (this.form.controls.companyId.invalid || this.form.controls.title.invalid || this.form.controls.roleTitle.invalid) return;
    }
    if (this.step() < 3) { this.step.set(this.step() + 1); return; }
    this.save();
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.step.set(0); return; }
    const v = this.form.getRawValue();
    const e = v.eligibility;
    const body: DriveRequest = {
      companyId: v.companyId!, title: v.title!, roleTitle: v.roleTitle!, driveType: v.driveType!, jobDescription: v.jobDescription || null,
      skills: csv(v.skills), ctcLpa: v.ctcLpa, stipendMonthly: v.stipendMonthly, tierId: v.tierId || null, ctcBreakup: v.ctcBreakup || null,
      location: v.location || null, bondMonths: v.bondMonths ?? 0, bondDetails: v.bondDetails || null, mode: v.mode!, venue: v.venue || null,
      registrationDeadline: v.registrationDeadline ? new Date(v.registrationDeadline).toISOString() : null, driveDate: v.driveDate || null,
      eligibility: { minCgpa: e.minCgpa, minTenth: e.minTenth, minTwelfth: e.minTwelfth, maxActiveBacklogs: e.maxActiveBacklogs, maxGapYears: e.maxGapYears,
        requiredSkills: csv(e.requiredSkills), branchIds: this.branchIds(), batchYears: this.batches() },
    };
    this.busy.set(true);
    this.error.set('');
    (this.id() ? this.drives.update(this.id()!, body) : this.drives.create(body)).subscribe({
      next: d => { this.toast.success(this.id() ? 'Drive saved' : 'Drive created as draft. Preview eligibility, then publish.'); this.router.navigate(['/college/drives', d.id]); },
      error: (err: AppError) => { this.busy.set(false); this.error.set(Object.entries(err.fieldErrors).map(([k, m]) => `${k}: ${m}`).join('; ') || err.message); },
    });
  }
}
