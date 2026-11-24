import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/** Client-side pagination bar: "21–40 of 132", page size, previous / next. Use with {@link pageOf}. */
@Component({
  selector: 'pm-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > minSize()) {
      <nav class="d-flex flex-wrap align-items-center justify-content-end gap-2 mt-2 small" aria-label="Pages">
        <span class="text-body-secondary">{{ from() }}–{{ to() }} of {{ total() }}</span>
        <select class="form-select form-select-sm w-auto" aria-label="Rows per page" [value]="size()" (change)="setSize(+$any($event.target).value)">
          @for (n of sizes(); track n) { <option [value]="n">{{ n }} / page</option> }
        </select>
        <div class="btn-group btn-group-sm">
          <button type="button" class="btn btn-outline-secondary" [disabled]="page() === 0" (click)="page.set(page() - 1)" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>
          <span class="btn btn-outline-secondary disabled">{{ page() + 1 }} / {{ pages() }}</span>
          <button type="button" class="btn btn-outline-secondary" [disabled]="page() + 1 >= pages()" (click)="page.set(page() + 1)" aria-label="Next page"><i class="bi bi-chevron-right"></i></button>
        </div>
      </nav>
    }`,
})
export class Pager {
  total = input.required<number>();
  page = model(0);
  size = model(25);
  sizes = input([10, 25, 50, 100]);
  minSize = computed(() => Math.min(...this.sizes()));
  pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  from = computed(() => (this.total() ? this.page() * this.size() + 1 : 0));
  to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));

  setSize(n: number): void { this.size.set(n); this.page.set(0); }
}

/** The rows of one page (the page is clamped when the list got shorter). */
export function pageOf<T>(rows: T[], page: number, size: number): T[] {
  const last = Math.max(0, Math.ceil(rows.length / size) - 1);
  const p = Math.min(page, last);
  return rows.slice(p * size, p * size + size);
}
