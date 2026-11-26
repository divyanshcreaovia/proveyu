import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DailyProblem } from './code-ground.models';

/** Today's problem, the same for every student; solving it today gives its points again. */
@Component({
  selector: 'pm-daily-card',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    @let d = daily();
    <section class="cg-panel dc" [class.dc-done]="d.solvedToday" aria-label="Problem of the day">
      <div class="cg-kicker"><i class="bi bi-calendar-event" aria-hidden="true"></i> Problem of the day · {{ d.date | date: 'd MMM' }}</div>
      <h2>{{ d.title }}</h2>
      <div class="dc-meta">
        <span class="cg-diff-pill" [attr.data-level]="d.difficulty">{{ level() }}</span>
        <span class="dc-bonus">+{{ d.bonus }} bonus points</span>
      </div>
      @if (d.solvedToday) {
        <p class="dc-state"><i class="bi bi-check-circle-fill" aria-hidden="true"></i> Done for today. A new one comes at midnight.</p>
        <button type="button" class="cg-btn" (click)="open.emit(d.key)">View problem</button>
      } @else {
        <p class="dc-state">Submit it today to earn the bonus and keep your streak.</p>
        <button type="button" class="cg-btn cg-btn-primary" (click)="open.emit(d.key)">Solve now <i class="bi bi-arrow-right" aria-hidden="true"></i></button>
      }
    </section>`,
  styles: [`
    :host { display: block; }
    .dc { height: 100%; display: flex; flex-direction: column; align-items: flex-start; gap: .5rem; padding: 1rem 1.1rem;
      background: linear-gradient(150deg, color-mix(in srgb, #f59e0b 13%, var(--cg-panel)), var(--cg-panel) 65%); }
    .dc-done { background: linear-gradient(150deg, color-mix(in srgb, var(--cg-ok) 13%, var(--cg-panel)), var(--cg-panel) 65%); }
    h2 { margin: 0; font-size: 1.15rem; font-weight: 700; color: var(--pm-heading); }
    .dc-meta { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; }
    .dc-bonus { font-size: .76rem; font-weight: 700; color: var(--cg-medium); }
    .dc-state { margin: 0; flex: 1; font-size: .8rem; color: var(--pm-muted); }
  `],
})
export class DailyCard {
  daily = input.required<DailyProblem>();
  open = output<string>();
  level = computed(() => ({ EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' })[this.daily().difficulty]);
}
