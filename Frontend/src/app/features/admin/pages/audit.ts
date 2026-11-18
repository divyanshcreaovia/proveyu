import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminService, AuditRow, Page } from '../services/admin.service';
import { EmptyState } from '../../../shared/ui/states';

/** What each platform admin did and when. */
@Component({
  selector: 'pm-admin-audit',
  imports: [DatePipe, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap gap-2 mb-3">
      <select class="form-select form-select-sm w-auto" aria-label="Action" [value]="action()" (change)="action.set($any($event.target).value); load(0)">
        @for (f of filters; track f.key) { <option [value]="f.key">{{ f.label }}</option> }
      </select>
      @if (data(); as p) { <span class="small text-body-secondary ms-auto align-self-center">{{ p.total }} action{{ p.total === 1 ? '' : 's' }}</span> }
    </div>
    <div class="table-responsive bg-body border rounded-3">
      <table class="table align-middle small mb-0">
        <thead><tr><th>When</th><th>Admin</th><th>Action</th><th>What happened</th></tr></thead>
        <tbody>
          @for (a of data()?.rows ?? []; track a.id) {
            <tr><td class="text-nowrap">{{ a.createdAt | date: 'd MMM y, h:mm a' }}</td><td class="text-nowrap">{{ a.adminName }}</td>
              <td><span class="badge text-bg-light border">{{ a.action }}</span></td><td>{{ a.summary }}</td></tr>
          } @empty { <tr><td colspan="4"><pm-empty-state icon="bi-journal-text" title="No actions yet" /></td></tr> }
        </tbody>
      </table>
    </div>
    @if (data(); as p) {
      @if (p.total > p.size) {
        <nav class="d-flex justify-content-between align-items-center mt-2 small" aria-label="Pages">
          <button class="btn btn-sm btn-outline-secondary" [disabled]="p.page === 0" (click)="load(p.page - 1)">Newer</button>
          <span>Page {{ p.page + 1 }} of {{ pages(p) }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="p.page + 1 >= pages(p)" (click)="load(p.page + 1)">Older</button>
        </nav>
      }
    }`,
})
export class AdminAuditPage implements OnInit {
  private svc = inject(AdminService);
  readonly filters = [
    { key: '', label: 'All actions' }, { key: 'COLLEGE_', label: 'Colleges' }, { key: 'USER_', label: 'Users' }, { key: 'ADMIN_', label: 'Admins' },
    { key: 'PAYMENT_', label: 'Payments' }, { key: 'CREDITS_', label: 'Free interviews' }, { key: 'MOCK_TEST_', label: 'Mock tests' },
    { key: 'SETTINGS_', label: 'Settings' }, { key: 'BROADCAST_', label: 'Broadcasts' }, { key: 'PASSWORD_', label: 'Password resets' },
  ];
  action = signal('');
  data = signal<Page<AuditRow> | null>(null);

  ngOnInit(): void { this.load(0); }
  load(page: number): void { this.svc.audit(this.action() || null, page).subscribe(p => this.data.set(p)); }
  pages(p: Page<AuditRow>): number { return Math.max(1, Math.ceil(p.total / p.size)); }
}
