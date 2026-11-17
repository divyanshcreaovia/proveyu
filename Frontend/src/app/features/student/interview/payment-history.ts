import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, input, output, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PurchaseStatus, StudentPurchase } from '../../../services/interview.service';

/** The student's interview purchases, newest first, in a dialog: amount, interviews, UPI reference, status and the reason for a rejection. */
@Component({
  selector: 'pm-payment-history',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ph-backdrop" (click)="closed.emit()"></div>
    <div class="ph-dialog" role="dialog" aria-modal="true" aria-labelledby="ph-title" (keydown.escape)="closed.emit()" tabindex="-1" #box>
      <div class="ph-head">
        <div>
          <h2 id="ph-title" class="h5 fw-bold mb-0">Transactions</h2>
          <div class="small text-body-secondary">Your AI interview purchases by UPI</div>
        </div>
        <button type="button" class="btn-close" aria-label="Close" (click)="closed.emit()"></button>
      </div>

      <div class="ph-stats">
        <div><span class="ph-stat-n">₹{{ totals().paid }}</span><span class="ph-stat-l">Paid</span></div>
        <div><span class="ph-stat-n">{{ totals().added }}</span><span class="ph-stat-l">Interviews added</span></div>
        <div><span class="ph-stat-n">{{ totals().open }}</span><span class="ph-stat-l">In progress</span></div>
      </div>

      <ul class="ph-list list-unstyled mb-0" aria-label="Transactions">
        @for (p of purchases(); track p.id) {
          <li class="ph-item" [attr.data-status]="p.status">
            <span class="ph-icon" aria-hidden="true"><i class="bi" [class]="meta[p.status].icon"></i></span>
            <div class="ph-main">
              <div class="ph-line">
                <span class="ph-title">{{ p.quantity }} interview{{ p.quantity === 1 ? '' : 's' }}</span>
                <span class="ph-amt">₹{{ p.amountRupees }}</span>
              </div>
              <div class="ph-meta">
                <span>{{ (p.submittedAt ?? p.createdAt) | date: 'd MMM y, h:mm a' }}</span>
                <span>Ref {{ p.reference }}</span>
                @if (p.utr) { <span>UTR {{ p.utr }}</span> }
              </div>
              @if (p.status === 'REJECTED' && p.note) {
                <div class="ph-note"><i class="bi bi-exclamation-circle me-1"></i>{{ p.note }}</div>
              }
              <div class="ph-foot">
                <span class="ph-badge">{{ meta[p.status].label }}</span>
                @if (p.status === 'PENDING' && p.upiUri) {
                  <button type="button" class="btn btn-sm btn-primary" (click)="pay.emit(p)">Enter UTR</button>
                }
              </div>
            </div>
          </li>
        } @empty {
          <li class="ph-empty">No transactions yet.</li>
        }
      </ul>
      <p class="small text-body-secondary mb-0 mt-3"><i class="bi bi-shield-check me-1"></i>Payments are checked by the ProveYu team, usually within a few hours.
        If a payment was rejected by mistake, contact support with its reference.</p>
    </div>`,
  styles: [`
    .ph-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, .5); z-index: 1970; }
    .ph-dialog { position: fixed; z-index: 1975; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(560px, calc(100vw - 1.5rem));
      max-height: calc(100dvh - 1.5rem); overflow-y: auto; background: var(--bs-body-bg); color: var(--bs-body-color);
      border-radius: 1rem; padding: 1.15rem 1.2rem; box-shadow: 0 24px 60px rgba(0,0,0,.3); outline: none; }
    .ph-head { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; margin-bottom: .9rem; }
    .ph-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .5rem; margin-bottom: 1rem; }
    .ph-stats > div { display: grid; gap: .1rem; padding: .6rem .75rem; border-radius: 12px; background: var(--pm-soft, var(--bs-tertiary-bg)); }
    .ph-stat-n { font-size: 1.15rem; font-weight: 800; font-variant-numeric: tabular-nums; color: var(--pm-heading, inherit); }
    .ph-stat-l { font-size: .72rem; color: var(--bs-secondary-color); }
    .ph-list { display: grid; gap: .5rem; }
    .ph-item { display: flex; gap: .75rem; padding: .75rem .85rem; border: 1px solid var(--bs-border-color); border-radius: 12px; }
    .ph-icon { width: 2.1rem; height: 2.1rem; border-radius: 10px; display: grid; place-items: center; flex-shrink: 0; font-size: 1rem; }
    .ph-main { flex: 1; min-width: 0; display: grid; gap: .25rem; }
    .ph-line { display: flex; justify-content: space-between; gap: .5rem; align-items: baseline; }
    .ph-title { font-weight: 600; }
    .ph-amt { font-weight: 800; font-variant-numeric: tabular-nums; }
    .ph-meta { display: flex; flex-wrap: wrap; gap: .15rem .75rem; font-size: .76rem; color: var(--bs-secondary-color); font-variant-numeric: tabular-nums; }
    .ph-note { font-size: .78rem; color: #e11d48; }
    .ph-foot { display: flex; align-items: center; justify-content: space-between; gap: .5rem; margin-top: .15rem; }
    .ph-badge { font-size: .72rem; font-weight: 700; padding: .18rem .55rem; border-radius: 999px; }
    .ph-empty { text-align: center; padding: 1.5rem; color: var(--bs-secondary-color); }
    [data-status='PAID'] .ph-icon, [data-status='PAID'] .ph-badge { background: rgba(22,163,74,.12); color: #15803d; }
    [data-status='SUBMITTED'] .ph-icon, [data-status='SUBMITTED'] .ph-badge { background: rgba(217,119,6,.12); color: #b45309; }
    [data-status='PENDING'] .ph-icon, [data-status='PENDING'] .ph-badge { background: rgba(11,92,255,.10); color: #0b5cff; }
    [data-status='REJECTED'] .ph-icon, [data-status='REJECTED'] .ph-badge { background: rgba(225,29,72,.10); color: #e11d48; }
    @media (max-width: 420px) { .ph-stats { grid-template-columns: 1fr 1fr; } .ph-stats > div:last-child { grid-column: span 2; } }
  `],
})
export class PaymentHistory {
  purchases = input.required<StudentPurchase[]>();
  closed = output<void>();
  /** Reopen the QR of an unpaid request so the student can enter its UTR. */
  pay = output<StudentPurchase>();
  private box = viewChild.required<ElementRef<HTMLElement>>('box');

  readonly meta: Record<PurchaseStatus, { label: string; icon: string }> = {
    PENDING: { label: 'Awaiting UTR', icon: 'bi-qr-code' },
    SUBMITTED: { label: 'Verifying', icon: 'bi-hourglass-split' },
    PAID: { label: 'Added', icon: 'bi-check-lg' },
    REJECTED: { label: 'Rejected', icon: 'bi-x-lg' },
  };

  totals = computed(() => {
    const ps = this.purchases();
    const paid = ps.filter(p => p.status === 'PAID');
    return {
      paid: paid.reduce((s, p) => s + p.amountRupees, 0),
      added: paid.reduce((s, p) => s + p.quantity, 0),
      open: ps.filter(p => p.status === 'PENDING' || p.status === 'SUBMITTED').length,
    };
  });

  constructor() {
    afterNextRender(() => this.box().nativeElement.focus());
  }
}
