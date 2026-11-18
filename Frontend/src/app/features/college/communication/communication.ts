import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, inject, signal, viewChild, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NotificationService } from '../../../core/services/notification.service';
import { ChatMessage, ChatService } from '../../../core/services/chat.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { Announcement, AudienceFilter, ChatContact, StudentStatus } from '../../../core/models/college.models';
import { EmptyState } from '../../../shared/ui/states';

@Component({
  selector: 'pm-communication',
  imports: [FormsModule, DatePipe, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="nav nav-tabs small mb-3" role="tablist">
      <li class="nav-item"><button class="nav-link" role="tab" [class.active]="tab() === 'ann'" (click)="tab.set('ann')">Announcements</button></li>
      <li class="nav-item"><button class="nav-link" role="tab" [class.active]="tab() === 'chat'" (click)="openChat()">Chat
        @if (chat.totalUnread()) { <span class="chat-unread ms-1">{{ chat.totalUnread() }}</span> }</button></li>
    </ul>
    @if (tab() === 'ann') {
      <div class="row g-3">
        @if (college.canWrite()) {
          <div class="col-xl-5"><section class="pm-cm-card">
            <h2 class="h6">New announcement</h2>
            <form (ngSubmit)="send()">
              <label class="form-label small fw-semibold" for="an-title">Title *</label>
              <input id="an-title" class="form-control mb-2" [(ngModel)]="title" name="t" maxlength="200" required />
              <label class="form-label small fw-semibold" for="an-body">Message *</label>
              <textarea id="an-body" class="form-control mb-3" rows="5" [(ngModel)]="body" name="b" required></textarea>
              <fieldset class="mb-2"><legend class="form-label small fw-semibold">Batches</legend>
                @for (b of college.batches(); track b.id) {
                  <div class="form-check form-check-inline"><input class="form-check-input" type="checkbox" [id]="'ab' + b.id" (change)="toggle('batchYears', b.gradYear)" />
                    <label class="form-check-label small" [for]="'ab' + b.id">{{ b.gradYear }}</label></div> }</fieldset>
              <fieldset class="mb-2"><legend class="form-label small fw-semibold">Branches</legend>
                @for (b of college.branches(); track b.id) {
                  <div class="form-check form-check-inline"><input class="form-check-input" type="checkbox" [id]="'abr' + b.id" (change)="toggle('branchIds', b.id)" />
                    <label class="form-check-label small" [for]="'abr' + b.id">{{ b.code }}</label></div> }</fieldset>
              <label class="form-label small fw-semibold" for="an-placed">Placement status</label>
              <select id="an-placed" class="form-select form-select-sm mb-2" [(ngModel)]="filter.placed" name="p" (change)="count()">
                <option [ngValue]="null">Everyone</option><option [ngValue]="false">Unplaced only</option><option [ngValue]="true">Placed only</option></select>
              <div class="form-check mb-3"><input id="an-mail" type="checkbox" class="form-check-input" [(ngModel)]="sendEmail" name="m" />
                <label for="an-mail" class="form-check-label small">Also send by e-mail</label></div>
              <div class="d-flex justify-content-between align-items-center">
                <span class="small text-body-secondary">Recipients: <strong>{{ audience() ?? '…' }}</strong></span>
                <button class="btn btn-primary btn-sm" [disabled]="!title.trim() || !body.trim() || busy()">Send</button>
              </div>
            </form>
          </section></div>
        }
        <div [class]="college.canWrite() ? 'col-xl-7' : 'col-12'"><section class="pm-cm-card">
          <h2 class="h6">Sent announcements</h2>
          @for (a of list(); track a.id) {
            <article class="border-bottom py-2"><div class="d-flex justify-content-between"><strong class="small">{{ a.title }}</strong>
              <small class="text-body-secondary">{{ a.createdAt | date: 'medium' }}</small></div>
              <p class="small mb-1 pm-cm-pre">{{ a.body }}</p>
              <small class="text-body-secondary">{{ a.recipientCount }} recipients · {{ a.readCount ?? 0 }} read · by {{ a.createdByName }} @if (a.sendEmail) { · e-mailed }</small></article>
          } @empty { <pm-empty-state icon="bi-megaphone" title="No announcements yet" /> }
        </section></div>
      </div>
    } @else {
      <div class="pm-cm-chat">
        <aside class="pm-cm-contacts">
          <input type="search" class="form-control form-control-sm mb-2" placeholder="Search people…" aria-label="Search contacts" [(ngModel)]="contactQ" (change)="loadContacts()" />
          <div class="small text-body-secondary mb-1">@if (chat.connected()) { <i class="bi bi-circle-fill text-success"></i> live } @else { <i class="bi bi-circle text-secondary"></i> offline (messages still send) }</div>
          @for (c of contacts(); track c.userId) {
            <button type="button" class="pm-cm-contact" [class.pm-cm-active]="peer()?.userId === c.userId" (click)="pick(c)">
              <span class="d-flex align-items-center gap-2"><span class="flex-grow-1 min-w-0">
                <span class="small d-block text-truncate" [class.fw-bold]="chat.unread()[c.userId]" [class.fw-semibold]="!chat.unread()[c.userId]">{{ c.name }}</span>
                <small class="text-body-secondary">{{ c.type }} · {{ c.subtitle }}</small></span>
                @if (chat.unread()[c.userId]; as n) { <span class="chat-unread" [attr.aria-label]="n + ' unread messages'">{{ n > 99 ? '99+' : n }}</span> }
              </span></button>
          } @empty { <p class="small text-body-secondary">No contacts.</p> }
        </aside>
        <section class="pm-cm-thread">
          @if (!peer()) { <pm-empty-state icon="bi-chat-dots" title="Pick a conversation" text="Chat with students, linked recruiters and your team." /> }
          @else {
            <header class="border-bottom pb-2 mb-2 fw-semibold small">{{ peer()!.name }} <span class="text-body-secondary fw-normal">({{ peer()!.type.toLowerCase() }})</span></header>
            <div #scroller class="pm-cm-msgs" aria-live="polite">
              @for (m of messages(); track m.id) {
                <div class="pm-cm-msg" [class.pm-cm-mine]="m.senderId === college.me()?.userId">
                  @if (m.messageType === 'FILE') { <button class="btn btn-link btn-sm p-0" (click)="chat.openAttachment(m.id)"><i class="bi bi-paperclip"></i> {{ m.fileName }}</button> }
                  @if (m.message) { <div>{{ m.message }}</div> }
                  <small class="opacity-75">{{ m.createdAt | date: 'short' }}</small>
                </div>
              }
            </div>
            <form class="d-flex gap-2 mt-2" (ngSubmit)="sendMsg()">
              <label class="btn btn-outline-secondary btn-sm mb-0" title="Attach file"><i class="bi bi-paperclip"></i>
                <input type="file" class="visually-hidden" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg" (change)="attach($event)" aria-label="Attach file" /></label>
              <label for="cm-text" class="visually-hidden">Message</label>
              <input id="cm-text" class="form-control form-control-sm" [(ngModel)]="text" name="msg" autocomplete="off" placeholder="Type a message…" />
              <button class="btn btn-primary btn-sm" [disabled]="!text.trim()" aria-label="Send"><i class="bi bi-send"></i></button>
            </form>
          }
        </section>
      </div>
    }`,
  styles: [`
    .pm-cm-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }
    .pm-cm-pre { white-space: pre-wrap; }
    .chat-unread { min-width: 1.35rem; height: 1.35rem; padding: 0 .4rem; border-radius: 999px; background: #22c55e; color: #fff;
      font-size: .72rem; font-weight: 700; display: inline-grid; place-items: center; flex-shrink: 0; }
    .pm-cm-chat { display: grid; grid-template-columns: 1fr; gap: .75rem; height: calc(100vh - 220px); min-height: 420px; }
    @media (min-width: 992px) { .pm-cm-chat { grid-template-columns: 300px 1fr; } }
    .pm-cm-contacts, .pm-cm-thread { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1rem; overflow-y: auto; }
    .pm-cm-thread { display: flex; flex-direction: column; }
    @media (max-width: 991.98px) { .pm-cm-contacts { max-height: 220px; } }
    .pm-cm-contact { all: unset; display: block; box-sizing: border-box; width: 100%; padding: .45rem .5rem; border-radius: .4rem; cursor: pointer; }
    .pm-cm-contact:hover, .pm-cm-active { background: rgba(37,99,235,.1); }
    .pm-cm-msgs { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: .4rem; }
    .pm-cm-msg { max-width: 75%; padding: .45rem .65rem; border-radius: .7rem; background: var(--bs-secondary-bg); font-size: .875rem; align-self: flex-start; }
    .pm-cm-mine { align-self: flex-end; background: #2563eb; color: #fff; }
  `],
})
export class Communication implements OnInit, OnDestroy {
  private notifications = inject(NotificationService);
  chat = inject(ChatService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  tab = signal<'ann' | 'chat'>('ann');
  list = signal<Announcement[]>([]);
  audience = signal<number | null>(null);
  busy = signal(false);
  title = ''; body = ''; sendEmail = false;
  filter: AudienceFilter & { batchYears: number[]; branchIds: string[] } = { batchYears: [], branchIds: [], statuses: ['ACTIVE', 'PLACED'] as StudentStatus[], placed: null };
  contacts = signal<ChatContact[]>([]);
  peer = signal<ChatContact | null>(null);
  messages = signal<ChatMessage[]>([]);
  contactQ = ''; text = '';

  constructor() {
    this.chat.incoming.pipe(takeUntilDestroyed()).subscribe(m => {
      const p = this.peer();
      if (p && (m.senderId === p.userId || m.receiverId === p.userId) && !this.messages().some(x => x.id === m.id)) {
        this.messages.update(list => [...list, m]); this.scrollDown();
        if (m.senderId === p.userId) this.chat.markRead(m.id).subscribe(); // conversation is open: read immediately
      }
    });
  }

  /** ?tab=chat opens the chat directly (used by the sidebar unread badge). */
  initialTab = input<string>(undefined, { alias: 'tab' });

  ngOnInit(): void { this.loadList(); this.count(); if (this.initialTab() === 'chat') this.openChat(); }
  ngOnDestroy(): void { this.chat.activePeer.set(null); } // the socket stays open for sidebar badges

  loadList(): void { this.notifications.announcements().subscribe(p => this.list.set(p.content)); }
  toggle(key: 'batchYears' | 'branchIds', v: number | string): void {
    if (key === 'batchYears') {
      const n = v as number;
      this.filter.batchYears = this.filter.batchYears.includes(n) ? this.filter.batchYears.filter(x => x !== n) : [...this.filter.batchYears, n];
    } else {
      const id = v as string;
      this.filter.branchIds = this.filter.branchIds.includes(id) ? this.filter.branchIds.filter(x => x !== id) : [...this.filter.branchIds, id];
    }
    this.count();
  }
  count(): void { if (this.college.canWrite()) this.notifications.audienceCount(this.filter).subscribe(n => this.audience.set(n)); }
  send(): void {
    this.busy.set(true);
    this.notifications.announce({ title: this.title.trim(), body: this.body.trim(), filter: this.filter, sendEmail: this.sendEmail }).subscribe({
      next: a => { this.busy.set(false); this.toast.success(`Sent to ${a.recipientCount} student(s)`); this.title = ''; this.body = ''; this.loadList(); },
      error: () => this.busy.set(false),
    });
  }

  openChat(): void { this.tab.set('chat'); this.chat.connect(); this.loadContacts(); }
  loadContacts(): void {
    // unread conversations first, like a messenger
    this.chat.contacts(this.contactQ).subscribe(c => this.contacts.set([...c].sort((a, b) => (this.chat.unread()[b.userId] ?? 0) - (this.chat.unread()[a.userId] ?? 0))));
  }
  pick(c: ChatContact): void {
    this.peer.set(c);
    this.chat.openConversation(c.userId);
    this.chat.history(c.userId).subscribe(m => {
      this.messages.set([...m].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? '')));
      this.scrollDown();
      m.filter(x => x.receiverId === this.college.me()?.userId && x.status !== 'READ').forEach(x => this.chat.markRead(x.id).subscribe());
    });
  }
  sendMsg(): void {
    const t = this.text.trim();
    if (!t || !this.peer()) return;
    this.text = '';
    this.chat.send(this.peer()!.userId, t).subscribe(m => { if (!this.messages().some(x => x.id === m.id)) this.messages.update(l => [...l, m]); this.scrollDown(); });
  }
  attach(ev: Event): void {
    const el = ev.target as HTMLInputElement;
    const f = el.files?.[0];
    el.value = '';
    if (f && this.peer()) this.chat.sendFile(this.peer()!.userId, f).subscribe(m => { this.messages.update(l => [...l, m]); this.scrollDown(); });
  }
  private scrollDown(): void { setTimeout(() => { const el = this.scroller()?.nativeElement; if (el) el.scrollTop = el.scrollHeight; }); }
}
