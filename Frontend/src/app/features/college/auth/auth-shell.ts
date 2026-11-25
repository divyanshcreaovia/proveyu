import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Two-column layout shared by the college auth pages. */
@Component({
  selector: 'pm-auth-shell',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-auth">
      <aside class="pm-auth-brand d-none d-lg-flex" [class.pm-auth-cxo]="variant() === 'cxo'">
        @if (variant() === 'student') {
          <a routerLink="/" class="pm-auth-logo"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span><em>Student</em></a>
          <div>
            <h2 class="fw-bold mb-3">Your placement season, all in one place.</h2>
            <ul class="list-unstyled pm-auth-points">
              <li><i class="bi bi-check2-circle"></i> See the drives you are eligible for and apply in one click</li>
              <li><i class="bi bi-check2-circle"></i> Track rounds, interviews and offers live</li>
              <li><i class="bi bi-check2-circle"></i> Announcements, chat with your placement cell & a safe document locker</li>
            </ul>
          </div>
          <small class="opacity-75">© ProveYu Student · Connected to your college's Training & Placement cell</small>
        } @else if (variant() === 'company') {
          <a routerLink="/" class="pm-auth-logo"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span><em>Recruiter</em></a>
          <div>
            <h2 class="fw-bold mb-3">Run your campus drive without spreadsheets.</h2>
            <ul class="list-unstyled pm-auth-points">
              <li><i class="bi bi-check2-circle"></i> See applicants and download every resume in one click</li>
              <li><i class="bi bi-check2-circle"></i> Shortlist, score interviews and record round results live</li>
              <li><i class="bi bi-check2-circle"></i> Release offers; the placement cell sees everything instantly</li>
            </ul>
          </div>
          <small class="opacity-75">© ProveYu Recruiter · Drive Room for campus hiring</small>
        } @else if (variant() === 'admin') {
          <a routerLink="/" class="pm-auth-logo"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span><em>Admin</em></a>
          <div>
            <h2 class="fw-bold mb-3">Run the whole platform from one place.</h2>
            <ul class="list-unstyled pm-auth-points">
              <li><i class="bi bi-check2-circle"></i> Approve, suspend and support colleges</li>
              <li><i class="bi bi-check2-circle"></i> Users, payments, mock tests and proctoring</li>
              <li><i class="bi bi-check2-circle"></i> Settings, broadcasts and a full audit log</li>
            </ul>
          </div>
          <small class="opacity-75">© ProveYu Admin · For the ProveYu team only</small>
        } @else if (variant() === 'cxo') {
          <a routerLink="/" class="pm-auth-logo"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span><em>Leadership</em></a>
          <div>
            <h2 class="fw-bold mb-3">Ask a question. Get a live dashboard.</h2>
            <ul class="list-unstyled pm-auth-points">
              <li><i class="bi bi-check2-circle"></i> Placements, packages, drives and tests in plain language</li>
              <li><i class="bi bi-check2-circle"></i> Dashboards built live from your college's own data</li>
              <li><i class="bi bi-check2-circle"></i> Save, refine and keep them updating on their own</li>
            </ul>
          </div>
          <small class="opacity-75">© ProveYu Leadership · For principals, directors and management</small>
        } @else {
          <a routerLink="/" class="pm-auth-logo"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span><em>Campus</em></a>
          <div>
            <h2 class="fw-bold mb-3">Run your entire placement season in one place.</h2>
            <ul class="list-unstyled pm-auth-points">
              <li><i class="bi bi-check2-circle"></i> Student master with bulk import & verified skills</li>
              <li><i class="bi bi-check2-circle"></i> Drives, eligibility engine & dream-offer policy</li>
              <li><i class="bi bi-check2-circle"></i> Rounds, interviews, offers & NIRF-ready reports</li>
            </ul>
          </div>
          <small class="opacity-75">© ProveYu Campus · Secure placement cell software for colleges</small>
        }
      </aside>
      <section class="pm-auth-main">
        <a routerLink="/" class="pm-auth-back"><i class="bi bi-arrow-left me-1"></i>Back to website</a>
        <div class="pm-auth-card" [class.pm-auth-wide]="wide()">
          <div class="pm-auth-cardbrand d-lg-none"><img src="images/proveyu-mark.png" alt="" /><span>ProveYu</span>
            <em>{{ variant() === 'student' ? 'Student' : variant() === 'company' ? 'Recruiter' : variant() === 'admin' ? 'Admin' : variant() === 'cxo' ? 'Leadership' : 'Campus' }}</em></div>
          <h1 class="h4 fw-bold mb-1">{{ heading() }}</h1>
          @if (sub()) { <p class="text-body-secondary small mb-4">{{ sub() }}</p> }
          <ng-content />
        </div>
      </section>
    </div>`,
  styles: [`
    .pm-auth { min-height: 100vh; display: grid; grid-template-columns: 1fr; background: var(--bs-tertiary-bg); }
    @media (min-width: 992px) { .pm-auth { grid-template-columns: 420px 1fr; } }
    .pm-auth-brand { flex-direction: column; justify-content: space-between; padding: 2.5rem; color: #fff;
      background: radial-gradient(circle at 20% 20%, #2563eb 0, #1e3a8a 45%, #0f172a 100%); }
    .pm-auth-cxo { background: radial-gradient(circle at 80% 10%, #7c3aed 0, #4338ca 40%, #0f0b2a 100%); }
    .pm-auth-logo { display: inline-flex; align-items: center; gap: .6rem; text-decoration: none; color: #fff; }
    .pm-auth-logo img { width: 2.6rem; height: 2.6rem; object-fit: contain; filter: drop-shadow(0 4px 12px rgba(0,0,0,.25)); }
    .pm-auth-logo span { font-size: 1.6rem; font-weight: 800; letter-spacing: -.02em; }
    .pm-auth-logo em, .pm-auth-cardbrand em { font-style: normal; font-size: .65rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase;
      padding: .25rem .5rem; border-radius: 6px; background: rgba(255,255,255,.18); border: 1px solid rgba(255,255,255,.35); }
    .pm-auth-cardbrand { display: flex; align-items: center; gap: .5rem; margin-bottom: 1.25rem; }
    .pm-auth-cardbrand img { width: 2rem; height: 2rem; object-fit: contain; }
    .pm-auth-cardbrand span { font-weight: 800; font-size: 1.2rem; letter-spacing: -.02em; }
    .pm-auth-cardbrand em { background: linear-gradient(90deg, #0b5cff, #00b4ff); color: #fff; border: 0; }
    .pm-auth-points li { margin-bottom: .6rem; } .pm-auth-points i { color: #86efac; margin-right: .4rem; }
    .pm-auth-main { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 1.5rem 1rem; gap: 1rem; }
    .pm-auth-back { align-self: flex-start; font-size: .85rem; font-weight: 600; text-decoration: none; color: var(--bs-secondary-color); }
    .pm-auth-back:hover { color: var(--bs-body-color); }
    @media (min-width: 992px) { .pm-auth-back { position: absolute; top: 1.5rem; } .pm-auth-main { position: relative; } }
    .pm-auth-card { width: 100%; max-width: 440px; background: var(--bs-body-bg); border-radius: 1rem; padding: 1.75rem; box-shadow: 0 10px 40px rgba(15,23,42,.08); }
    .pm-auth-wide { max-width: 760px; }
  `],
})
export class AuthShell {
  heading = input('');
  sub = input('');
  wide = input(false);
  /** 'student' switches the brand panel to the student portal copy. */
  variant = input<'college' | 'student' | 'company' | 'admin' | 'cxo'>('college');
}
