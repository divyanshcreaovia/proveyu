import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AppError } from '../../../core/api';
import { RegisterResponse } from '../../../core/models/college.models';
import { AuthShell } from './auth-shell';
import { Stepper } from '../../../shared/ui/stepper';

const domainMatch = (g: AbstractControl): ValidationErrors | null => {
  const email = (g.get('tpoEmail')?.value ?? '').toLowerCase().trim();
  const domain = (g.get('emailDomain')?.value ?? '').toLowerCase().trim();
  if (!email || !domain) return null;
  return email.endsWith('@' + domain) || email.endsWith('.' + domain) ? null : { domainMismatch: true };
};
const passwordsMatch = (g: AbstractControl): ValidationErrors | null =>
  g.get('password')?.value === g.get('confirm')?.value ? null : { mismatch: true };

@Component({
  selector: 'pm-college-register',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Stepper],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-auth-shell heading="Register your college" sub="Set up the placement cell workspace for your institution" [wide]="true">
      @if (done(); as d) {
        <div class="text-center py-3">
          <i class="bi bi-envelope-check text-success" style="font-size:3rem"></i>
          <h2 class="h5 fw-bold mt-2">Check your inbox</h2>
          <p class="text-body-secondary">{{ d.message }}</p>
          @if (d.devVerificationToken) {
            <div class="alert alert-secondary small text-start"><strong>Dev mode:</strong> e-mail delivery is not configured locally.
              <a [routerLink]="['/college/verify-email']" [queryParams]="{ token: d.devVerificationToken }" class="alert-link">Verify now</a>.</div>
          }
          <a routerLink="/college/login" class="btn btn-primary mt-2">Go to sign in</a>
        </div>
      } @else {
        <pm-stepper [steps]="steps" [current]="step()" (go)="step.set($event)" />
        <form [formGroup]="form" (ngSubmit)="next()" novalidate>
          @if (step() === 0) {
            <fieldset class="row g-3" formGroupName="college">
              <legend class="visually-hidden">College details</legend>
              <div class="col-md-8"><label class="form-label small fw-semibold" for="r-name">College name *</label>
                <input id="r-name" class="form-control" formControlName="collegeName" [class.is-invalid]="inv('college.collegeName')" /></div>
              <div class="col-md-4"><label class="form-label small fw-semibold" for="r-code">AICTE / UGC code *</label>
                <input id="r-code" class="form-control" formControlName="aicteCode" [class.is-invalid]="inv('college.aicteCode')" /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-uni">Affiliated university *</label>
                <input id="r-uni" class="form-control" formControlName="university" [class.is-invalid]="inv('college.university')" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="r-city">City *</label>
                <input id="r-city" class="form-control" formControlName="city" [class.is-invalid]="inv('college.city')" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="r-state">State *</label>
                <select id="r-state" class="form-select" formControlName="state" [class.is-invalid]="inv('college.state')">
                  <option value="">Select</option>@for (s of states; track s) { <option [value]="s">{{ s }}</option> }
                </select></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-domain">Official e-mail domain *</label>
                <div class="input-group"><span class="input-group-text">@</span>
                  <input id="r-domain" class="form-control" formControlName="emailDomain" placeholder="college.edu.in" [class.is-invalid]="inv('college.emailDomain')" /></div></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-web">Website</label>
                <input id="r-web" class="form-control" formControlName="website" placeholder="https://" /></div>
            </fieldset>
          }
          @if (step() === 1) {
            <fieldset class="row g-3" formGroupName="tpo">
              <legend class="visually-hidden">Training & Placement Officer</legend>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-tname">TPO full name *</label>
                <input id="r-tname" class="form-control" formControlName="tpoName" [class.is-invalid]="inv('tpo.tpoName')" /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-tphone">Phone *</label>
                <input id="r-tphone" class="form-control" formControlName="tpoPhone" inputmode="tel" [class.is-invalid]="inv('tpo.tpoPhone')" /></div>
              <div class="col-12"><label class="form-label small fw-semibold" for="r-temail">Official e-mail *</label>
                <input id="r-temail" type="email" class="form-control" formControlName="tpoEmail" [class.is-invalid]="inv('tpo.tpoEmail') || domainError()" />
                @if (domainError()) { <div class="invalid-feedback d-block">Must be an address on &#64;{{ form.value.college?.emailDomain }}</div> }</div>
            </fieldset>
          }
          @if (step() === 2) {
            <fieldset class="row g-3" formGroupName="account">
              <legend class="visually-hidden">Account</legend>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-pass">Password *</label>
                <input id="r-pass" type="password" class="form-control" formControlName="password" autocomplete="new-password" [class.is-invalid]="inv('account.password')" />
                <div class="form-text">At least 8 characters with letters and numbers.</div></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="r-confirm">Confirm password *</label>
                <input id="r-confirm" type="password" class="form-control" formControlName="confirm" autocomplete="new-password"
                       [class.is-invalid]="form.controls.account.touched && form.controls.account.errors?.['mismatch']" />
                <div class="invalid-feedback">Passwords do not match.</div></div>
              <div class="col-12 form-check ms-1">
                <input id="r-terms" type="checkbox" class="form-check-input" formControlName="acceptTerms" [class.is-invalid]="inv('account.acceptTerms')" />
                <label for="r-terms" class="form-check-label small">I confirm I am authorised to register this institution and accept the ProveYu terms & data processing policy.</label>
              </div>
            </fieldset>
          }
          @if (error()) { <div class="alert alert-danger small mt-3" role="alert">{{ error() }}</div> }
          <div class="d-flex justify-content-between mt-4">
            <button type="button" class="btn btn-outline-secondary" [disabled]="step() === 0" (click)="step.set(step() - 1)">Back</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy()">
              @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> } {{ step() < 2 ? 'Continue' : 'Create account' }}
            </button>
          </div>
        </form>
        <p class="text-center small mt-4 mb-0">Already registered? <a routerLink="/college/login">Sign in</a></p>
      }
    </pm-auth-shell>`,
})
export class CollegeRegister {
  private auth = inject(AuthService);
  steps = ['College', 'Placement officer', 'Account'];
  step = signal(0);
  busy = signal(false);
  error = signal('');
  done = signal<RegisterResponse | null>(null);
  states = ['Andhra Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka',
    'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Odisha', 'Punjab', 'Rajasthan', 'Tamil Nadu', 'Telangana', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Other'];

  form = inject(FormBuilder).nonNullable.group({
    college: inject(FormBuilder).nonNullable.group({
      collegeName: ['', [Validators.required, Validators.maxLength(200)]],
      aicteCode: ['', [Validators.required, Validators.maxLength(50)]],
      university: ['', Validators.required], city: ['', Validators.required], state: ['', Validators.required],
      emailDomain: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/)]],
      website: [''],
    }),
    tpo: inject(FormBuilder).nonNullable.group({
      tpoName: ['', Validators.required],
      tpoPhone: ['', [Validators.required, Validators.pattern(/^[0-9+\- ]{7,15}$/)]],
      tpoEmail: ['', [Validators.required, Validators.email]],
    }),
    account: inject(FormBuilder).nonNullable.group({
      password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
      confirm: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    }, { validators: passwordsMatch }),
  }, { validators: domainMatch });

  inv(path: string): boolean {
    const c = this.form.get(path);
    return !!c && c.touched && c.invalid;
  }
  domainError(): boolean { return this.form.controls.tpo.touched && !!this.form.errors?.['domainMismatch']; }

  next(): void {
    const groups = [this.form.controls.college, this.form.controls.tpo, this.form.controls.account];
    const g = groups[this.step()];
    g.markAllAsTouched();
    if (g.invalid || (this.step() === 1 && this.form.errors?.['domainMismatch'])) return;
    if (this.step() < 2) { this.step.set(this.step() + 1); return; }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.auth.registerCollege({ ...v.college, ...v.tpo, website: v.college.website || undefined, password: v.account.password, acceptTerms: v.account.acceptTerms })
      .subscribe({
        next: r => { this.busy.set(false); this.done.set(r.data); },
        error: (e: AppError) => {
          this.busy.set(false);
          const fe = Object.entries(e.fieldErrors).map(([k, m]) => `${k}: ${m}`).join('; ');
          this.error.set(fe || e.message);
        },
      });
  }
}
