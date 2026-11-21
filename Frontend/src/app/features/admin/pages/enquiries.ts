import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { ToastService } from '../../../core/services/toast.service';
import { Enquiry, EnquiryPage, EnquiryService, EnquiryStatus, orgTypeLabel, studentCountLabel } from '../../public/services/enquiry.service';

/** People who asked for a quote on the public Pricing page. The team calls them back and marks each one contacted or closed. */
@Component({
  selector: 'pm-admin-enquiries',
  imports: [DatePipe, FormsModule, Modal, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <ul class="nav nav-pills small" role="tablist">
        @for (f of filters; track f.label) {
          <li class="nav-item"><button class="nav-link py-1 px-3" [class.active]="status() === f.key" (click)="setFilter(f.key)">{{ f.label }}
            @if (f.key === 'NEW' && svc.fresh()) { <span class="badge rounded-pill text-bg-danger ms-1">{{ svc.fresh() }}</span> }</button></li>
        }
      </ul>
      <input type="search" class="form-control form-control-sm ms-auto" style="max-width: 300px" placeholder="Search name, college, e-mail, phone or city"
        aria-label="Search pricing enquiries" [ngModel]="q()" (ngModelChange)="search($event)" />
    </div>

    @if (!page()) { <pm-skeleton [rows]="5" /> } @else {
      <div class="table-responsive bg-body border rounded-3">
        <table class="table table-hover align-middle small mb-0">
          <thead><tr><th>Organisation</th><th class="d-none d-md-table-cell">Contact</th><th class="d-none d-lg-table-cell">Students</th>
            <th class="d-none d-lg-table-cell">Received</th><th>Status</th></tr></thead>
          <tbody>
            @for (e of page()!.rows; track e.id) {
              <tr class="ae-row" tabindex="0" (click)="open(e)" (keydown.enter)="open(e)">
                <td class="ae-org"><strong class="d-block text-truncate">{{ e.organization }}</strong>
                  <span class="text-body-secondary">{{ orgType(e.orgType) }}{{ e.city ? ' · ' + e.city : '' }}</span></td>
                <td class="d-none d-md-table-cell">{{ e.fullName }}{{ e.designation ? ', ' + e.designation : '' }}<br>
                  <span class="text-body-secondary">{{ e.phone }} · {{ e.email }}</span></td>
                <td class="d-none d-lg-table-cell">{{ students(e.studentCount) || '-' }}</td>
                <td class="d-none d-lg-table-cell text-nowrap">{{ e.createdAt | date: 'd MMM, h:mm a' }}</td>
                <td><pm-status [value]="e.status" /></td>
              </tr>
            } @empty {
              <tr><td colspan="5"><pm-empty-state icon="bi-tag" title="No pricing enquiries"
                text="People who fill the form on the public Pricing page appear here." /></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page()!.total > page()!.size) {
        <div class="d-flex align-items-center justify-content-end gap-2 mt-2 small">
          <span class="text-body-secondary">{{ page()!.page * page()!.size + 1 }}–{{ min(page()!.total, (page()!.page + 1) * page()!.size) }} of {{ page()!.total }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="page()!.page === 0" (click)="load(page()!.page - 1)" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="(page()!.page + 1) * page()!.size >= page()!.total" (click)="load(page()!.page + 1)" aria-label="Next page"><i class="bi bi-chevron-right"></i></button>
        </div>
      }
    }

    <pm-modal [open]="!!sel()" [title]="sel()?.organization ?? ''" variant="drawer" (closed)="sel.set(null)">
      @if (sel(); as e) {
        <div class="d-flex align-items-center gap-2 mb-3"><pm-status [value]="e.status" /><span class="small text-body-secondary">{{ orgType(e.orgType) }}</span></div>
        <dl class="ae-dl mb-3">
          <dt>Name</dt><dd>{{ e.fullName }}{{ e.designation ? ', ' + e.designation : '' }}</dd>
          <dt>Phone</dt><dd><a [href]="'tel:' + e.phone">{{ e.phone }}</a></dd>
          <dt>E-mail</dt><dd><a [href]="'mailto:' + e.email">{{ e.email }}</a></dd>
          @if (e.city) { <dt>City</dt><dd>{{ e.city }}</dd> }
          <dt>Students</dt><dd>{{ students(e.studentCount) || 'Not given' }}</dd>
          <dt>Received</dt><dd>{{ e.createdAt | date: 'd MMM y, h:mm a' }}</dd>
          @if (e.handledBy) { <dt>Handled by</dt><dd>{{ e.handledBy }}</dd> }
        </dl>
        @if (e.message) { <div class="ae-msg mb-3"><div class="ae-who">What they need</div><p>{{ e.message }}</p></div> }
        <label class="form-label small fw-semibold" for="ae-note">Team note</label>
        <textarea id="ae-note" class="form-control form-control-sm" rows="4" maxlength="2000" [ngModel]="note()" (ngModelChange)="note.set($event)"
          placeholder="e.g. Called on 20 Nov, demo on Monday, quote sent"></textarea>
        <div class="form-text">Only the ProveYu team sees this.</div>
      }
      <div pmFooter class="d-flex w-100 gap-2">
        @if (sel(); as e) {
          @if (e.status !== 'NEW') { <button class="btn btn-sm btn-outline-secondary me-auto" [disabled]="busy()" (click)="save(e, 'NEW')"><i class="bi bi-arrow-counterclockwise me-1"></i>Mark new</button> }
          @else { <span class="me-auto"></span> }
          @if (e.status !== 'CLOSED') { <button class="btn btn-sm btn-outline-secondary" [disabled]="busy()" (click)="save(e, 'CLOSED')"><i class="bi bi-x-circle me-1"></i>Close</button> }
          <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="save(e, e.status === 'NEW' ? 'CONTACTED' : e.status)">
            <i class="bi bi-check2-circle me-1"></i>{{ e.status === 'NEW' ? 'Mark contacted' : 'Save note' }}</button>
        }
      </div>
    </pm-modal>`,
  styles: [`
    .ae-row { cursor: pointer; } .ae-row:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
    .ae-org { max-width: 320px; }
    .ae-dl { display: grid; grid-template-columns: 6.5rem 1fr; gap: .35rem .75rem; font-size: .85rem; margin: 0; }
    .ae-dl dt { font-weight: 400; color: var(--bs-secondary-color); } .ae-dl dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .ae-msg { background: var(--bs-tertiary-bg); border-radius: 10px; padding: .7rem .85rem; }
    .ae-msg p { margin: 0; white-space: pre-wrap; font-size: .875rem; }
    .ae-who { font-size: .72rem; font-weight: 700; color: var(--bs-secondary-color); margin-bottom: .2rem; text-transform: uppercase; letter-spacing: .03em; }
  `],
})
export class AdminEnquiriesPage implements OnInit {
  svc = inject(EnquiryService);
  private toast = inject(ToastService);
  readonly orgType = orgTypeLabel;
  readonly students = studentCountLabel;
  readonly min = Math.min;
  readonly filters: { key: EnquiryStatus | null; label: string }[] = [
    { key: 'NEW', label: 'New' }, { key: 'CONTACTED', label: 'Contacted' }, { key: 'CLOSED', label: 'Closed' }, { key: null, label: 'All' },
  ];
  status = signal<EnquiryStatus | null>('NEW');
  q = signal('');
  page = signal<EnquiryPage | null>(null);
  sel = signal<Enquiry | null>(null);
  note = signal('');
  busy = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void { this.load(0); }

  setFilter(s: EnquiryStatus | null): void { this.status.set(s); this.load(0); }
  search(v: string): void { this.q.set(v); clearTimeout(this.timer); this.timer = setTimeout(() => this.load(0), 300); }
  open(e: Enquiry): void { this.sel.set(e); this.note.set(e.adminNote ?? ''); }

  save(e: Enquiry, s: EnquiryStatus): void {
    this.busy.set(true);
    this.svc.setStatus(e.id, s, this.note().trim()).subscribe({
      next: u => {
        this.busy.set(false);
        this.toast.success(s === e.status ? 'Note saved' : `Marked ${s.toLowerCase()}`);
        this.sel.set(u);
        this.load(this.page()?.page ?? 0);
      },
      error: () => this.busy.set(false),
    });
  }

  load(p: number): void {
    this.svc.list({ status: this.status(), q: this.q().trim() || undefined, page: p, size: 25 }).subscribe(pg => this.page.set(pg));
    this.svc.refreshNew();
  }
}
