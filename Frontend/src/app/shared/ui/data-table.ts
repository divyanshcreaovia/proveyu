import { ChangeDetectionStrategy, Component, TemplateRef, computed, input, model, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { EmptyState, Skeleton } from './states';

export interface Column {
  key: string; label: string; sortable?: boolean; align?: 'start' | 'end' | 'center'; width?: string;
  /** Hide below the md breakpoint to keep tables readable on phones. */
  hideSm?: boolean;
}

/**
 * Server-side table: the parent owns data, paging and sorting and reloads on (pageChange) / (sortChange).
 * Custom cells: pass [cell]="tpl" where tpl is `<ng-template #tpl let-row let-col="col">`.
 */
@Component({
  selector: 'pm-data-table',
  imports: [NgTemplateOutlet, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-dt">
      @if (loading() && !rows().length) {
        <div class="p-3"><pm-skeleton [rows]="6" /></div>
      } @else if (!rows().length) {
        <pm-empty-state [icon]="emptyIcon()" [title]="emptyTitle()" [text]="emptyText()"><ng-content select="[pmEmpty]" /></pm-empty-state>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 pm-dt-table" [class.pm-dt-loading]="loading()">
            <thead>
              <tr>
                @if (selectable()) {
                  <th scope="col" class="pm-dt-check">
                    <input class="form-check-input" type="checkbox" aria-label="Select all on this page" [checked]="allSelected()" (change)="toggleAll()" />
                  </th>
                }
                @for (c of columns(); track c.key) {
                  <th scope="col" [class.d-none]="c.hideSm" [class.d-md-table-cell]="c.hideSm" [style.width]="c.width ?? null"
                      [class.text-end]="c.align === 'end'" [class.text-center]="c.align === 'center'"
                      [attr.aria-sort]="sort() === c.key ? (dir() === 'asc' ? 'ascending' : 'descending') : null">
                    @if (c.sortable) {
                      <button type="button" class="pm-dt-sort" (click)="toggleSort(c.key)">
                        {{ c.label }}
                        <i class="bi" [class.bi-arrow-down-up]="sort() !== c.key" [class.bi-sort-up]="sort() === c.key && dir() === 'asc'"
                           [class.bi-sort-down]="sort() === c.key && dir() === 'desc'" aria-hidden="true"></i>
                      </button>
                    } @else { {{ c.label }} }
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track rowId()(row)) {
                <tr [class.pm-dt-clickable]="clickable()" (click)="clickable() && rowClick.emit(row)"
                    [attr.tabindex]="clickable() ? 0 : null" (keydown.enter)="clickable() && rowClick.emit(row)">
                  @if (selectable()) {
                    <td class="pm-dt-check" (click)="$event.stopPropagation()">
                      <input class="form-check-input" type="checkbox" [attr.aria-label]="'Select row'" [checked]="isSelected(row)" (change)="toggle(row)" />
                    </td>
                  }
                  @for (c of columns(); track c.key) {
                    <td [class.d-none]="c.hideSm" [class.d-md-table-cell]="c.hideSm" [class.text-end]="c.align === 'end'" [class.text-center]="c.align === 'center'">
                      @if (cell()) {
                        <ng-container *ngTemplateOutlet="cell()!; context: { $implicit: row, col: c }" />
                      } @else { {{ value(row, c.key) }} }
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      @if (total() > 0 && paginated()) {
        <nav class="pm-dt-foot" aria-label="Pagination">
          <span class="small text-body-secondary">{{ from() }}-{{ to() }} of {{ total() }}@if (selected().length) { · {{ selected().length }} selected }</span>
          <div class="d-flex align-items-center gap-2">
            <label class="small text-body-secondary d-none d-sm-inline" for="pm-dt-size">Rows</label>
            <select id="pm-dt-size" class="form-select form-select-sm w-auto" [value]="size()" (change)="sizeChange.emit(+$any($event.target).value)">
              @for (s of [10, 20, 50, 100]; track s) { <option [value]="s" [selected]="s === size()">{{ s }}</option> }
            </select>
            <div class="btn-group btn-group-sm">
              <button type="button" class="btn btn-outline-secondary" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)" aria-label="Previous page">
                <i class="bi bi-chevron-left"></i></button>
              <span class="btn btn-outline-secondary disabled">{{ page() + 1 }} / {{ pages() }}</span>
              <button type="button" class="btn btn-outline-secondary" [disabled]="page() + 1 >= pages()" (click)="pageChange.emit(page() + 1)" aria-label="Next page">
                <i class="bi bi-chevron-right"></i></button>
            </div>
          </div>
        </nav>
      }
    </div>`,
  styles: [`
    .pm-dt { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; overflow: hidden; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); }
    .pm-dt-table thead th { font-size: .7rem; text-transform: uppercase; letter-spacing: .05em; color: var(--pm-muted, #64748b); font-weight: 700;
      background: var(--pm-table-head, #f8fafc); white-space: nowrap; border-bottom: 1px solid var(--pm-divider, #ebedf2); padding: .9rem 1rem; }
    .pm-dt-table td { font-size: .875rem; padding: .85rem 1rem; border-color: var(--pm-divider, #ebedf2); color: var(--pm-text, #1e293b); }
    .pm-dt-table tbody tr:last-child td { border-bottom: 0; }
    .pm-dt-table .fw-semibold { color: var(--pm-heading, #0f172a); white-space: nowrap; }
    .pm-dt-loading { opacity: .55; transition: opacity .15s; }
    .pm-dt-sort { all: unset; cursor: pointer; display: inline-flex; gap: .3rem; align-items: center; }
    .pm-dt-sort:focus-visible { outline: 2px solid #2563eb; border-radius: 3px; }
    .pm-dt-check { width: 2.5rem; }
    .pm-dt-clickable { cursor: pointer; }
    .pm-dt-foot { display: flex; justify-content: space-between; align-items: center; gap: .75rem; flex-wrap: wrap; padding: .8rem 1.1rem;
      border-top: 1px solid var(--pm-divider, #ebedf2); }
  `],
})
export class DataTable<T = Record<string, unknown>> {
  columns = input.required<Column[]>();
  rows = input<T[]>([]);
  total = input(0);
  page = input(0);
  size = input(20);
  sort = input('');
  dir = input<'asc' | 'desc'>('asc');
  loading = input(false);
  selectable = input(false);
  clickable = input(false);
  paginated = input(true);
  rowId = input<(row: T) => string>((row: T) => (row as { id: string }).id);
  cell = input<TemplateRef<{ $implicit: T; col: Column }> | null>(null);
  emptyTitle = input('No records found');
  emptyText = input('Try changing the filters.');
  emptyIcon = input('bi-inbox');
  selected = model<string[]>([]);
  pageChange = output<number>();
  sizeChange = output<number>();
  sortChange = output<{ sort: string; dir: 'asc' | 'desc' }>();
  rowClick = output<T>();

  pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  from = computed(() => this.page() * this.size() + 1);
  to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
  allSelected = computed(() => this.rows().length > 0 && this.rows().every(r => this.selected().includes(this.rowId()(r))));

  value(row: T, key: string): unknown {
    const v = (row as Record<string, unknown>)[key];
    return v === null || v === undefined || v === '' ? '-' : v;
  }
  toggleSort(key: string): void {
    const dir = this.sort() === key && this.dir() === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ sort: key, dir });
  }
  isSelected(row: T): boolean { return this.selected().includes(this.rowId()(row)); }
  toggle(row: T): void {
    const id = this.rowId()(row);
    this.selected.update(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
  }
  toggleAll(): void {
    const ids = this.rows().map(r => this.rowId()(r));
    this.selected.update(s => this.allSelected() ? s.filter(x => !ids.includes(x)) : Array.from(new Set([...s, ...ids])));
  }
}
