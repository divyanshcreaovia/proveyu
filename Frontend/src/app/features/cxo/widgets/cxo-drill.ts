import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { CxoService } from '../cxo.service';
import { Drill, WidgetSpec } from '../cxo.models';

/** "Who are these 19?": the students behind a bar or a number, in a window over the board. */
@Component({
  selector: 'pm-cxo-drill',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="xd-back" (click)="closed.emit()" (keydown.escape)="closed.emit()">
      <section class="xd" role="dialog" aria-modal="true" [attr.aria-label]="title()" (click)="$event.stopPropagation()">
        <header>
          <div><h2>{{ title() }}</h2><div class="xd-sub">{{ subtitle() }}</div></div>
          <button type="button" class="x-btn x-btn-icon" (click)="closed.emit()" aria-label="Close">✕</button>
        </header>
        <div class="xd-body">
          @if (loading()) { <div class="x-skel" style="height: 220px"></div> }
          @else if (error()) { <div class="x-empty">{{ error() }}</div> }
          @else if (data(); as d) {
            @if (d.note) { <div class="x-empty">{{ d.note }}</div> }
            @else if (!d.rows.length) { <div class="x-empty">No students behind this number.</div> }
            @else {
              <table>
                <thead><tr><th>Student</th><th>Roll no.</th><th>Branch</th><th>Batch</th><th class="r">CGPA</th><th>Status</th><th>Placed</th></tr></thead>
                <tbody>
                  @for (r of d.rows; track $index) {
                    <tr><td>{{ r.name || '–' }}</td><td>{{ r.roll || '–' }}</td><td>{{ r.branch || '–' }}</td><td>{{ r.batch ?? '–' }}</td>
                      <td class="r">{{ r.cgpa ?? '–' }}</td><td>{{ pretty(r.status) }}</td><td>{{ r.placed ? 'Yes' : 'No' }}</td></tr>
                  }
                </tbody>
              </table>
              @if (d.truncated) { <div class="xd-cut">Showing the first {{ d.rows.length }} students.</div> }
            }
          }
        </div>
        <footer><span class="x-muted">{{ data()?.rows?.length ?? 0 }} students listed · one row per student, even when a student has several offers</span>
          <button type="button" class="x-btn" [disabled]="!data()?.rows?.length" (click)="csv()">Download CSV</button></footer>
      </section>
    </div>`,
  styles: [`
    .xd-back { position: fixed; inset: 0; z-index: 1050; display: grid; place-items: center; padding: 1rem; background: rgba(15, 23, 42, .45); }
    .xd { width: min(880px, 100%); max-height: 86vh; display: flex; flex-direction: column; background: var(--x-panel, #fff); border-radius: 20px;
      box-shadow: 0 24px 60px rgba(15, 23, 42, .3); overflow: hidden; }
    header { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; padding: 1.1rem 1.3rem .8rem; }
    h2 { margin: 0; font-size: 1.15rem; font-weight: 700; color: var(--x-text); letter-spacing: -.02em; }
    .xd-sub { font-size: .78rem; color: var(--x-muted); margin-top: .15rem; }
    .xd-body { overflow: auto; padding: 0 1.3rem; flex: 1; }
    table { width: 100%; border-collapse: collapse; font-size: .82rem; }
    th { position: sticky; top: 0; background: var(--x-panel, #fff); text-align: left; font-size: .7rem; text-transform: uppercase; letter-spacing: .05em; color: var(--x-muted); padding: .5rem .5rem; border-bottom: 1px solid var(--x-line); }
    td { padding: .5rem; border-bottom: 1px solid var(--x-line); color: var(--x-text); }
    .r { text-align: right; }
    .xd-cut { padding: .7rem 0; font-size: .78rem; color: var(--x-muted); }
    footer { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: .8rem 1.3rem; border-top: 1px solid var(--x-line); font-size: .76rem; }
  `],
})
export class CxoDrill implements OnInit {
  private cxo = inject(CxoService);
  spec = input.required<WidgetSpec>();
  label = input<string | null>(null);
  closed = output<void>();

  loading = signal(true);
  error = signal('');
  data = signal<Drill | null>(null);

  title = () => `Students behind “${this.label() ?? this.spec().title}”`;
  subtitle = () => `${this.spec().title}${this.spec().batchYear ? ' · batch ' + this.spec().batchYear : ''}${this.spec().branchCode ? ' · ' + this.spec().branchCode : ''}`;
  pretty = (s: string | null) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '–');

  ngOnInit(): void {
    this.cxo.drill(this.spec(), this.label()).subscribe({
      next: d => { this.data.set(d); this.loading.set(false); },
      error: () => { this.error.set('Could not load the students. Try again.'); this.loading.set(false); },
    });
  }

  csv(): void {
    const rows = this.data()?.rows ?? [];
    const cell = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const text = [['Student', 'Roll no.', 'Branch', 'Batch', 'CGPA', 'Status', 'Placed'].map(cell).join(','),
      ...rows.map(r => [r.name, r.roll, r.branch, r.batch, r.cgpa, r.status, r.placed ? 'Yes' : 'No'].map(cell).join(','))].join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
    a.download = 'students.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
