import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
import { TopicCard } from './code-ground.models';

const R = 15;

/** The study plan track: one step per topic with a progress ring (problems + learning guide). Arrow keys move along it. */
@Component({
  selector: 'pm-roadmap',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    <div class="rm" role="tablist" aria-label="Topics" (keydown)="onKey($event)">
      @for (t of topics(); track t.key; let i = $index) {
        <button type="button" role="tab" class="rm-step" [class.rm-on]="t.key === selected()" [class.rm-full]="t.done === t.total"
                [class.rm-started]="t.done > 0" [attr.aria-selected]="t.key === selected()" [attr.tabindex]="t.key === selected() ? 0 : -1"
                [attr.data-key]="t.key" [attr.aria-label]="(i + 1) + '. ' + t.title + ': ' + t.done + ' of ' + t.total + ' done'" (click)="pick.emit(t.key)">
          <span class="rm-ring" aria-hidden="true">
            <svg viewBox="0 0 36 36">
              <circle cx="18" cy="18" [attr.r]="r" class="rm-track" />
              <circle cx="18" cy="18" [attr.r]="r" class="rm-fill" [attr.stroke-dasharray]="length" [attr.stroke-dashoffset]="length * (1 - t.done / t.total)" />
            </svg>
            <span class="rm-mark">@if (t.done === t.total) { <i class="bi bi-check-lg"></i> } @else { {{ i + 1 }} }</span>
          </span>
          <span class="rm-text">
            <span class="rm-title">{{ t.title }}</span>
            <span class="rm-count">{{ t.done }}/{{ t.total }}</span>
          </span>
        </button>
      }
    </div>`,
  styles: [`
    :host { display: block; }
    .rm { display: flex; overflow-x: auto; scrollbar-width: thin; padding: .35rem; gap: 0; }
    .rm-step { all: unset; box-sizing: border-box; cursor: pointer; position: relative; flex: 1 0 128px; display: flex; flex-direction: column; align-items: center;
      gap: .45rem; padding: .7rem .4rem .75rem; border-radius: 8px; text-align: center; }
    .rm-step + .rm-step::before { content: ''; position: absolute; top: calc(.7rem + 17px); right: calc(50% + 24px); width: calc(100% - 48px); height: 2px;
      background: var(--cg-line); }
    .rm-step.rm-started + .rm-step::before, .rm-step.rm-full + .rm-step::before { background: color-mix(in srgb, var(--cg-ok) 45%, var(--cg-line)); }
    .rm-step:hover { background: var(--cg-sub); }
    .rm-step:focus-visible { outline: 2px solid var(--pm-link); outline-offset: -2px; }
    .rm-on { background: var(--cg-sub); }
    .rm-on::after { content: ''; position: absolute; left: 22%; right: 22%; bottom: 0; height: 2px; border-radius: 2px; background: var(--pm-heading); }
    .rm-ring { position: relative; width: 36px; height: 36px; display: grid; place-items: center; }
    .rm-ring svg { position: absolute; inset: 0; transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 3; }
    .rm-track { stroke: var(--cg-line); }
    .rm-fill { stroke: var(--cg-ok); stroke-linecap: round; transition: stroke-dashoffset .5s ease; }
    .rm-mark { position: relative; font-size: .78rem; font-weight: 700; color: var(--pm-muted); font-variant-numeric: tabular-nums; }
    .rm-on .rm-mark { color: var(--pm-heading); }
    .rm-full .rm-ring { background: var(--cg-ok); border-radius: 50%; }
    .rm-full .rm-mark { color: #fff; }
    .rm-full .rm-track, .rm-full .rm-fill { stroke: transparent; }
    .rm-text { display: flex; flex-direction: column; gap: .1rem; }
    .rm-title { font-size: .78rem; font-weight: 600; color: var(--pm-heading); line-height: 1.25; }
    .rm-count { font-size: .7rem; color: var(--pm-muted); font-variant-numeric: tabular-nums; }
  `],
})
export class Roadmap {
  topics = input.required<TopicCard[]>();
  selected = input.required<string>();
  pick = output<string>();
  readonly r = R;
  readonly length = 2 * Math.PI * R;
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  onKey(e: KeyboardEvent): void {
    const list = this.topics();
    const at = list.findIndex(t => t.key === this.selected());
    const next = e.key === 'ArrowRight' ? at + 1 : e.key === 'ArrowLeft' ? at - 1 : e.key === 'Home' ? 0 : e.key === 'End' ? list.length - 1 : null;
    if (next === null) return;
    e.preventDefault();
    const topic = list[(next + list.length) % list.length];
    this.pick.emit(topic.key);
    const el = this.host.nativeElement.querySelector<HTMLElement>(`[data-key="${topic.key}"]`);
    el?.focus();
    el?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
}
