import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Two-column layout shared by the college auth pages. */
@Component({
  selector: 'pm-auth-shell',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-auth">
      <aside class="pm-auth-brand d-none d-lg-flex">
        <a routerLink="/" class="text-white text-decoration-none fw-bold fs-4"><i class="bi bi-mortarboard-fill me-2"></i>PROVEYU Campus</a>
        <div>
          <h2 class="fw-bold mb-3">Run your entire placement season in one place.</h2>
          <ul class="list-unstyled pm-auth-points">
            <li><i class="bi bi-check2-circle"></i> Student master with bulk import & verified skills</li>
            <li><i class="bi bi-check2-circle"></i> Drives, eligibility engine & dream-offer policy</li>
            <li><i class="bi bi-check2-circle"></i> Rounds, interviews, offers & NIRF-ready reports</li>
          </ul>
        </div>
        <small class="opacity-75">© PROVEYU · Secure, multi-tenant placement cell software</small>
      </aside>
      <section class="pm-auth-main">
        <div class="pm-auth-card" [class.pm-auth-wide]="wide()">
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
    .pm-auth-points li { margin-bottom: .6rem; } .pm-auth-points i { color: #86efac; margin-right: .4rem; }
    .pm-auth-main { display: flex; align-items: center; justify-content: center; padding: 1.5rem 1rem; }
    .pm-auth-card { width: 100%; max-width: 440px; background: var(--bs-body-bg); border-radius: 1rem; padding: 1.75rem; box-shadow: 0 10px 40px rgba(15,23,42,.08); }
    .pm-auth-wide { max-width: 760px; }
  `],
})
export class AuthShell {
  heading = input('');
  sub = input('');
  wide = input(false);
}
