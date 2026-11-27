import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { CxoService } from '../cxo.service';
import { DashboardSpec } from '../cxo.models';
import { ToastService } from '../../../core/services/toast.service';

/** One click: the dashboard on screen (or any page that gives a file) as a PDF or a PowerPoint board report. */
@Component({
  selector: 'pm-cxo-export',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="xe" (click)="$event.stopPropagation()">
      <button type="button" class="x-btn" (click)="open.set(!open())" [disabled]="busy()" [attr.aria-expanded]="open()">{{ busy() ? 'Preparing…' : 'Export' }} ▾</button>
      @if (open()) {
        <div class="xe-menu" role="menu">
          <button type="button" role="menuitem" (click)="go('pdf')">PDF report</button>
          <button type="button" role="menuitem" (click)="go('pptx')">PowerPoint</button>
        </div>
      }
    </div>`,
  styles: [`
    .xe { position: relative; }
    .xe-menu { position: absolute; right: 0; top: calc(100% + 6px); z-index: 30; min-width: 170px; padding: .3rem; border-radius: 14px; background: #fff;
      border: 1px solid var(--x-line); box-shadow: 0 12px 30px rgba(15, 23, 42, .14); display: grid; }
    .xe-menu button { all: unset; cursor: pointer; padding: .55rem .8rem; border-radius: 9px; font-size: .82rem; font-weight: 600; color: var(--x-text); }
    .xe-menu button:hover { background: var(--x-panel-2); }
  `],
})
export class CxoExport {
  private cxo = inject(CxoService);
  private toast = inject(ToastService);
  /** The dashboard to export (a board report), or null when {@link source} supplies the file. */
  spec = input<DashboardSpec | null>(null);
  prompt = input<string | null>(null);
  /** Another file to export instead of a dashboard, e.g. the NIRF / NAAC page. */
  source = input<((format: 'pdf' | 'pptx') => Observable<Blob>) | null>(null);
  name = input('ProveYu_board_report');

  open = signal(false);
  busy = signal(false);

  go(format: 'pdf' | 'pptx'): void {
    this.open.set(false);
    const src = this.source();
    const spec = this.spec();
    const req = src ? src(format) : spec ? this.cxo.exportReport(format, this.prompt(), spec) : null;
    if (!req) return;
    this.busy.set(true);
    req.subscribe({
      next: blob => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `${this.name().replace(/[^A-Za-z0-9 _-]/g, '').trim().replace(/\s+/g, '_') || 'report'}.${format}`;
        a.click();
        URL.revokeObjectURL(a.href);
        this.busy.set(false);
      },
      error: () => { this.busy.set(false); this.toast.error('Could not create the report. Try again.'); },
    });
  }
}
