import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SUPPORT_EMAIL } from '../support/support.service';

/** Closing call to action shared by the public pages. */
@Component({
  selector: 'app-public-cta',
  imports: [RouterLink],
  template: `
    <section class="pv-sec">
      <div class="pv-wrap">
        <div class="pv-cta pv-card-like">
          <span class="orb" aria-hidden="true"></span>
          <div>
            <h2 class="pv-h2">{{ heading() }}</h2>
            <p class="mb-0">{{ text() }}</p>
            <div class="pv-actions">
              @if (forStudents()) {
                <a routerLink="/student/signup" class="pv-btn">Activate your account</a>
                <a routerLink="/student/login" class="pv-btn alt">Student sign in</a>
              } @else {
                <a routerLink="/college/register" class="pv-btn">Register your college</a>
                <a routerLink="/student/signup" class="pv-btn alt">Activate student account</a>
              }
            </div>
            <p class="mt-3 mb-0 small">Questions? Write to <a class="text-white fw-bold" [href]="'mailto:' + supportEmail">{{ supportEmail }}</a></p>
          </div>
          <div class="pops" aria-hidden="true">@for (p of forStudents() ? studentPops : pops; track p) { <span class="pop">{{ p }}</span> }</div>
        </div>
      </div>
    </section>`,
})
export class PublicCta {
  readonly heading = input('Run your next drive on ProveYu');
  readonly text = input('Register your placement cell, import your batch and publish your first drive.');
  readonly forStudents = input(false);
  readonly supportEmail = SUPPORT_EMAIL;
  readonly studentPops = ['Applied to a drive', 'Mock test cleared', 'Offer accepted'];
  readonly pops = ['Drive published', 'Eligible students notified', 'Offer accepted'];
}
