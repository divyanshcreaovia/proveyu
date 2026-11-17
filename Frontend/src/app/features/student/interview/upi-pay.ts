import { ChangeDetectionStrategy, Component, ElementRef, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toDataURL } from 'qrcode';
import { AppError } from '../../../core/api';
import { InterviewService, StudentPurchase } from '../../../services/interview.service';

/** What the dialog needs: one open UPI purchase (from a new order or from the student's list). */
export interface UpiPayment { purchaseId: string; reference: string; quantity: number; amountRupees: number; upiUri: string; payeeName: string; upiId: string; }

/**
 * Pay by UPI QR: the QR holds a UPI link with the exact amount, so any UPI app opens it filled in. After paying, the student
 * enters the 12-digit UPI reference (UTR); the payment then waits for the ProveYu team to verify it.
 */
@Component({
  selector: 'pm-upi-pay',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="up-backdrop" (click)="closed.emit()"></div>
    <div class="up-dialog" role="dialog" aria-modal="true" aria-labelledby="up-title" (keydown.escape)="closed.emit()" tabindex="-1" #box>
      <div class="d-flex align-items-start justify-content-between gap-2 mb-2">
        <div>
          <h2 id="up-title" class="h5 fw-bold mb-0">Pay ₹{{ pay().amountRupees }}</h2>
          <div class="small text-body-secondary">{{ pay().quantity }} AI interview{{ pay().quantity === 1 ? '' : 's' }} · Ref {{ pay().reference }}</div>
        </div>
        <button type="button" class="btn-close" aria-label="Close" (click)="closed.emit()"></button>
      </div>

      <div class="up-qr-card">
        <div class="up-brand"><span class="up-logo">P</span> ProveYu</div>
        @if (qr()) {
          <div class="up-qr">
            <img [src]="qr()" width="232" height="232" alt="UPI QR code to pay ProveYu" />
            <span class="up-qr-mark" aria-hidden="true">ProveYu</span>
          </div>
        } @else { <div class="up-qr up-qr-loading"><span class="spinner-border text-secondary"></span></div> }
        <div class="up-amount">₹{{ pay().amountRupees }}.00</div>
        <div class="up-payee">Paying <strong>{{ pay().payeeName }}</strong> · {{ pay().upiId }}</div>
        <div class="up-apps">GPay · PhonePe · Paytm · BHIM · any UPI app</div>
      </div>

      <a class="btn btn-success w-100 mt-3 up-open" [href]="pay().upiUri"><i class="bi bi-phone me-1"></i> Open UPI app to pay ₹{{ pay().amountRupees }}</a>

      <ol class="up-steps small">
        <li>Scan the QR with any UPI app<span class="up-phone-only"> or tap the button above</span>. The amount is filled in, do not change it.</li>
        <li>After paying, open the payment details and copy the <strong>12-digit UPI reference number</strong> (called UTR, UPI Ref No. or Transaction reference).</li>
        <li>Enter it below. Your interviews are added once the ProveYu team verifies the payment.</li>
      </ol>

      <form (ngSubmit)="submit()" class="mt-2">
        <label for="up-utr" class="form-label small fw-semibold mb-1">UPI reference number (UTR)</label>
        <div class="d-flex gap-2">
          <input id="up-utr" name="utr" class="form-control" inputmode="numeric" autocomplete="off" maxlength="16" placeholder="e.g. 412345678901"
            [ngModel]="utr()" (ngModelChange)="utr.set($event); error.set('')" [attr.aria-invalid]="!!error()" aria-describedby="up-utr-help" />
          <button class="btn btn-primary text-nowrap" [disabled]="busy() || digits().length !== 12">
            @if (busy()) { <span class="spinner-border spinner-border-sm"></span> } @else { Submit }
          </button>
        </div>
        <div id="up-utr-help" class="form-text">{{ digits().length }}/12 digits</div>
        @if (error()) { <div class="text-danger small mt-1" role="alert">{{ error() }}</div> }
      </form>
      <button type="button" class="btn btn-link btn-sm px-0 mt-1" (click)="closed.emit()">I'll enter it later</button>
    </div>`,
  styles: [`
    .up-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, .5); z-index: 1980; }
    .up-dialog { position: fixed; z-index: 1985; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(420px, calc(100vw - 1.5rem));
      max-height: calc(100dvh - 1.5rem); overflow-y: auto; background: var(--bs-body-bg); color: var(--bs-body-color);
      border-radius: 1rem; padding: 1.1rem 1.15rem; box-shadow: 0 24px 60px rgba(0,0,0,.3); outline: none; }
    /* The QR card is always white: scanners need dark modules on a light background, also in dark mode. */
    .up-qr-card { background: #fff; color: #111827; border: 1px solid #e5e7eb; border-radius: 14px; padding: .85rem .85rem .75rem; text-align: center; }
    .up-brand { display: inline-flex; align-items: center; gap: .4rem; font-weight: 800; font-size: 1.05rem; color: #0b5cff; margin-bottom: .35rem; }
    .up-logo { width: 1.5rem; height: 1.5rem; border-radius: 7px; display: grid; place-items: center; color: #fff; font-size: .85rem;
      background: linear-gradient(135deg, #7c3aed, #0b5cff); }
    .up-qr { position: relative; width: 232px; height: 232px; margin: 0 auto; }
    .up-qr img { display: block; width: 100%; height: 100%; image-rendering: pixelated; }
    .up-qr-loading { display: grid; place-items: center; }
    .up-qr-mark { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); background: #fff; color: #0b5cff; font-weight: 800;
      font-size: .78rem; padding: .12rem .4rem; border-radius: 6px; border: 1.5px solid #0b5cff; letter-spacing: .01em; }
    .up-amount { font-size: 1.5rem; font-weight: 800; margin-top: .35rem; font-variant-numeric: tabular-nums; }
    .up-payee { font-size: .82rem; color: #374151; word-break: break-all; }
    .up-apps { font-size: .72rem; color: #6b7280; margin-top: .15rem; }
    .up-steps { padding-left: 1.1rem; margin: .9rem 0 .2rem; display: grid; gap: .3rem; }
    /* The deep link only works on a phone with a UPI app. */
    .up-open, .up-phone-only { display: none; }
    @media (pointer: coarse) { .up-open { display: block; } .up-phone-only { display: inline; } }
  `],
})
export class UpiPay implements OnInit {
  private api = inject(InterviewService);
  pay = input.required<UpiPayment>();
  submitted = output<StudentPurchase>();
  closed = output<void>();
  private box = viewChild<ElementRef<HTMLElement>>('box');
  qr = signal<string | null>(null);
  utr = signal('');
  busy = signal(false);
  error = signal('');
  digits = () => (this.utr() || '').replace(/\D/g, '');

  ngOnInit(): void {
    // High error correction: the "ProveYu" mark in the middle covers part of the code and it still scans.
    toDataURL(this.pay().upiUri, { errorCorrectionLevel: 'H', margin: 1, width: 464, color: { dark: '#111827', light: '#ffffff' } })
      .then(url => this.qr.set(url))
      .catch(() => this.error.set('The QR code could not be created. Use the UPI ID shown to pay.'));
    queueMicrotask(() => this.box()?.nativeElement.focus());
  }

  submit(): void {
    const utr = this.digits();
    if (utr.length !== 12 || this.busy()) { this.error.set('The UPI reference number has 12 digits.'); return; }
    this.busy.set(true);
    this.api.submitUtr(this.pay().purchaseId, utr).subscribe({
      next: p => { this.busy.set(false); this.submitted.emit(p); },
      error: (e: AppError) => { this.busy.set(false); this.error.set(e.message); },
    });
  }
}
