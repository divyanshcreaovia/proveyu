import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../shared/ui/modal';
import { StatusBadge } from '../../shared/ui/status-badge';
import { ToastService } from '../../core/services/toast.service';
import { SUPPORT_CATEGORIES, SUPPORT_EMAIL, SupportCategory, SupportService, SupportTicket, categoryLabel } from './support.service';

/**
 * Help & support popup for the college and student portals: write a message to the ProveYu team like an e-mail, and read the
 * team's answers under "My requests".
 */
@Component({
  selector: 'pm-support-dialog',
  imports: [Modal, StatusBadge, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="open()" title="Help & support" size="lg" (closed)="closed.emit()">
      <div class="sd-tabs mb-3" role="tablist">
        <button type="button" role="tab" class="sd-tab" [class.on]="tab() === 'new'" [attr.aria-selected]="tab() === 'new'" (click)="tab.set('new')">
          <i class="bi bi-pencil-square"></i> New message</button>
        <button type="button" role="tab" class="sd-tab" [class.on]="tab() === 'mine'" [attr.aria-selected]="tab() === 'mine'" (click)="showMine()">
          <i class="bi bi-inbox"></i> My requests @if (tickets().length) { <span class="sd-count">{{ tickets().length }}</span> }</button>
      </div>

      @if (tab() === 'new') {
        <form class="sd-mail" (ngSubmit)="send()" id="pm-support-form">
          <div class="sd-line"><span class="sd-k">To</span><span class="sd-to"><i class="bi bi-life-preserver"></i> ProveYu support
            <small>&lt;{{ email }}&gt;</small></span></div>
          <div class="sd-line"><label class="sd-k" for="sd-cat">Topic</label>
            <select id="sd-cat" class="sd-in" name="category" [ngModel]="category()" (ngModelChange)="category.set($event)">
              @for (c of categories; track c.key) { <option [ngValue]="c.key">{{ c.label }}</option> }
            </select></div>
          <div class="sd-line"><label class="sd-k" for="sd-subj">Subject</label>
            <input id="sd-subj" class="sd-in" name="subject" maxlength="150" placeholder="In a few words, what do you need help with?"
              [ngModel]="subject()" (ngModelChange)="subject.set($event)" /></div>
          <textarea class="sd-body" name="message" rows="8" maxlength="4000" aria-label="Message"
            placeholder="Describe the problem: what you did, what you expected and what happened. Add the drive, test or page name if it helps."
            [ngModel]="message()" (ngModelChange)="message.set($event)"></textarea>
          <div class="d-flex justify-content-between small text-body-secondary mt-1">
            <span>The team answers here, usually within one working day.</span><span>{{ message().length }}/4000</span>
          </div>
        </form>
      } @else {
        @if (loading()) { <div class="text-center py-4"><span class="spinner-border spinner-border-sm"></span></div> }
        @for (t of tickets(); track t.id) {
          <article class="sd-ticket" [class.sd-open]="openId() === t.id">
            <button type="button" class="sd-head" (click)="openId.set(openId() === t.id ? null : t.id)" [attr.aria-expanded]="openId() === t.id">
              <span class="flex-grow-1 min-w-0 text-start"><strong class="d-block text-truncate">{{ t.subject }}</strong>
                <small class="text-body-secondary">{{ label(t.category) }} · {{ t.createdAt | date: 'd MMM, h:mm a' }}</small></span>
              @if (t.reply) { <i class="bi bi-reply-fill text-primary" title="Answered"></i> }
              <pm-status [value]="t.status" /><i class="bi bi-chevron-down sd-chev"></i>
            </button>
            @if (openId() === t.id) {
              <div class="sd-thread">
                <div class="sd-msg"><div class="sd-who">You wrote</div><p>{{ t.message }}</p></div>
                @if (t.reply) {
                  <div class="sd-msg sd-reply"><div class="sd-who"><i class="bi bi-life-preserver"></i> {{ t.repliedBy || 'ProveYu team' }} ·
                    {{ t.repliedAt | date: 'd MMM, h:mm a' }}</div><p>{{ t.reply }}</p></div>
                } @else { <p class="small text-body-secondary mb-0"><i class="bi bi-hourglass-split"></i> Waiting for the team to answer.</p> }
              </div>
            }
          </article>
        } @empty {
          @if (!loading()) { <div class="text-center text-body-secondary py-4"><i class="bi bi-inbox fs-3 d-block mb-1"></i>No requests yet.</div> }
        }
      }

      <div pmFooter class="d-flex w-100 align-items-center gap-2">
        <a class="small text-body-secondary me-auto" [href]="'mailto:' + email">Or e-mail {{ email }}</a>
        @if (tab() === 'new') {
          <button type="button" class="btn btn-outline-secondary btn-sm" (click)="closed.emit()">Cancel</button>
          <button type="submit" form="pm-support-form" class="btn btn-primary btn-sm" [disabled]="!valid() || busy()">
            @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } <i class="bi bi-send me-1"></i>Send</button>
        } @else { <button type="button" class="btn btn-outline-primary btn-sm" (click)="tab.set('new')"><i class="bi bi-plus-lg me-1"></i>New message</button> }
      </div>
    </pm-modal>`,
  styles: [`
    .sd-tabs { display: flex; gap: .25rem; border-bottom: 1px solid var(--bs-border-color); }
    .sd-tab { all: unset; cursor: pointer; padding: .45rem .8rem; font-size: .875rem; font-weight: 600; color: var(--bs-secondary-color);
      border-bottom: 2px solid transparent; margin-bottom: -1px; display: inline-flex; align-items: center; gap: .4rem; }
    .sd-tab.on { color: var(--bs-primary); border-bottom-color: var(--bs-primary); }
    .sd-tab:focus-visible { outline: 2px solid var(--bs-primary); }
    .sd-count { background: var(--bs-secondary-bg); border-radius: 999px; padding: 0 .45rem; font-size: .72rem; }
    .sd-mail { border: 1px solid var(--bs-border-color); border-radius: 12px; overflow: hidden; }
    .sd-line { display: flex; align-items: center; gap: .75rem; padding: .5rem .85rem; border-bottom: 1px solid var(--bs-border-color); }
    .sd-k { width: 4.2rem; flex-shrink: 0; font-size: .8rem; color: var(--bs-secondary-color); margin: 0; }
    .sd-to { display: inline-flex; align-items: center; gap: .35rem; background: rgba(37,99,235,.1); color: #1d4ed8; border-radius: 999px;
      padding: .15rem .6rem; font-size: .82rem; font-weight: 600; }
    .sd-to small { font-weight: 400; opacity: .8; }
    .sd-in { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: inherit; font-size: .9rem; padding: .15rem 0; }
    .sd-body { display: block; width: 100%; border: 0; outline: 0; resize: vertical; padding: .85rem; background: transparent; color: inherit;
      font-size: .9rem; min-height: 180px; }
    .sd-mail:focus-within { border-color: var(--bs-primary); box-shadow: 0 0 0 3px rgba(13,110,253,.12); }
    .sd-ticket { border: 1px solid var(--bs-border-color); border-radius: 12px; margin-bottom: .5rem; overflow: hidden; }
    .sd-head { all: unset; box-sizing: border-box; width: 100%; cursor: pointer; display: flex; align-items: center; gap: .6rem; padding: .65rem .85rem; }
    .sd-head:hover { background: var(--bs-tertiary-bg); } .sd-head:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
    .sd-chev { transition: transform .15s; color: var(--bs-secondary-color); } .sd-open .sd-chev { transform: rotate(180deg); }
    .sd-thread { padding: .25rem .85rem .85rem; display: grid; gap: .5rem; }
    .sd-msg { background: var(--bs-tertiary-bg); border-radius: 10px; padding: .6rem .8rem; }
    .sd-msg p { margin: 0; white-space: pre-wrap; font-size: .875rem; }
    .sd-reply { background: rgba(37,99,235,.08); border-left: 3px solid #2563eb; }
    .sd-who { font-size: .72rem; font-weight: 700; color: var(--bs-secondary-color); margin-bottom: .2rem; text-transform: uppercase; letter-spacing: .03em; }
  `],
})
export class SupportDialog {
  private svc = inject(SupportService);
  private toast = inject(ToastService);
  open = input(false);
  closed = output<void>();

  readonly email = SUPPORT_EMAIL;
  readonly categories = SUPPORT_CATEGORIES;
  readonly label = categoryLabel;
  tab = signal<'new' | 'mine'>('new');
  category = signal<SupportCategory>('ACCOUNT');
  subject = signal('');
  message = signal('');
  busy = signal(false);
  loading = signal(false);
  tickets = signal<SupportTicket[]>([]);
  openId = signal<string | null>(null);

  constructor() {
    effect(() => { if (this.open()) this.load(); });
  }

  valid(): boolean { return this.subject().trim().length > 0 && this.message().trim().length >= 10; }

  showMine(): void { this.tab.set('mine'); this.load(); }

  send(): void {
    if (!this.valid() || this.busy()) return;
    this.busy.set(true);
    this.svc.send({ category: this.category(), subject: this.subject().trim(), message: this.message().trim() }).subscribe({
      next: t => {
        this.busy.set(false);
        this.toast.success('Sent. The ProveYu team will answer here.');
        this.subject.set(''); this.message.set('');
        this.tickets.update(list => [t, ...list]);
        this.openId.set(t.id);
        this.tab.set('mine');
      },
      error: () => this.busy.set(false),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.svc.mine().subscribe({ next: t => { this.tickets.set(t); this.loading.set(false); }, error: () => this.loading.set(false) });
  }
}
