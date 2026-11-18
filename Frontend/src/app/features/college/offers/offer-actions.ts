import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { OfferService } from '../services/offer.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Offer, OfferStatus } from '../../../core/models/college.models';

const NEXT: Record<OfferStatus, OfferStatus[]> = {
  OFFERED: ['ACCEPTED', 'DECLINED', 'REVOKED'], ACCEPTED: ['JOINED', 'DECLINED', 'REVOKED'], JOINED: ['REVOKED'], DECLINED: [], REVOKED: [],
};

/** Status transitions + offer letter upload / view for one offer. */
@Component({
  selector: 'pm-offer-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-inline-flex gap-1 flex-wrap justify-content-end">
      @if (offer().offerLetterFileId) { <button class="btn btn-sm btn-outline-secondary" (click)="svc.openLetter(offer().id)" aria-label="View offer letter"><i class="bi bi-file-earmark-pdf"></i></button> }
      @if (college.canWrite()) {
        <label class="btn btn-sm btn-outline-secondary mb-0" [attr.aria-label]="'Upload offer letter'" title="Upload offer letter (PDF)">
          <i class="bi bi-upload"></i><input type="file" accept=".pdf" class="visually-hidden" (change)="upload($event)" /></label>
        @for (s of next(); track s) {
          <button class="btn btn-sm" [class.btn-outline-success]="s === 'ACCEPTED' || s === 'JOINED'" [class.btn-outline-danger]="s === 'REVOKED'"
                  [class.btn-outline-secondary]="s === 'DECLINED'" (click)="change(s)">{{ s.charAt(0) + s.slice(1).toLowerCase() }}</button>
        }
      }
    </div>`,
})
export class OfferActions {
  svc = inject(OfferService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  offer = input.required<Offer>();
  changed = output<void>();
  next = computed(() => NEXT[this.offer().status]);

  async change(s: OfferStatus): Promise<void> {
    const r = await this.confirm.ask({ title: `Mark offer as ${s.toLowerCase()}?`, message: 'Student placement status is recalculated automatically.',
      inputLabel: 'Remarks', inputRequired: s === 'REVOKED', tone: s === 'REVOKED' ? 'danger' : 'primary' });
    if (r.ok) this.svc.changeStatus(this.offer().id, s, r.value || undefined).subscribe(() => { this.toast.success('Offer updated'); this.changed.emit(); });
  }
  upload(ev: Event): void {
    const el = ev.target as HTMLInputElement;
    const f = el.files?.[0];
    el.value = '';
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.pdf')) { this.toast.error('Offer letters must be PDF'); return; }
    this.svc.uploadLetter(this.offer().id, f).subscribe(() => { this.toast.success('Offer letter uploaded'); this.changed.emit(); });
  }
}
