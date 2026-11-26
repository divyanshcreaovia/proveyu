import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { Difficulty, TopicCard } from './code-ground.models';

type Filter = 'ALL' | Difficulty;

/** A topic's problems as a problem-set table: status, number, title, difficulty. Compact mode is the list drawer in the workspace. */
@Component({
  selector: 'pm-problem-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    @if (!compact()) {
      <div class="pt-tools">
        <div class="pt-filter" role="radiogroup" aria-label="Filter by difficulty">
          @for (f of filters; track f.value) {
            <button type="button" role="radio" [attr.aria-checked]="filter() === f.value" [class.pt-f-on]="filter() === f.value" (click)="filter.set(f.value)">
              {{ f.label }}</button>
          }
        </div>
        <span class="pt-count">{{ topic().solved }}/{{ topic().problems.length }} solved</span>
      </div>
    }
    <div class="pt" role="table" [attr.aria-label]="topic().title + ' problems'">
      @if (!compact()) {
        <div class="pt-row pt-head" role="row">
          <span role="columnheader">Status</span><span role="columnheader">Title</span><span role="columnheader" class="pt-d">Difficulty</span>
        </div>
      }
      <div role="rowgroup" (keydown)="onKey($event)">
        @for (p of rows(); track p.key) {
          <button type="button" class="pt-row pt-item" role="row" [class.pt-on]="p.key === selected()" [attr.aria-current]="p.key === selected() ? 'true' : null"
                  [attr.data-key]="p.key" (click)="open.emit(p.key)">
            <span role="cell" class="cg-status" [class.cg-status-solved]="p.solved" [class.cg-status-tried]="!p.solved && p.attempted">
              @if (p.solved) { <i class="bi bi-check-circle" aria-label="Solved"></i> }
              @else if (p.attempted) { <i class="bi bi-circle-half" aria-label="Attempted"></i> }
              @else { <span class="visually-hidden">Not started</span> }
            </span>
            <span role="cell" class="pt-title"><span class="pt-n">{{ p.n }}.</span> {{ p.title }}</span>
            <span role="cell" class="cg-diff pt-d" [attr.data-level]="p.difficulty">{{ p.label }}</span>
          </button>
        } @empty {
          <div class="pt-empty">No {{ filter().toLowerCase() }} problems in this topic.</div>
        }
      </div>
    </div>`,
  styles: [`
    :host { display: block; }
    .pt-tools { display: flex; justify-content: space-between; align-items: center; gap: .75rem; margin-bottom: .6rem; flex-wrap: wrap; }
    .pt-filter { display: inline-flex; padding: 3px; border-radius: 8px; background: var(--cg-sub); }
    .pt-filter button { all: unset; cursor: pointer; padding: .3rem .8rem; border-radius: 6px; font-size: .78rem; font-weight: 600; color: var(--pm-muted); }
    .pt-filter button:hover { color: var(--pm-heading); }
    .pt-filter .pt-f-on { background: var(--cg-panel); color: var(--pm-heading); box-shadow: 0 1px 2px rgba(0,0,0,.08); }
    .pt-filter button:focus-visible { outline: 2px solid var(--pm-link); }
    .pt-count { font-size: .78rem; color: var(--pm-muted); font-variant-numeric: tabular-nums; }
    .pt-row { display: grid; grid-template-columns: 44px minmax(0, 1fr) 84px; align-items: center; gap: .5rem; min-height: 44px; padding: 0 .75rem; }
    .pt-head { min-height: 36px; font-size: .72rem; font-weight: 600; color: var(--pm-muted); border-bottom: 1px solid var(--cg-line); }
    .pt-item { all: unset; box-sizing: border-box; position: relative; display: grid; grid-template-columns: 44px minmax(0, 1fr) 84px; align-items: center; gap: .5rem; min-height: 44px;
      padding: 0 .75rem; width: 100%; cursor: pointer; border-radius: 6px; }
    .pt-item:nth-child(even) { background: color-mix(in srgb, var(--cg-sub) 55%, transparent); }
    .pt-item:hover { background: var(--cg-sub); }
    .pt-item:focus-visible { outline: 2px solid var(--pm-link); outline-offset: -2px; }
    .pt-on { background: color-mix(in srgb, var(--pm-link) 10%, transparent) !important; }
    .pt-title { font-size: .88rem; font-weight: 500; color: var(--pm-heading); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .pt-n { color: var(--pm-muted); font-variant-numeric: tabular-nums; }
    .pt-d { text-align: right; }
    .pt-empty { padding: 1.25rem .75rem; color: var(--pm-muted); font-size: .85rem; }
    :host(.pt-compact) .pt-item { grid-template-columns: 28px minmax(0, 1fr) 64px; padding: 0 .6rem; }
  `],
  host: { '[class.pt-compact]': 'compact()' },
})
export class ProblemTable {
  topic = input.required<TopicCard>();
  selected = input<string | null>(null);
  compact = input(false);
  open = output<string>();
  filter = signal<Filter>('ALL');
  readonly filters: { value: Filter; label: string }[] = [{ value: 'ALL', label: 'All' }, { value: 'EASY', label: 'Easy' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'HARD', label: 'Hard' }];
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  rows = computed(() => this.topic().problems
    .map((p, i) => ({ ...p, n: i + 1, label: p.difficulty === 'EASY' ? 'Easy' : p.difficulty === 'MEDIUM' ? 'Medium' : 'Hard' }))
    .filter(p => this.filter() === 'ALL' || p.difficulty === this.filter()));

  onKey(e: KeyboardEvent): void {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    e.preventDefault();
    const items = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.pt-item'));
    const at = items.indexOf(document.activeElement as HTMLElement);
    items[(at + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus();
  }

  /** Focus the open problem (or the first one), e.g. when the drawer opens. */
  focus(): void {
    const el = this.host.nativeElement;
    (el.querySelector<HTMLElement>('.pt-on') ?? el.querySelector<HTMLElement>('.pt-item'))?.focus();
  }
}
