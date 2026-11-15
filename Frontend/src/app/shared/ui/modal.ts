import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Modal dialog or side drawer (variant="drawer"). Content is projected; footer via [pmFooter] slot. */
@Component({
  selector: 'pm-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'open() && closed.emit()' },
  template: `
    @if (open()) {
      <div class="pm-modal-backdrop" (click)="closed.emit()"></div>
      <section class="pm-modal" [class.pm-modal-drawer]="variant() === 'drawer'" [class.pm-modal-lg]="size() === 'lg'" [class.pm-modal-xl]="size() === 'xl'"
               role="dialog" aria-modal="true" [attr.aria-label]="title()">
        <header class="pm-modal-head">
          <h2 class="h6 fw-semibold m-0 text-truncate">{{ title() }}</h2>
          <button type="button" class="btn-close" aria-label="Close" (click)="closed.emit()"></button>
        </header>
        <div class="pm-modal-body"><ng-content /></div>
        <footer class="pm-modal-foot"><ng-content select="[pmFooter]" /></footer>
      </section>
    }`,
  styles: [`
    .pm-modal-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, .45); z-index: 1050; }
    .pm-modal { position: fixed; z-index: 1055; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(560px, calc(100vw - 1.5rem));
      max-height: calc(100vh - 2rem); display: flex; flex-direction: column; background: var(--bs-body-bg); color: var(--bs-body-color);
      border-radius: .75rem; box-shadow: 0 20px 60px rgba(0,0,0,.25); }
    .pm-modal-lg { width: min(800px, calc(100vw - 1.5rem)); }
    .pm-modal-xl { width: min(1140px, calc(100vw - 1.5rem)); }
    .pm-modal-drawer { top: 0; right: 0; left: auto; bottom: 0; transform: none; max-height: 100vh; height: 100vh; border-radius: 0;
      width: min(640px, 100vw); animation: pm-modal-in .18s ease-out; }
    @keyframes pm-modal-in { from { transform: translateX(30px); opacity: .4; } to { transform: none; opacity: 1; } }
    .pm-modal-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .9rem 1.1rem; border-bottom: 1px solid var(--bs-border-color); }
    .pm-modal-body { padding: 1.1rem; overflow-y: auto; flex: 1; }
    .pm-modal-foot:empty { display: none; }
    .pm-modal-foot { padding: .75rem 1.1rem; border-top: 1px solid var(--bs-border-color); display: flex; justify-content: flex-end; gap: .5rem; flex-wrap: wrap; }
  `],
})
export class Modal {
  open = input(false);
  title = input('');
  size = input<'md' | 'lg' | 'xl'>('md');
  variant = input<'modal' | 'drawer'>('modal');
  closed = output<void>();
}
