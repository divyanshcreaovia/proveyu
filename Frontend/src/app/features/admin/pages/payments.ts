import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminPurchase, InterviewPaymentsAdminService, PurchaseStatus } from '../../student/services/interview.service';
import { AdminService } from '../services/admin.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { EmptyState } from '../../../shared/ui/states';
import { AppError } from '../../../core/api';

/**
 * Platform ADMIN: students who bought AI interviews by UPI QR. For each one, find the credit in the bank / UPI app by its
 * UTR and amount, then approve (interviews are added) or reject with a reason the student sees.
 */
@Component({
  selector: 'pm-admin-payments',
  imports: [DatePipe, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <div class="d-flex flex-wrap justify-content-end gap-2 mb-2">
        <span class="small text-body-secondary align-self-center me-auto">Approve or reject UPI payments; download all payments for accounts.</span>
        <button class="btn btn-sm btn-outline-secondary" (click)="exportAll('xlsx')"><i class="bi bi-file-earmark-excel me-1"></i>Export Excel</button>
        <button class="btn btn-sm btn-outline-secondary" (click)="exportAll('csv')"><i class="bi bi-filetype-csv me-1"></i>CSV</button>
      </div>

      <div class="alert alert-info small d-flex gap-2 align-items-start">
        <i class="bi bi-info-circle-fill mt-1"></i>
        <span>Students pay by UPI QR and enter the 12-digit UPI reference (UTR). Open your bank or UPI business app, find a credit with
          <strong>the same UTR and amount</strong>, then approve. Approving adds the interviews to the student right away.</span>
      </div>

      <div class="btn-group btn-group-sm mb-3" role="tablist" aria-label="Payment status">
        @for (t of tabs; track t.key) {
          <button type="button" class="btn" role="tab" [attr.aria-selected]="tab() === t.key" [class]="tab() === t.key ? 'btn btn-primary' : 'btn btn-outline-primary'" (click)="show(t.key)">
            {{ t.label }}@if (t.key === 'SUBMITTED' && waiting() > 0) { <span class="badge text-bg-light ms-1">{{ waiting() }}</span> }
          </button>
        }
        <button type="button" class="btn btn-outline-secondary ms-2" (click)="show(tab())" aria-label="Refresh"><i class="bi bi-arrow-clockwise"></i></button>
      </div>

      @if (loading()) { <div class="text-body-secondary small">Loading…</div> }
      @else {
        <div class="vstack gap-2">
          @for (p of rows(); track p.id) {
            <div class="bg-body border rounded-3 p-3 ap-row" [attr.data-id]="p.id">
              <div class="ap-main">
                <div class="fw-semibold">{{ p.studentName }} <span class="text-body-secondary fw-normal small">{{ p.rollNumber }}</span></div>
                <div class="small text-body-secondary text-truncate">{{ p.collegeName }}{{ p.email ? ' · ' + p.email : '' }}</div>
                <div class="small text-body-secondary">Ref {{ p.reference }} · submitted {{ p.submittedAt | date: 'd MMM, h:mm a' }}
                  @if (p.reviewedAt) { · reviewed {{ p.reviewedAt | date: 'd MMM, h:mm a' }} }</div>
                @if (p.note) { <div class="small text-danger">Reason: {{ p.note }}</div> }
              </div>
              <div class="ap-amount">
                <div class="fs-5 fw-bold">₹{{ p.amountRupees }}</div>
                <div class="small text-body-secondary">{{ p.quantity }} interview{{ p.quantity === 1 ? '' : 's' }}</div>
              </div>
              <div class="ap-utr">
                <div class="small text-body-secondary">UTR</div>
                <button type="button" class="btn btn-sm btn-light font-monospace ap-utr-btn" (click)="copy(p.utr)" [attr.aria-label]="'Copy UTR ' + p.utr">
                  {{ p.utr }} <i class="bi bi-copy ms-1"></i></button>
              </div>
              <div class="ap-actions">
                @if (p.status === 'SUBMITTED' || p.status === 'REJECTED') {
                  <button type="button" class="btn btn-sm btn-success" [disabled]="busy() === p.id" (click)="approve(p)"><i class="bi bi-check-lg me-1"></i>Approve</button>
                }
                @if (p.status === 'SUBMITTED') {
                  <button type="button" class="btn btn-sm btn-outline-danger" [disabled]="busy() === p.id" (click)="reject(p)">Reject</button>
                }
                @if (p.status === 'PAID') { <span class="badge text-bg-success">Approved</span> }
              </div>
            </div>
          } @empty {
            <pm-empty-state icon="bi-qr-code" [title]="tab() === 'SUBMITTED' ? 'Nothing to verify' : 'No payments here'"
              [text]="tab() === 'SUBMITTED' ? 'New UPI payments from students will appear here.' : ''" />
          }
        </div>
      }
    </div>`,
  styles: [`
    .ap-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; gap: .75rem 1.25rem; align-items: center; }
    .ap-main { min-width: 0; }
    .ap-amount { text-align: right; }
    .ap-actions { display: flex; gap: .4rem; justify-content: flex-end; }
    .ap-utr-btn { letter-spacing: .04em; }
    @media (max-width: 760px) {
      .ap-row { grid-template-columns: minmax(0, 1fr) auto; }
      .ap-utr { grid-column: 1; } .ap-actions { grid-column: 2; }
    }
  `],
})
export class AdminPaymentsPage implements OnInit {
  private adminApi = inject(AdminService);
  private api = inject(InterviewPaymentsAdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly tabs: { key: PurchaseStatus; label: string }[] = [
    { key: 'SUBMITTED', label: 'To verify' }, { key: 'PAID', label: 'Approved' }, { key: 'REJECTED', label: 'Rejected' },
  ];
  tab = signal<PurchaseStatus>('SUBMITTED');
  rows = signal<AdminPurchase[]>([]);
  waiting = signal(0);
  loading = signal(true);
  busy = signal<string | null>(null);

  ngOnInit(): void { this.show('SUBMITTED'); }

  show(status: PurchaseStatus): void {
    this.tab.set(status);
    this.loading.set(true);
    this.api.list(status).subscribe({
      next: r => { this.rows.set(r); if (status === 'SUBMITTED') this.waiting.set(r.length); this.loading.set(false); },
      error: (e: AppError) => { this.loading.set(false); this.toast.error(e.message); },
    });
  }

  async approve(p: AdminPurchase): Promise<void> {
    const r = await this.confirm.ask({
      title: `Approve ₹${p.amountRupees} from ${p.studentName}?`, confirmText: 'Approve', tone: 'primary',
      message: `Only approve if your bank shows a credit of ₹${p.amountRupees} with UTR ${p.utr}. ${p.quantity} interview${p.quantity === 1 ? '' : 's'} will be added.`,
    });
    if (!r.ok) return;
    this.busy.set(p.id);
    this.api.approve(p.id).subscribe({
      next: () => { this.busy.set(null); this.toast.success(`Approved. ${p.quantity} interview${p.quantity === 1 ? '' : 's'} added for ${p.studentName}.`); this.show(this.tab()); },
      error: (e: AppError) => { this.busy.set(null); this.toast.error(e.message); },
    });
  }

  async reject(p: AdminPurchase): Promise<void> {
    const r = await this.confirm.ask({
      title: 'Reject this payment?', confirmText: 'Reject', tone: 'danger', inputLabel: 'Reason (the student sees this)', inputRequired: true, inputMinLength: 3,
      message: `No credit of ₹${p.amountRupees} with UTR ${p.utr} found? The student is told the reason and can pay again.`,
    });
    if (!r.ok) return;
    this.busy.set(p.id);
    this.api.reject(p.id, r.value.trim()).subscribe({
      next: () => { this.busy.set(null); this.toast.success('Payment rejected.'); this.show(this.tab()); },
      error: (e: AppError) => { this.busy.set(null); this.toast.error(e.message); },
    });
  }

  copy(text?: string): void {
    if (!text) return;
    navigator.clipboard?.writeText(text).then(() => this.toast.info('UTR copied'), () => undefined);
  }

  /** All payments of every status as a file (the list above shows one status at a time). */
  exportAll(format: 'xlsx' | 'csv'): void { this.adminApi.exportPayments(null, format).subscribe(); }
}
