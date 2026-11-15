import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';
export interface Toast { id: number; kind: ToastKind; message: string; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  show(message: string, kind: ToastKind = 'info', ms = 4500): void {
    const id = ++this.seq;
    this.toasts.update(t => [...t.slice(-4), { id, kind, message }]);
    setTimeout(() => this.dismiss(id), ms);
  }
  success(m: string) { this.show(m, 'success'); }
  error(m: string) { this.show(m, 'error', 7000); }
  info(m: string) { this.show(m, 'info'); }
  warning(m: string) { this.show(m, 'warning', 6000); }
  dismiss(id: number) { this.toasts.update(t => t.filter(x => x.id !== id)); }
}
