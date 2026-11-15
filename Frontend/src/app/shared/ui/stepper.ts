import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'pm-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="pm-steps" aria-label="Progress">
      @for (s of steps(); track s; let i = $index) {
        <li class="pm-step" [class.pm-step-done]="i < current()" [class.pm-step-active]="i === current()" [attr.aria-current]="i === current() ? 'step' : null">
          <button type="button" class="pm-step-btn" [disabled]="!clickable() || i > current()" (click)="go.emit(i)">
            <span class="pm-step-dot">@if (i < current()) { <i class="bi bi-check"></i> } @else { {{ i + 1 }} }</span>
            <span class="pm-step-label">{{ s }}</span>
          </button>
        </li>
      }
    </ol>`,
  styles: [`
    .pm-steps { list-style: none; padding: 0; margin: 0 0 1.25rem; display: flex; gap: .5rem; flex-wrap: wrap; }
    .pm-step { flex: 1 1 120px; }
    .pm-step-btn { all: unset; box-sizing: border-box; display: flex; align-items: center; gap: .6rem; width: 100%; padding: .65rem .9rem; border-radius: 50px; cursor: pointer;
      border: 1px solid var(--pm-card-border, #e2e8f0); background: var(--pm-card-bg, #fff); font-size: .85rem; font-weight: 600; color: var(--pm-muted, #64748b); }
    .pm-step-btn:disabled { cursor: default; }
    .pm-step-btn:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }
    .pm-step-dot { width: 1.5rem; height: 1.5rem; border-radius: 50%; display: grid; place-items: center; font-weight: 700; font-size: .75rem;
      background: var(--bs-secondary-bg); flex-shrink: 0; }
    .pm-step-active .pm-step-btn { border-color: var(--pm-primary, #16122b); color: var(--pm-heading, #0f172a); } .pm-step-active .pm-step-dot { background: var(--pm-primary, #16122b); color: #fff; }
    .pm-step-done .pm-step-dot { background: #16a34a; color: #fff; }
  `],
})
export class Stepper {
  steps = input<string[]>([]);
  current = input(0);
  clickable = input(true);
  go = output<number>();
}
