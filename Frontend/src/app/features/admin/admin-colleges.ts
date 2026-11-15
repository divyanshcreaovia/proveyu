import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../shared/ui/confirm-dialog';
import { ApprovalStatus, CollegeSummary } from '../../core/models/college.models';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EmptyState } from '../../shared/ui/states';

/** Platform ADMIN: approve / reject college registrations. */
@Component({
  selector: 'pm-admin-colleges',
  imports: [DatePipe, FormsModule, StatusBadge, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container py-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 class="h4 fw-bold m-0">College registrations</h1>
        <div class="d-flex gap-2">
          <select class="form-select form-select-sm w-auto" aria-label="Status" [(ngModel)]="status" (change)="load()">
            <option [ngValue]="null">All</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option></select>
          <button class="btn btn-sm btn-outline-secondary" (click)="auth.logout()">Sign out</button>
        </div>
      </div>
      <div class="table-responsive bg-body border rounded-3">
        <table class="table align-middle small mb-0">
          <thead><tr><th>College</th><th class="d-none d-md-table-cell">TPO</th><th class="d-none d-md-table-cell">Registered</th><th>Status</th><th></th></tr></thead>
          <tbody>@for (c of rows(); track c.collegeId) {
            <tr><td><strong>{{ c.collegeName }}</strong><br><span class="text-body-secondary">{{ c.aicteCode }} · {{ c.university }} · {{ c.city }}, {{ c.state }} · &#64;{{ c.emailDomain }}</span></td>
              <td class="d-none d-md-table-cell">{{ c.tpoName }}<br><span class="text-body-secondary">{{ c.tpoEmail }} · {{ c.tpoPhone }}</span></td>
              <td class="d-none d-md-table-cell">{{ c.createdAt | date: 'medium' }}</td>
              <td><pm-status [value]="c.approvalStatus" />@if (c.approvalNote) { <br><small class="text-body-secondary">{{ c.approvalNote }}</small> }</td>
              <td class="text-end text-nowrap">
                @if (c.approvalStatus !== 'APPROVED') { <button class="btn btn-sm btn-success me-1" (click)="decide(c, 'APPROVED')">Approve</button> }
                @if (c.approvalStatus !== 'REJECTED') { <button class="btn btn-sm btn-outline-danger" (click)="decide(c, 'REJECTED')">Reject</button> }</td></tr>
          } @empty { <tr><td colspan="5"><pm-empty-state icon="bi-building-check" title="No colleges in this view" /></td></tr> }</tbody>
        </table>
      </div>
    </div>`,
})
export class AdminColleges implements OnInit {
  private svc = inject(AdminService);
  auth = inject(AuthService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  rows = signal<CollegeSummary[]>([]);
  status: ApprovalStatus | null = 'PENDING';

  ngOnInit(): void { this.load(); }
  load(): void { this.svc.colleges(this.status).subscribe(r => this.rows.set(r)); }
  async decide(c: CollegeSummary, s: ApprovalStatus): Promise<void> {
    const r = await this.confirm.ask({ title: `${s === 'APPROVED' ? 'Approve' : 'Reject'} ${c.collegeName}?`, message: 'The TPO is notified by e-mail.',
      inputLabel: s === 'REJECTED' ? 'Reason' : 'Note (optional)', inputRequired: s === 'REJECTED', tone: s === 'REJECTED' ? 'danger' : 'primary' });
    if (r.ok) this.svc.decide(c.collegeId, s, r.value || undefined).subscribe(() => { this.toast.success(`College ${s.toLowerCase()}`); this.load(); });
  }
}
