import { ChangeDetectionStrategy, Component, Injectable, effect, inject, signal, viewChild, ElementRef } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface ConfirmOptions {
  title: string; message: string; confirmText?: string; tone?: 'danger' | 'primary' | 'warning';
  /** When set, the dialog asks for a text input (e.g. a mandatory reason). */
  inputLabel?: string; inputRequired?: boolean; inputMinLength?: number;
}
interface Pending extends ConfirmOptions { resolve: (r: { ok: boolean; value: string }) => void; }

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<Pending | null>(null);
  ask(o: ConfirmOptions): Promise<{ ok: boolean; value: string }> {
    return new Promise(resolve => this.pending.set({ ...o, resolve }));
  }
}

/** Accessible confirm dialog (focus-trapped by modal semantics, Esc to cancel). Rendered once in the app root. */
@Component({
  selector: 'pm-confirm-dialog',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (svc.pending(); as p) {
      <div class="pm-confirm-backdrop" (click)="close(false)"></div>
      <div class="pm-confirm" role="alertdialog" aria-modal="true" aria-labelledby="pm-confirm-title" aria-describedby="pm-confirm-msg" (keydown.escape)="close(false)">
        <h2 id="pm-confirm-title" class="h6 fw-semibold mb-2">{{ p.title }}</h2>
        <p id="pm-confirm-msg" class="text-body-secondary small mb-3">{{ p.message }}</p>
        @if (p.inputLabel) {
          <label for="pm-confirm-input" class="form-label small">{{ p.inputLabel }}</label>
          <textarea id="pm-confirm-input" #input class="form-control form-control-sm mb-3" rows="2" [(ngModel)]="value"></textarea>
        }
        <div class="d-flex justify-content-end gap-2">
          <button #cancel type="button" class="btn btn-sm btn-outline-secondary" (click)="close(false)">Cancel</button>
          <button type="button" class="btn btn-sm" [class]="'btn btn-sm btn-' + (p.tone ?? 'primary')" [disabled]="!valid()" (click)="close(true)">
            {{ p.confirmText ?? 'Confirm' }}
          </button>
        </div>
      </div>
    }`,
  styles: [`
    .pm-confirm-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, .42); z-index: 1990;
      backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); animation: pmConfirmFade .2s ease-out backwards; }
    .pm-confirm { position: fixed; z-index: 1995; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(440px, calc(100vw - 2rem));
      background: var(--bs-body-bg); color: var(--bs-body-color); border: 1px solid var(--bs-border-color-translucent); border-radius: 18px;
      padding: 1.4rem 1.5rem 1.25rem; box-shadow: 0 24px 64px -12px rgba(16,24,40,.3), 0 4px 12px -2px rgba(16,24,40,.08);
      animation: pmConfirmIn .24s cubic-bezier(.2,.8,.2,1) backwards; }
    .pm-confirm h2 { font-size: 1.05rem; font-weight: 750 !important; letter-spacing: -.015em; }
    .pm-confirm p { line-height: 1.55; }
    .pm-confirm > .d-flex { margin: 1.1rem -1.5rem -1.25rem; padding: .85rem 1.5rem; border-top: 1px solid var(--bs-border-color-translucent);
      background: var(--bs-tertiary-bg); border-radius: 0 0 18px 18px; }
    .pm-confirm .btn { border-radius: 50px; font-weight: 600; min-width: 6rem; }
    @keyframes pmConfirmFade { from { opacity: 0; } to { opacity: 1; } }
    @keyframes pmConfirmIn { from { opacity: 0; transform: translate(-50%, -48%) scale(.97); } to { opacity: 1; transform: translate(-50%, -50%); } }
    @media (prefers-reduced-motion: reduce) { .pm-confirm, .pm-confirm-backdrop { animation: none; } }
  `],
})
export class ConfirmDialog {
  svc = inject(ConfirmService);
  value = '';
  private cancelBtn = viewChild<ElementRef<HTMLButtonElement>>('cancel');
  private input = viewChild<ElementRef<HTMLTextAreaElement>>('input');

  constructor() {
    effect(() => {
      if (this.svc.pending()) {
        this.value = '';
        setTimeout(() => (this.input()?.nativeElement ?? this.cancelBtn()?.nativeElement)?.focus());
      }
    });
  }

  valid(): boolean {
    const p = this.svc.pending();
    if (!p?.inputLabel || !p.inputRequired) return true;
    return this.value.trim().length >= (p.inputMinLength ?? 1);
  }

  close(ok: boolean): void {
    const p = this.svc.pending();
    if (!p) return;
    if (ok && !this.valid()) return;
    this.svc.pending.set(null);
    p.resolve({ ok, value: this.value.trim() });
  }
}
