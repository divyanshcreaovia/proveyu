import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminService, SettingEntry } from '../services/admin.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { AppError } from '../../../core/api';

/** Platform settings: applied at once, no restart. Each has a default from the server configuration and can be reset to it. */
@Component({
  selector: 'pm-admin-settings',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) { <div class="alert alert-danger small" role="alert">{{ error() }}</div> }
    @for (g of groups(); track g.name) {
      <section class="bg-body border rounded-3 p-3 mb-3">
        <h2 class="h6 fw-bold mb-3">{{ g.name }}</h2>
        @for (s of g.items; track s.key) {
          <div class="as-row">
            <div class="as-label">
              <label class="fw-semibold small" [for]="'s-' + s.key">{{ s.label }}</label>
              <div class="small text-body-secondary">{{ s.help }}</div>
              <div class="as-meta">Default: {{ s.defaultValue === '' ? '(empty)' : s.defaultValue }}
                @if (s.overridden) { · changed {{ s.updatedAt | date: 'd MMM y' }} · <button type="button" class="btn btn-link btn-sm p-0 align-baseline" (click)="reset(s)">Reset</button> }</div>
            </div>
            <div class="as-input">
              @switch (s.type) {
                @case ('BOOL') {
                  <div class="form-check form-switch"><input class="form-check-input" type="checkbox" role="switch" [id]="'s-' + s.key"
                    [checked]="val(s) === 'true'" (change)="edit(s, $any($event.target).checked ? 'true' : 'false')" />
                    <label class="form-check-label small" [for]="'s-' + s.key">{{ val(s) === 'true' ? 'On' : 'Off' }}</label></div>
                }
                @case ('INT') {
                  <input type="number" class="form-control form-control-sm" [id]="'s-' + s.key" [min]="s.min" [max]="s.max" [value]="val(s)"
                    (input)="edit(s, $any($event.target).value)" />
                }
                @default {
                  <input type="text" class="form-control form-control-sm" [id]="'s-' + s.key" [maxLength]="s.max" [value]="val(s)"
                    (input)="edit(s, $any($event.target).value)" />
                }
              }
            </div>
          </div>
        }
      </section>
    }
    <div class="as-bar">
      <span class="small text-body-secondary">@if (dirtyCount()) { {{ dirtyCount() }} unsaved change{{ dirtyCount() === 1 ? '' : 's' }} } @else { All changes saved }</span>
      <button class="btn btn-outline-secondary btn-sm ms-auto" [disabled]="!dirtyCount() || busy()" (click)="edits.set({})">Discard</button>
      <button class="btn btn-primary btn-sm" [disabled]="!dirtyCount() || busy()" (click)="save()">
        @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Save changes</button>
    </div>`,
  styles: [`
    .as-row { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 1rem; align-items: center; padding: .75rem 0; border-top: 1px solid var(--bs-border-color); }
    .as-row:first-of-type { border-top: 0; padding-top: 0; }
    @media (max-width: 767.98px) { .as-row { grid-template-columns: 1fr; gap: .5rem; } }
    .as-meta { font-size: .75rem; color: var(--bs-secondary-color); margin-top: .15rem; }
    .as-bar { position: sticky; bottom: 0; display: flex; gap: .5rem; align-items: center; padding: .75rem 1rem; border-radius: 12px;
      background: var(--bs-body-bg); border: 1px solid var(--bs-border-color); box-shadow: 0 -4px 16px rgba(15,23,42,.06); }
  `],
})
export class AdminSettingsPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  rows = signal<SettingEntry[]>([]);
  edits = signal<Record<string, string>>({});
  busy = signal(false);
  error = signal('');
  groups = computed(() => {
    const out: { name: string; items: SettingEntry[] }[] = [];
    for (const s of this.rows()) { const g = out.find(x => x.name === s.group); if (g) g.items.push(s); else out.push({ name: s.group, items: [s] }); }
    return out;
  });
  dirtyCount = computed(() => Object.entries(this.edits()).filter(([k, v]) => this.rows().find(r => r.key === k)?.value !== v).length);

  ngOnInit(): void { this.svc.settings().subscribe(r => this.rows.set(r)); }
  val(s: SettingEntry): string { return this.edits()[s.key] ?? s.value; }
  edit(s: SettingEntry, v: string): void { this.edits.update(e => ({ ...e, [s.key]: v })); }

  save(): void {
    const changed = Object.fromEntries(Object.entries(this.edits()).filter(([k, v]) => this.rows().find(r => r.key === k)?.value !== v));
    this.busy.set(true);
    this.error.set('');
    this.svc.saveSettings(changed).subscribe({
      next: r => { this.busy.set(false); this.rows.set(r); this.edits.set({}); this.toast.success('Settings saved. They apply right away.'); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }

  async reset(s: SettingEntry): Promise<void> {
    const r = await this.confirm.ask({ title: `Reset "${s.label}"?`, message: `It goes back to the default: ${s.defaultValue === '' ? '(empty)' : s.defaultValue}.`, confirmText: 'Reset' });
    if (r.ok) this.svc.resetSetting(s.key).subscribe(rows => { this.rows.set(rows); this.edits.update(e => { const { [s.key]: _, ...rest } = e; return rest; }); this.toast.success('Reset to default'); });
  }
}
