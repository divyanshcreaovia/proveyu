import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'pm-stat-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pm-stat h-100">
      <div class="d-flex align-items-start justify-content-between gap-2">
        <div class="min-w-0">
          <div class="pm-stat-label text-truncate">{{ label() }}</div>
          <div class="pm-stat-value">{{ value() }}</div>
          @if (hint()) { <div class="pm-stat-hint text-truncate">{{ hint() }}</div> }
        </div>
        <span class="pm-stat-icon pm-stat-{{ tone() }}" aria-hidden="true"><i class="bi {{ icon() }}"></i></span>
      </div>
    </div>`,
  styles: [`
    .pm-stat { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 14px; padding: 1.25rem 1.4rem;
      box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); transition: all .25s ease; }
    .pm-stat:hover { transform: translateY(-2px); box-shadow: var(--pm-card-shadow-hover, 0 8px 24px rgba(15,23,42,.06)); }
    .pm-stat-label { font-size: .7rem; text-transform: uppercase; letter-spacing: .05em; color: var(--pm-muted, #64748b); font-weight: 700; }
    .pm-stat-value { font-size: 1.6rem; font-weight: 800; line-height: 1.2; margin-top: .45rem; color: var(--pm-heading, #0f172a); }
    .pm-stat-hint { font-size: .78rem; font-weight: 600; color: var(--pm-muted, #64748b); margin-top: .3rem; }
    .pm-stat-icon { width: 2.5rem; height: 2.5rem; border-radius: 12px; display: grid; place-items: center; font-size: 1.1rem; flex-shrink: 0; }
    .pm-stat-blue { background: rgba(37,99,235,.12); color: #2563eb; } .pm-stat-green { background: rgba(22,163,74,.12); color: #16a34a; }
    .pm-stat-amber { background: rgba(217,119,6,.14); color: #d97706; } .pm-stat-violet { background: rgba(124,58,237,.12); color: #7c3aed; }
    .pm-stat-rose { background: rgba(225,29,72,.12); color: #e11d48; } .pm-stat-slate { background: rgba(100,116,139,.15); color: #64748b; }
  `],
})
export class StatCard {
  label = input.required<string>();
  value = input<string | number | null>('-');
  hint = input('');
  icon = input('bi-graph-up');
  tone = input<'blue' | 'green' | 'amber' | 'violet' | 'rose' | 'slate'>('blue');
}
