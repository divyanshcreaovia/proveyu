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
  imports: [RouterModule, ReactiveFormsModule, ScrollAnimate],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../home/home.scss'],
  template: `
  <div class="pr-page" appScrollAnimate>
    <section class="pr-wrap">
      <div class="container-custom">
        <!-- desktop: intro and details on the left, form on the right; phone: intro, form, details -->
        <div class="pr-grid">
          <div class="pr-intro">
            <div class="pill-badge mb-3">
              <i class="bi bi-tag" aria-hidden="true"></i>
              <span>Pricing</span>
            </div>
            <h1 class="pr-title">Pricing built around <span class="gradient-text">your campus.</span></h1>
            <p class="pr-lead mb-0 mb-lg-4">
              No fixed plans and no hidden extras. Tell us about your college and our team will contact you with a quote that fits.
            </p>
          </div>

          <div class="pr-details">
            <div class="pr-included mb-4">
              <strong class="d-block text-navy mb-2">The full platform, always</strong>
              <div class="d-flex flex-wrap gap-2">
                @for (m of included; track m) { <span class="pr-chip">{{ m }}</span> }
              </div>
            </div>

            <h2 class="h6 fw-bold text-navy mb-3">Your quote depends on</h2>
            <ul class="list-unstyled pr-points mb-4">
              @for (p of points; track p.title) {
                <li>
                  <span class="pr-icon"><i class="bi {{ p.icon }}" aria-hidden="true"></i></span>
                  <div><strong class="d-block text-navy">{{ p.title }}</strong><span class="text-muted-custom fs-7">{{ p.text }}</span></div>
                </li>
              }
            </ul>
            <p class="fs-7 text-muted-custom mb-0">
              <i class="bi bi-telephone me-1" aria-hidden="true"></i>Our team calls you within 1–2 working days with a demo and a quote. No payment
              and no commitment to start.<br>
              Prefer e-mail? <a class="fw-semibold text-primary text-decoration-none" [href]="'mailto:' + supportEmail">{{ supportEmail }}</a>
            </p>
          </div>

          <!-- form -->
          <div class="pr-form">
            <div class="card card-shadow border-0 rounded-4 p-4 p-md-4 pr-card">
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
                      <textarea id="pr-msg" class="form-control" rows="3" formControlName="message" maxlength="2000"
                        placeholder="e.g. placement portal for 10 branches"></textarea>
                    </div>
                    <!-- hidden from people; bots fill it and are dropped -->
                    <div class="pr-hp" aria-hidden="true">
                      <label for="pr-web">Website</label>
                      <input id="pr-web" formControlName="website" tabindex="-1" autocomplete="off" />
                    </div>
                  </div>
                  @if (error()) { <div class="alert alert-danger fs-7 mt-3 mb-0 py-2" role="alert">{{ error() }}</div> }
                  <button type="submit" class="btn btn-primary-custom w-100 py-3 mt-3" [disabled]="busy()">
                    @if (busy()) { <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span> }
                    Request a quote
                  </button>
                  <p class="fs-8 text-muted-custom text-center mt-3 mb-0">We use these details only to contact you about ProveYu.</p>
                </form>
              }
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>`,
  styles: [`
    .pr-page { background: radial-gradient(circle at 85% 0%, rgba(99, 102, 241, .08) 0%, transparent 45%), #fff; }
    .pr-wrap { padding: 1.5rem 0 4rem; }
    .pr-grid { display: grid; gap: 1.5rem; grid-template-areas: 'intro' 'form' 'details'; }
    .pr-intro { grid-area: intro; } .pr-details { grid-area: details; } .pr-form { grid-area: form; min-width: 0; }
    @media (min-width: 992px) {
      .pr-grid { grid-template-columns: 5fr 7fr; grid-template-areas: 'intro form' 'details form'; column-gap: 3rem; row-gap: 0; align-items: start; }
    }
    .pr-title { font-size: clamp(1.9rem, 3.2vw, 2.6rem); font-weight: 800; line-height: 1.15; letter-spacing: -.02em; color: #0a1b3f; margin-bottom: .9rem; }
    .pr-lead { color: #64748b; font-size: 1.02rem; line-height: 1.6; margin-bottom: 1.4rem; max-width: 30rem; }
    .pr-included { background: #f5f8ff; border: 1px solid #e0e9ff; border-radius: 14px; padding: 1rem 1.1rem; }
    .pr-tick { color: #16a34a; }
    .pr-chip { font-size: .8rem; font-weight: 600; color: #334155; background: #fff; border: 1px solid #e2e8f0; border-radius: 999px; padding: .25rem .7rem; }
    @media (min-width: 992px) { .pr-card { position: sticky; top: 96px; } }
    .pr-points { display: grid; gap: 1rem; }
    .pr-points li { display: flex; gap: .85rem; align-items: flex-start; }
    .pr-icon { flex: 0 0 auto; width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center;
      background: rgba(0, 87, 255, .08); color: #0057ff; font-size: 1.1rem; }
    .pr-done { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-size: 2rem;
      background: rgba(22, 163, 74, .12); color: #16a34a; }
    .pr-hp { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
  `],
})
export class Pricing {
  private fb = inject(FormBuilder);
  private svc = inject(EnquiryService);
  readonly orgTypes = ORG_TYPES;
  readonly counts = STUDENT_COUNTS;
  readonly supportEmail = SUPPORT_EMAIL;
  readonly included = ['Placement portal', 'Company Drive Room', 'Student portal', 'Mock tests', 'AI mock interviews', 'Off-campus jobs'];
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
