import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { CxoService } from '../cxo.service';
import { CxoBoard } from '../widgets/cxo-board';
import { SharedBoard, WidgetData } from '../cxo.models';

/** A dashboard opened with a share link: no sign-in, read only, numbers fresh as of opening. */
@Component({
  selector: 'pm-cxo-shared',
  imports: [CxoBoard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../layout/cxo-theme.scss', '../layout/cxo-public.scss'],
  template: `
    <div class="x-app">
      <main class="x-main" style="max-width: 1280px; margin: 0 auto; padding: 1.5rem 1rem">
        @if (loading()) { <div class="x-skel" style="height: 420px; border-radius: 20px"></div> }
        @else if (!b()) {
          <div class="x-card x-empty">This link is not valid any more. Ask the person who shared it for a new one.</div>
        } @else {
          @let board = b()!;
          <header style="margin-bottom: 1.1rem">
            <div class="x-muted" style="font-size: .78rem; font-weight: 600">{{ board.college }} · shared by {{ board.sharedBy }}</div>
            <h1 style="margin: .2rem 0 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -.035em; color: var(--x-text)">{{ board.title }}</h1>
            <div class="x-muted" style="font-size: .76rem; margin-top: .2rem">Read only · numbers as of {{ asOf() }} · link valid until {{ until() }}</div>
          </header>
          <pm-cxo-board [spec]="board.spec" [data]="data()" [insight]="board.insight" [interactive]="false" />
        }
      </main>
    </div>`,
})
export class CxoShared implements OnInit {
  private cxo = inject(CxoService);
  /** Route parameter (component input binding). */
  token = input.required<string>();
  b = signal<SharedBoard | null>(null);
  data = signal<Record<string, WidgetData>>({});
  loading = signal(true);

  asOf = () => new Date(this.b()?.generatedAt ?? Date.now()).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  until = () => new Date(this.b()?.expiresAt ?? Date.now()).toLocaleDateString('en-IN', { dateStyle: 'medium' });

  ngOnInit(): void {
    this.cxo.openShared(this.token()).subscribe({
      next: b => { this.b.set(b); this.data.set(Object.fromEntries(b.data.map(d => [d.widgetId, d]))); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
