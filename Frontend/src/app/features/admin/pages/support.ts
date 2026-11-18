import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { ToastService } from '../../../core/services/toast.service';
import { SupportPage, SupportService, SupportStatus, SupportTicket, categoryLabel } from '../../support/support.service';

/** Support inbox: messages from the college and student portals (Help & support). Read, answer, close. */
@Component({
  selector: 'pm-admin-support',
  imports: [DatePipe, FormsModule, Modal, StatusBadge, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <ul class="nav nav-pills small" role="tablist">
        @for (f of filters; track f.label) {
          <li class="nav-item"><button class="nav-link py-1 px-3" [class.active]="status() === f.key" (click)="setStatus(f.key)">{{ f.label }}
            @if (f.key === 'OPEN' && svc.open()) { <span class="badge rounded-pill text-bg-danger ms-1">{{ svc.open() }}</span> }</button></li>
        }
      </ul>
      <input type="search" class="form-control form-control-sm ms-auto" style="max-width: 300px" placeholder="Search name, e-mail, subject or college"
        aria-label="Search support requests" [ngModel]="q()" (ngModelChange)="search($event)" />
    </div>

    @if (!page()) { <pm-skeleton [rows]="5" /> } @else {
      <div class="table-responsive bg-body border rounded-3">
        <table class="table table-hover align-middle small mb-0">
          <thead><tr><th>Request</th><th class="d-none d-md-table-cell">From</th><th class="d-none d-lg-table-cell">Received</th><th>Status</th></tr></thead>
          <tbody>
            @for (t of page()!.rows; track t.id) {
              <tr class="as-row" tabindex="0" (click)="open(t)" (keydown.enter)="open(t)">
                <td class="as-subj"><strong class="d-block text-truncate">{{ t.subject }}</strong>
                  <span class="text-body-secondary">{{ label(t.category) }}</span></td>
                <td class="d-none d-md-table-cell"><span class="as-portal as-{{ t.portal.toLowerCase() }}">{{ t.portal === 'STUDENT' ? 'Student' : 'College' }}</span>
                  {{ t.userName }}<br><span class="text-body-secondary">{{ t.collegeName || t.userEmail }}</span></td>
                <td class="d-none d-lg-table-cell text-nowrap">{{ t.createdAt | date: 'd MMM, h:mm a' }}</td>
                <td><pm-status [value]="t.status" /></td>
              </tr>
            } @empty {
              <tr><td colspan="4"><pm-empty-state icon="bi-life-preserver" title="No support requests"
                text="Messages from Help & support in the college and student portals appear here." /></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (page()!.total > page()!.size) {
        <div class="d-flex align-items-center justify-content-end gap-2 mt-2 small">
          <span class="text-body-secondary">{{ page()!.page * page()!.size + 1 }}–{{ min(page()!.total, (page()!.page + 1) * page()!.size) }} of {{ page()!.total }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="page()!.page === 0" (click)="go(page()!.page - 1)" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="(page()!.page + 1) * page()!.size >= page()!.total" (click)="go(page()!.page + 1)" aria-label="Next page"><i class="bi bi-chevron-right"></i></button>
        </div>
      }
    }

    <pm-modal [open]="!!sel()" [title]="sel()?.subject ?? ''" variant="drawer" (closed)="sel.set(null)">
      @if (sel(); as t) {
        <div class="d-flex align-items-center gap-2 mb-3"><pm-status [value]="t.status" /><span class="small text-body-secondary">{{ label(t.category) }}</span></div>
        <dl class="as-dl mb-3">
          <dt>From</dt><dd>{{ t.userName }} · <a [href]="'mailto:' + t.userEmail">{{ t.userEmail }}</a></dd>
          <dt>Portal</dt><dd>{{ t.portal === 'STUDENT' ? 'Student' : 'College (' + role(t.userRole) + ')' }}</dd>
          @if (t.collegeName) { <dt>College</dt><dd>{{ t.collegeName }}</dd> }
          <dt>Received</dt><dd>{{ t.createdAt | date: 'd MMM y, h:mm a' }}</dd>
        </dl>
        <div class="as-msg mb-3"><div class="as-who">Message</div><p>{{ t.message }}</p></div>
        @if (t.reply) {
          <div class="as-msg as-reply mb-3"><div class="as-who">Answered by {{ t.repliedBy }} · {{ t.repliedAt | date: 'd MMM, h:mm a' }}</div><p>{{ t.reply }}</p></div>
        }
        <label class="form-label small fw-semibold" for="as-reply">{{ t.reply ? 'Update the answer' : 'Your answer' }}</label>
        <textarea id="as-reply" class="form-control form-control-sm mb-2" rows="6" maxlength="4000" [ngModel]="reply()" (ngModelChange)="reply.set($event)"
          placeholder="The user sees this in Help & support and gets a notification."></textarea>
        <div class="form-check small mb-1"><input class="form-check-input" type="checkbox" id="as-mail" [ngModel]="mail()" (ngModelChange)="mail.set($event)" />
          <label class="form-check-label" for="as-mail">Also e-mail the answer to {{ t.userEmail }}</label></div>
        <div class="form-check small"><input class="form-check-input" type="checkbox" id="as-close" [ngModel]="close()" (ngModelChange)="close.set($event)" />
          <label class="form-check-label" for="as-close">Close the request after answering</label></div>
      }
      <div pmFooter class="d-flex w-100 gap-2">
        @if (sel(); as t) {
          @if (t.status === 'CLOSED') { <button class="btn btn-sm btn-outline-secondary me-auto" (click)="setTicket(t, 'OPEN')"><i class="bi bi-arrow-counterclockwise me-1"></i>Reopen</button> }
          @else { <button class="btn btn-sm btn-outline-secondary me-auto" (click)="setTicket(t, 'CLOSED')"><i class="bi bi-check2-circle me-1"></i>Close without answer</button> }
          <button class="btn btn-sm btn-primary" [disabled]="!reply().trim() || busy()" (click)="answer(t)">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } <i class="bi bi-send me-1"></i>Send answer</button>
        }
      </div>
    </pm-modal>`,
  styles: [`
    .as-row { cursor: pointer; } .as-row:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
    .as-subj { max-width: 360px; }
    .as-portal { display: inline-block; font-size: .65rem; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; padding: .1rem .4rem;
      border-radius: 6px; margin-right: .3rem; }
    .as-student { background: rgba(37,99,235,.12); color: #1d4ed8; } .as-college { background: rgba(217,119,6,.14); color: #b45309; }
    .as-dl { display: grid; grid-template-columns: 6rem 1fr; gap: .35rem .75rem; font-size: .85rem; margin: 0; }
    .as-dl dt { font-weight: 400; color: var(--bs-secondary-color); } .as-dl dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .as-msg { background: var(--bs-tertiary-bg); border-radius: 10px; padding: .7rem .85rem; }
    .as-msg p { margin: 0; white-space: pre-wrap; font-size: .875rem; }
    .as-reply { background: rgba(37,99,235,.08); border-left: 3px solid #2563eb; }
    .as-who { font-size: .72rem; font-weight: 700; color: var(--bs-secondary-color); margin-bottom: .2rem; text-transform: uppercase; letter-spacing: .03em; }
  `],
})
export class AdminSupportPage implements OnInit {
  svc = inject(SupportService);
  private toast = inject(ToastService);
  readonly label = categoryLabel;
  readonly min = Math.min;
  readonly filters: { key: SupportStatus | null; label: string }[] = [
    { key: 'OPEN', label: 'Open' }, { key: 'ANSWERED', label: 'Answered' }, { key: 'CLOSED', label: 'Closed' }, { key: null, label: 'All' },
  ];
  status = signal<SupportStatus | null>('OPEN');
  q = signal('');
  page = signal<SupportPage | null>(null);
  sel = signal<SupportTicket | null>(null);
  reply = signal('');
  mail = signal(true);
  close = signal(false);
  busy = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void { this.load(0); }

  setStatus(s: SupportStatus | null): void { this.status.set(s); this.load(0); }
  search(v: string): void { this.q.set(v); clearTimeout(this.timer); this.timer = setTimeout(() => this.load(0), 300); }
  go(p: number): void { this.load(p); }
  role(r: string): string { return ({ COLLEGE_ADMIN: 'TPO', COLLEGE_COORDINATOR: 'Coordinator', COLLEGE_VIEWER: 'Viewer' } as Record<string, string>)[r] ?? r; }

  open(t: SupportTicket): void { this.sel.set(t); this.reply.set(t.reply ?? ''); this.mail.set(true); this.close.set(false); }

  answer(t: SupportTicket): void {
    this.busy.set(true);
    this.svc.reply(t.id, { reply: this.reply().trim(), close: this.close(), sendEmail: this.mail() }).subscribe({
      next: u => { this.busy.set(false); this.toast.success(`Answer sent to ${u.userName}`); this.sel.set(null); this.load(this.page()?.page ?? 0); },
      error: () => this.busy.set(false),
    });
  }

  setTicket(t: SupportTicket, s: SupportStatus): void {
    this.svc.setStatus(t.id, s).subscribe(u => {
      this.toast.success(s === 'CLOSED' ? 'Request closed' : 'Request reopened');
      this.sel.set(u);
      this.load(this.page()?.page ?? 0);
    });
  }

  private load(p: number): void {
    this.svc.list({ status: this.status(), q: this.q().trim() || undefined, page: p, size: 25 }).subscribe(pg => this.page.set(pg));
    this.svc.refreshOpen();
  }
}
