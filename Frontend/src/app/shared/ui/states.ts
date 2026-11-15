import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'pm-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-empty text-center py-5 px-3">
      <i class="bi {{ icon() }} pm-empty-icon" aria-hidden="true"></i>
      <h3 class="h6 fw-semibold mt-3 mb-1">{{ title() }}</h3>
      @if (text()) { <p class="text-body-secondary small mb-3 mx-auto pm-empty-text">{{ text() }}</p> }
      <ng-content />
    </div>`,
  styles: [`.pm-empty-icon { font-size: 1.6rem; color: var(--pm-muted, #64748b); width: 3.5rem; height: 3.5rem; border-radius: 16px; display: inline-grid; place-items: center;
      background: var(--pm-soft, #f1f5f9); }
    h3 { color: var(--pm-heading, #0f172a); font-weight: 800 !important; } .pm-empty-text { max-width: 420px; }`],
})
export class EmptyState {
  icon = input('bi-inbox');
  title = input('Nothing here yet');
  text = input('');
}

@Component({
  selector: 'pm-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="alert alert-danger d-flex align-items-center gap-3 flex-wrap" role="alert">
      <i class="bi bi-exclamation-octagon fs-5" aria-hidden="true"></i>
      <span class="flex-grow-1">{{ message() }}</span>
      <button type="button" class="btn btn-sm btn-outline-danger" (click)="retry.emit()"><i class="bi bi-arrow-clockwise"></i> Retry</button>
    </div>`,
})
export class ErrorState {
  message = input('Something went wrong while loading this page.');
  retry = output<void>();
}

@Component({
  selector: 'pm-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-skel" aria-busy="true" aria-label="Loading">
      @for (r of rowsArr(); track $index) {
        <div class="pm-skel-row" [style.width.%]="100 - ($index % 3) * 12" [style.height.px]="height()"></div>
      }
    </div>`,
  styles: [`
    .pm-skel-row { border-radius: .4rem; margin-bottom: .6rem; background: linear-gradient(90deg, var(--bs-secondary-bg) 25%, var(--bs-tertiary-bg) 50%, var(--bs-secondary-bg) 75%);
      background-size: 200% 100%; animation: pm-skel-shine 1.2s ease-in-out infinite; }
    @keyframes pm-skel-shine { from { background-position: 200% 0; } to { background-position: -200% 0; } }
  `],
})
export class Skeleton {
  rows = input(5);
  height = input(18);
  rowsArr() { return Array.from({ length: this.rows() }); }
}
