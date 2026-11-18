import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';
import { LoadingService } from '../../core/services/loading.service';

/** Global toasts + thin top progress bar. Rendered once in the app root. */
@Component({
  selector: 'pm-toasts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading.busy()) { <div class="pm-toast-progress" role="progressbar" aria-label="Loading"></div> }
    <div class="pm-toast-stack" aria-live="polite" aria-atomic="false">
      @for (t of toast.toasts(); track t.id) {
        <div class="pm-toast pm-toast-{{ t.kind }} shadow" role="status">
          <i class="bi" [class.bi-check-circle-fill]="t.kind === 'success'" [class.bi-x-octagon-fill]="t.kind === 'error'"
             [class.bi-info-circle-fill]="t.kind === 'info'" [class.bi-exclamation-triangle-fill]="t.kind === 'warning'" aria-hidden="true"></i>
          <span class="flex-grow-1">{{ t.message }}</span>
          <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="toast.dismiss(t.id)"></button>
        </div>
      }
    </div>`,
  styles: [`
    .pm-toast-progress { position: fixed; top: 0; left: 0; height: 3px; width: 100%; z-index: 2000; overflow: hidden;
      background: linear-gradient(90deg, transparent, #2563eb, transparent); background-size: 50% 100%; animation: pm-toast-slide 1s linear infinite; }
    @keyframes pm-toast-slide { from { background-position: -50% 0; } to { background-position: 150% 0; } }
    .pm-toast-stack { position: fixed; right: 1rem; bottom: 1rem; z-index: 2100; display: flex; flex-direction: column; gap: .5rem; max-width: min(420px, calc(100vw - 2rem)); }
    .pm-toast { display: flex; align-items: flex-start; gap: .6rem; padding: .75rem .9rem; border-radius: .6rem; font-size: .875rem;
      background: var(--bs-body-bg); color: var(--bs-body-color); border: 1px solid var(--bs-border-color); border-left-width: 4px; }
    .pm-toast-success { border-left-color: #16a34a; } .pm-toast-success i { color: #16a34a; }
    .pm-toast-error { border-left-color: #dc2626; } .pm-toast-error i { color: #dc2626; }
    .pm-toast-info { border-left-color: #2563eb; } .pm-toast-info i { color: #2563eb; }
    .pm-toast-warning { border-left-color: #d97706; } .pm-toast-warning i { color: #d97706; }
  `],
})
export class ToastContainer {
  toast = inject(ToastService);
  loading = inject(LoadingService);
}
