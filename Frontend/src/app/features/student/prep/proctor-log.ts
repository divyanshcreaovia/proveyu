import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Observable } from 'rxjs';
import { ProctorEvent, ProctorLog, SWITCH_LABELS } from '../services/prep.service';

/**
 * Proof of every time a student left a mock test: time, what happened and the screenshot of the shared screen.
 * Used on the student's result page and in the college portal; `image` loads a screenshot (with the sign-in token).
 */
@Component({
  selector: 'pm-proctor-log',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let l = log();
    @if (l.autoSubmitReason === 'TAB_SWITCH') {
      <div class="alert alert-danger small d-flex gap-2 align-items-start mb-2" role="status">
        <i class="bi bi-exclamation-octagon-fill mt-1" aria-hidden="true"></i>
        <span><strong>Submitted automatically.</strong> {{ subject() }} left the test {{ l.events.length }} times; after {{ l.maxWarnings }} warnings
          the next switch submitted it. The score counts only the answers given before that.</span>
      </div>
    }
    @if (!l.events.length) {
      <p class="small text-body-secondary mb-0"><i class="bi bi-shield-check me-1 text-success" aria-hidden="true"></i>{{ subject() }} stayed in the test the whole time.</p>
    } @else {
      <ul class="pl-list list-unstyled mb-0">
        @for (e of l.events; track e.id) {
          <li class="pl-item">
            <span class="pl-n" [class.final]="e.seq > l.maxWarnings">{{ e.seq }}</span>
            <div class="flex-grow-1 min-w-0">
              <div class="fw-semibold small">{{ e.seq > l.maxWarnings ? 'Test submitted' : 'Warning ' + e.seq + ' of ' + l.maxWarnings }}
                · {{ e.occurredAt | date: 'd MMM y, h:mm:ss a' }}</div>
              <div class="small text-body-secondary">{{ labels[e.kind] }}</div>
            </div>
            @if (e.hasScreenshot) {
              @if (urls()[e.id]; as u) {
                <button type="button" class="pl-thumb" (click)="open.set(e)" [attr.aria-label]="'Open screenshot ' + e.seq"><img [src]="u" alt="" /></button>
              } @else { <span class="pl-thumb pl-wait"><span class="spinner-border spinner-border-sm"></span></span> }
            } @else { <span class="small text-body-secondary">No screenshot</span> }
          </li>
        }
      </ul>
    }
    @if (open(); as e) {
      <div class="pl-backdrop" (click)="open.set(null)"></div>
      <div class="pl-dialog" role="dialog" aria-modal="true" aria-label="Screenshot" tabindex="-1" (keydown.escape)="open.set(null)">
        <div class="d-flex align-items-center gap-2 mb-2">
          <strong class="small flex-grow-1">Switch {{ e.seq }} · {{ e.occurredAt | date: 'd MMM y, h:mm:ss a' }} · {{ labels[e.kind] }}</strong>
          <button class="btn-close" (click)="open.set(null)" aria-label="Close"></button>
        </div>
        <img [src]="urls()[e.id]" class="pl-big" alt="Screen of the student when they left the test" />
      </div>
    }`,
  styles: [`
    .pl-list { display: grid; gap: .5rem; }
    .pl-item { display: flex; gap: .75rem; align-items: center; padding: .6rem .75rem; border: 1px solid var(--pm-card-border, var(--bs-border-color)); border-radius: 12px; }
    .pl-n { width: 2rem; height: 2rem; border-radius: 50%; display: grid; place-items: center; font-weight: 800; font-size: .8rem; flex-shrink: 0;
      background: rgba(217,119,6,.15); color: #b45309; }
    .pl-n.final { background: rgba(225,29,72,.15); color: #be123c; }
    .pl-thumb { all: unset; box-sizing: border-box; width: 132px; height: 74px; border-radius: 8px; overflow: hidden; cursor: zoom-in; flex-shrink: 0;
      border: 1px solid var(--pm-card-border, var(--bs-border-color)); background: #0b1220; display: grid; place-items: center; }
    .pl-thumb img { width: 100%; height: 100%; object-fit: cover; }
    .pl-thumb:focus-visible { outline: 2px solid var(--pm-focus, #0d6efd); outline-offset: 2px; }
    .pl-wait { cursor: default; color: #94a3b8; }
    .pl-backdrop { position: fixed; inset: 0; background: rgba(15,23,42,.6); z-index: 1990; }
    .pl-dialog { position: fixed; z-index: 1995; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(1000px, calc(100vw - 1.5rem));
      max-height: calc(100dvh - 1.5rem); overflow: auto; background: var(--bs-body-bg); color: var(--bs-body-color); border-radius: 14px; padding: 1rem; outline: none; }
    .pl-big { display: block; width: 100%; height: auto; border-radius: 8px; background: #0b1220; }
    @media (max-width: 575.98px) { .pl-thumb { width: 96px; height: 54px; } }
  `],
})
export class ProctorLogView {
  log = input.required<ProctorLog>();
  /** Loads one screenshot as an object URL. */
  image = input.required<(eventId: string) => Observable<string>>();
  /** "You" on the student's page, the student's name for the placement cell. */
  subject = input('You');

  readonly labels = SWITCH_LABELS;
  urls = signal<Record<string, string>>({});
  open = signal<ProctorEvent | null>(null);
  private loaded = new Set<string>();

  constructor() {
    effect(() => {
      const load = this.image();
      for (const e of this.log().events) {
        if (!e.hasScreenshot || this.loaded.has(e.id)) continue;
        this.loaded.add(e.id);
        load(e.id).subscribe({ next: u => this.urls.update(m => ({ ...m, [e.id]: u })), error: () => undefined });
      }
    });
    inject(DestroyRef).onDestroy(() => Object.values(this.urls()).forEach(u => URL.revokeObjectURL(u)));
  }
}
