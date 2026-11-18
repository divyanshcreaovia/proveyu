import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { StudentPortalService } from '../services/student-portal.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Offer } from '../../../core/models/college.models';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';

const POLICY: Record<string, string> = {
  OPEN: 'Your college allows you to keep applying after accepting an offer.',
  ONE_OFFER: 'Your college follows a one-offer policy: after you accept, you cannot apply to further drives.',
  TIER_UPGRADE: 'Your college follows a dream-offer policy: after accepting, you can only apply to drives in a higher tier.',
};

@Component({
  selector: 'pm-student-offers',
  imports: [DatePipe, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <div class="alert alert-light border small"><i class="bi bi-info-circle me-1"></i>{{ policy() }}</div>
    @if (loading()) { <pm-skeleton [rows]="4" /> } @else {
      @for (o of offers(); track o.id) {
        <div class="st-row"><span class="flex-grow-1 small"><strong>{{ o.companyName }}</strong> · {{ o.roleTitle }} · <strong>{{ o.ctcLpa }} LPA</strong>
          @if (o.tierName) { <span class="st-chip ms-1">{{ o.tierName }}</span> }<br>
          <span class="st-muted">Offered {{ o.offerDate | date: 'mediumDate' }} @if (o.joiningDate) { · joining {{ o.joiningDate | date: 'mediumDate' }} }</span></span>
          <pm-status [value]="o.status" />
          @if (o.offerLetterFileId) { <button class="btn btn-sm btn-outline-secondary" (click)="portal.openOfferLetter(o.id)"><i class="bi bi-file-earmark-pdf"></i> Offer letter</button> }
          @if (o.status === 'OFFERED') {
            <button class="btn btn-sm btn-success" (click)="respond(o, true)">Accept</button>
            <button class="btn btn-sm btn-outline-secondary" (click)="respond(o, false)">Decline</button>
          }
        </div>
      } @empty { <pm-empty-state icon="bi-award" title="No offers yet" text="Keep going - your offers will show up here." /> }
    }`,
})
export class StudentOffers implements OnInit {
  portal = inject(StudentPortalService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  loading = signal(true);
  offers = signal<Offer[]>([]);
  policy = () => POLICY[this.portal.me()?.placementPolicy ?? 'OPEN'];

  ngOnInit(): void { this.load(); }
  load(): void { this.portal.offers().subscribe({ next: o => { this.offers.set(o); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  async respond(o: Offer, accept: boolean): Promise<void> {
    const r = await this.confirm.ask({ title: `${accept ? 'Accept' : 'Decline'} offer from ${o.companyName}?`,
      message: accept ? this.policy() : 'This cannot be undone.', tone: accept ? 'primary' : 'danger' });
    if (!r.ok) return;
    this.portal.respond(o.id, accept).subscribe(() => {
      this.toast.success(accept ? 'Offer accepted. Congratulations!' : 'Offer declined');
      this.load();
      this.portal.profile().subscribe(); // status / best offer changed
    });
  }
}
