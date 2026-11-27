import { Scene } from '../../scene';
import { ScrollAnimate } from '../../scroll-animate';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AppError } from '../../../../core/api';
import { EnquiryService, NewEnquiry, ORG_TYPES, OrgType, STUDENT_COUNTS } from '../../services/enquiry.service';
import { SUPPORT_EMAIL } from '../../../support/support.service';

/** Public pricing page: pricing is custom, so the visitor leaves their details and the ProveYu team calls back. */
@Component({
  selector: 'app-pricing',
  imports: [RouterModule, ReactiveFormsModule, ScrollAnimate, Scene],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
  <div class="pv pr-page" appScrollAnimate>
    <!-- row 1: what it is on the left, the quote form on the right -->
    <section class="pv-grid-bg pr-top">
      <div class="pv-wrap pr-grid">
        <div class="pr-intro pv-copy">
          <p class="pv-pill">Pricing</p>
          <h1 class="pv-title">Pricing built around <span>your campus.</span></h1>
          <p class="pv-lead">
            No fixed plans. Tell us about your college and our team will contact you with a quote that fits.
          </p>
          <div class="pr-included">
            <strong>The full platform, always</strong>
            <div class="pv-chips">@for (m of included; track m) { <span>{{ m }}</span> }</div>
          </div>
          <p class="pr-call">
            <i class="bi bi-telephone me-1" aria-hidden="true"></i>Our team calls you within 1–2 working days with a demo and a quote. No payment
            and no commitment to start.<br>
            Prefer e-mail? <a class="pv-mail" [href]="'mailto:' + supportEmail">{{ supportEmail }}</a>
          </p>
        </div>

        <div class="pr-form">
            <div class="card border-0 pr-card">
              @if (sent()) {
                <div class="text-center py-4" role="status">
                  <div class="pr-done mx-auto mb-3"><i class="bi bi-check2" aria-hidden="true"></i></div>
                  <h2 class="h4 fw-bold text-navy">Thank you, {{ sentName() }}!</h2>
                  <p class="text-muted-custom mb-4">We have your details. Our team will contact you soon at the phone number or e-mail you gave.</p>
                  <a routerLink="/for-colleges" class="btn btn-outline-custom px-4 py-2 text-decoration-none">See what ProveYu does</a>
                </div>
              } @else {
                <h2 class="h5 fw-bold text-navy mb-1">Get a custom quote</h2>
                <p class="text-muted-custom fs-7 mb-3">Fields marked * are required.</p>
                <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
                  <div class="row g-3">
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-name">Your name *</label>
                      <input id="pr-name" class="form-control" formControlName="fullName" autocomplete="name" maxlength="150"
                        [class.is-invalid]="bad('fullName')" />
                      <div class="invalid-feedback">Enter your name.</div>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-desig">Designation</label>
                      <input id="pr-desig" class="form-control" formControlName="designation" placeholder="e.g. TPO, Principal, Campus Management" maxlength="120" />
                    </div>
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-email">Official e-mail *</label>
                      <input id="pr-email" type="email" class="form-control" formControlName="email" autocomplete="email" maxlength="190"
                        [class.is-invalid]="bad('email')" />
                      <div class="invalid-feedback">Enter a valid e-mail.</div>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-phone">Phone *</label>
                      <input id="pr-phone" type="tel" class="form-control" formControlName="phone" autocomplete="tel" placeholder="+91 98765 43210"
                        maxlength="20" [class.is-invalid]="bad('phone')" />
                      <div class="invalid-feedback">Enter a valid phone number.</div>
                    </div>
                    <div class="col-md-8">
                      <label class="form-label fs-7 fw-semibold" for="pr-org">College / organisation name *</label>
                      <input id="pr-org" class="form-control" formControlName="organization" autocomplete="organization" maxlength="255"
                        [class.is-invalid]="bad('organization')" />
                      <div class="invalid-feedback">Enter the name of your college or organisation.</div>
                    </div>
                    <div class="col-md-4">
                      <label class="form-label fs-7 fw-semibold" for="pr-type">You are *</label>
                      <select id="pr-type" class="form-select" formControlName="orgType">
                        @for (o of orgTypes; track o.key) { <option [value]="o.key">{{ o.label }}</option> }
                      </select>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-city">City</label>
                      <input id="pr-city" class="form-control" formControlName="city" autocomplete="address-level2" maxlength="120" />
                    </div>
                    <div class="col-md-6">
                      <label class="form-label fs-7 fw-semibold" for="pr-count">Students in the final years</label>
                      <select id="pr-count" class="form-select" formControlName="studentCount">
                        <option value="">Choose</option>
                        @for (c of counts; track c.key) { <option [value]="c.key">{{ c.label }}</option> }
                      </select>
                    </div>
                    <div class="col-12">
                      <label class="form-label fs-7 fw-semibold" for="pr-msg">What do you need?</label>
                      <textarea id="pr-msg" class="form-control" rows="2" formControlName="message" maxlength="2000"
                        placeholder="e.g. placement portal for 10 branches"></textarea>
                    </div>
                    <!-- hidden from people; bots fill it and are dropped -->
                    <div class="pr-hp" aria-hidden="true">
                      <label for="pr-web">Website</label>
                      <input id="pr-web" formControlName="website" tabindex="-1" autocomplete="off" />
                    </div>
                  </div>
                  @if (error()) { <div class="alert alert-danger fs-7 mt-3 mb-0 py-2" role="alert">{{ error() }}</div> }
                  <button type="submit" class="pv-btn pr-submit w-100 mt-3" [disabled]="busy()">
                    @if (busy()) { <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span> }
                    Request a quote
                  </button>
                  <p class="fs-8 text-muted-custom text-center mt-3 mb-0">We use these details only to contact you about ProveYu.</p>
                </form>
              }
            </div>
        </div>
      </div>
    </section>

    <!-- row 2: picture and what the quote depends on, side by side -->
    <section class="pv-sec">
      <div class="pv-wrap">
        <div class="pv-split">
          <app-scene kind="reports" />
          <div class="pv-copy">
            <p class="pv-pill">Your quote</p>
            <h2 class="pv-h2">What it <span>depends on</span></h2>
            <ul class="list-unstyled pr-points">
              @for (p of points; track p.title) {
                <li>
                  <span class="pr-icon"><i class="bi {{ p.icon }}" aria-hidden="true"></i></span>
                  <div><strong>{{ p.title }}</strong><span>{{ p.text }}</span></div>
                </li>
              }
            </ul>
          </div>
        </div>
      </div>
    </section>
  </div>`,
  styles: [`
    .pr-top { display: flex; align-items: center; padding: 3rem 0; }
    .pr-grid { display: grid; gap: 2.5rem; width: 100%; }
    .pr-form { min-width: 0; }
    @media (min-width: 992px) {
      .pr-top { min-height: clamp(600px, calc(100vh - 134px), 820px); }
      .pr-grid { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); column-gap: 4rem; align-items: center; }
      .pr-form { justify-self: end; width: 100%; max-width: 700px; }
    }
    .pr-intro .pv-title { font-size: clamp(2.4rem, 4vw, 4.6rem); line-height: 1.08; }
    .pr-included { margin-top: 1.8rem; strong { display: block; color: #0a1b3f; margin-bottom: .7rem; } }
    .pr-included .pv-chips { max-width: 36rem; }
    .pr-included .pv-chips span { background: #fff; border: 1px solid #dbe6fb; font-size: .84rem; padding: .4rem .9rem; }
    .pr-call { margin: 1.6rem 0 0; font-size: .9rem; max-width: 34rem; }
    .pv-mail { color: #0057ff; font-weight: 700; text-decoration: none; }
    .pr-card { border-radius: 1.6rem; padding: 2rem; box-shadow: 0 34px 60px -36px rgba(10, 27, 63, .45); border: 1px solid #e4ecfb !important; }
    .pr-submit { cursor: pointer; text-align: center; padding: .9rem 1rem; }
    .pr-submit:disabled { opacity: .7; cursor: not-allowed; }
    .pr-points { display: grid; gap: 1rem; margin: 1.6rem 0 0; }
    .pr-points li { display: flex; gap: 1rem; align-items: flex-start; background: #fff; border: 1px solid #e4ecfb; border-radius: 1.2rem; padding: 1.15rem 1.3rem;
      box-shadow: 0 24px 40px -34px rgba(10, 27, 63, .45); transition: translate .25s, box-shadow .25s; }
    .pr-points li:hover { translate: 0 -4px; box-shadow: 0 28px 44px -30px rgba(0, 87, 255, .5); }
    .pr-points strong { display: block; color: #0a1b3f; margin-bottom: .15rem; }
    .pr-points span { font-size: .93rem; }
    .pr-icon { flex: 0 0 auto; width: 44px; height: 44px; border-radius: 13px; display: grid; place-items: center;
      background: linear-gradient(135deg, #0057ff, #0a1b3f); color: #fff; font-size: 1.15rem !important; }
    .pr-done { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-size: 2rem;
      background: rgba(22, 163, 74, .12); color: #16a34a; }
    .fs-7 { font-size: .875rem; }
    .fs-8 { font-size: .8rem; }
    .pr-hp { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
    @media (max-width: 575.98px) { .pr-card { padding: 1.25rem; } }
  `],
})
export class Pricing {
  private fb = inject(FormBuilder);
  private svc = inject(EnquiryService);
  readonly orgTypes = ORG_TYPES;
  readonly counts = STUDENT_COUNTS;
  readonly supportEmail = SUPPORT_EMAIL;
  readonly included = ['Placement portal', 'Company Drive Room', 'Student portal', 'Code ground', 'Mock tests', 'AI mock interviews', 'Off-campus jobs', 'Leadership dashboards'];
  readonly points = [
    { icon: 'bi-people', title: 'Number of students', text: 'How many students use ProveYu in the placement and preparation years.' },
    { icon: 'bi-diagram-3', title: 'Campuses and branches', text: 'One campus or several, and how many departments run drives.' },
    { icon: 'bi-headset', title: 'Onboarding and support', text: 'Help importing your batch, training your team, and support during the season.' },
  ];

  busy = signal(false);
  error = signal('');
  sent = signal(false);
  sentName = signal('');

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    designation: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.pattern(/^[+0-9 ()-]{7,20}$/)]],
    organization: ['', [Validators.required, Validators.maxLength(255)]],
    orgType: ['COLLEGE' as OrgType],
    city: [''],
    studentCount: [''],
    message: [''],
    website: [''],
  });

  bad(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    const body: NewEnquiry = { ...v, fullName: v.fullName.trim(), email: v.email.trim(), phone: v.phone.trim(), organization: v.organization.trim() };
    this.svc.submit(body).subscribe({
      next: () => { this.busy.set(false); this.sentName.set(body.fullName.split(' ')[0]); this.sent.set(true); },
      error: (e: AppError) => {
        this.busy.set(false);
        const first = Object.values(e.fieldErrors ?? {})[0];
        this.error.set(e.code === 'ENQUIRY_LIMIT' ? e.message : first ?? e.message);
      },
    });
  }
}
