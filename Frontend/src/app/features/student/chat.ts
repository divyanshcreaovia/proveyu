import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChatMessage, ChatService } from '../../services/chat.service';
import { StudentPortalService } from '../../services/student-portal.service';
import { PortalContact } from '../../core/models/college.models';
import { EmptyState } from '../../shared/ui/states';

/** Real-time chat with the TPO and the coordinators of the student's branch (existing /ws/chat channel). */
@Component({
  selector: 'pm-student-chat',
  imports: [DatePipe, FormsModule, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './student-pages.scss',
  template: `
    <div class="st-chat">
      <aside class="st-card p-2 overflow-auto">
        <div class="small st-muted px-2 mb-2">@if (chat.connected()) { <i class="bi bi-circle-fill text-success"></i> live } @else { <i class="bi bi-circle"></i> offline (messages still send) }</div>
        @for (c of contacts(); track c.userId) {
          <button type="button" class="st-contact" [class.st-contact-active]="peer()?.userId === c.userId" (click)="pick(c)">
            <span class="d-flex align-items-center gap-2"><span class="flex-grow-1 min-w-0">
              <span class="small d-block text-truncate" [class.fw-bold]="chat.unread()[c.userId]" [class.fw-semibold]="!chat.unread()[c.userId]">{{ c.name }}</span>
              <small class="st-muted">{{ c.type === 'TPO' ? 'TPO' : 'Coordinator' }} · {{ c.subtitle }}</small></span>
              @if (chat.unread()[c.userId]; as n) { <span class="chat-unread" [attr.aria-label]="n + ' unread messages'">{{ n > 99 ? '99+' : n }}</span> }
            </span></button>
        } @empty { <p class="small st-muted p-2">Your placement cell hasn't added staff yet.</p> }
      </aside>
      <section class="st-card d-flex flex-column min-h-0">
        @if (!peer()) { <pm-empty-state icon="bi-chat-dots" title="Pick a conversation" text="Ask your TPO or coordinator about drives, documents or offers." /> }
        @else {
          <header class="border-bottom pb-2 mb-2 fw-semibold small">{{ peer()!.name }} <span class="st-muted fw-normal">· {{ peer()!.subtitle }}</span></header>
          <div #scroller class="st-msgs" aria-live="polite">
            @for (m of messages(); track m.id) {
              <div class="st-msg" [class.st-mine]="m.senderId === portal.me()?.userId">
                @if (m.messageType === 'FILE') { <button class="btn btn-link btn-sm p-0" (click)="chat.openAttachment(m.id)"><i class="bi bi-paperclip"></i> {{ m.fileName }}</button> }
                @if (m.message) { <div>{{ m.message }}</div> }
                <small class="opacity-75">{{ m.createdAt | date: 'short' }}</small>
              </div>
            } @empty { <p class="small st-muted text-center my-auto">No messages yet. Start the conversation.</p> }
          </div>
          <form class="d-flex gap-2 mt-2" (ngSubmit)="send()">
            <label class="btn btn-outline-secondary btn-sm mb-0" title="Attach file"><i class="bi bi-paperclip"></i>
              <input type="file" class="visually-hidden" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg" (change)="attach($event)" aria-label="Attach file" /></label>
            <label for="sc-text" class="visually-hidden">Message</label>
            <input id="sc-text" class="form-control form-control-sm" [(ngModel)]="text" name="msg" autocomplete="off" placeholder="Type a message…" />
            <button class="btn btn-primary btn-sm" [disabled]="!text.trim()" aria-label="Send"><i class="bi bi-send"></i></button>
          </form>
        }
      </section>
    </div>`,
  styles: [`
    .st-chat { display: grid; grid-template-columns: 1fr; gap: .75rem; height: calc(100vh - 140px); min-height: 420px; }
    @media (min-width: 992px) { .st-chat { grid-template-columns: 300px 1fr; } }
    .st-contact { all: unset; box-sizing: border-box; display: block; width: 100%; padding: .55rem .75rem; border-radius: 10px; cursor: pointer; }
    .st-contact:hover { background: var(--pm-soft); } .st-contact:focus-visible { outline: 2px solid var(--pm-focus); }
    .st-contact-active { background: rgba(37,99,235,.12); }
    .chat-unread { min-width: 1.35rem; height: 1.35rem; padding: 0 .4rem; border-radius: 999px; background: #22c55e; color: #fff;
      font-size: .72rem; font-weight: 700; display: inline-grid; place-items: center; flex-shrink: 0; }
    .st-msgs { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: .4rem; padding: .25rem; min-height: 0; }
    .st-msg { max-width: 75%; padding: .5rem .75rem; border-radius: 14px; background: var(--pm-soft); align-self: flex-start; font-size: .875rem; word-break: break-word; }
    .st-mine { align-self: flex-end; background: var(--pm-primary); color: var(--pm-primary-text); }
    .st-mine .btn-link { color: inherit; }
    .min-h-0 { min-height: 0; }
  `],
})
export class StudentChat implements OnInit, OnDestroy {
  chat = inject(ChatService);
  portal = inject(StudentPortalService);
  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  contacts = signal<PortalContact[]>([]);
  peer = signal<PortalContact | null>(null);
  messages = signal<ChatMessage[]>([]);
  text = '';

  constructor() {
    this.chat.incoming.pipe(takeUntilDestroyed()).subscribe(m => {
      const p = this.peer();
      if (p && (m.senderId === p.userId || m.receiverId === p.userId) && !this.messages().some(x => x.id === m.id)) {
        this.messages.update(l => [...l, m]);
        this.scrollDown();
        if (m.senderId === p.userId) this.chat.markRead(m.id).subscribe(); // conversation is open: read immediately
      }
    });
  }

  ngOnInit(): void {
    this.chat.connect();
    this.portal.contacts().subscribe(c => {
      // unread conversations first, like a messenger
      this.contacts.set([...c].sort((a, b) => (this.chat.unread()[b.userId] ?? 0) - (this.chat.unread()[a.userId] ?? 0)));
      if (c.length === 1) this.pick(c[0]);
    });
  }
  ngOnDestroy(): void { this.chat.activePeer.set(null); } // the socket stays open for sidebar badges

  pick(c: PortalContact): void {
    this.peer.set(c);
    this.chat.openConversation(c.userId);
    this.chat.history(c.userId).subscribe(m => {
      this.messages.set([...m].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? '')));
      this.scrollDown();
      m.filter(x => x.receiverId === this.portal.me()?.userId && x.status !== 'READ').forEach(x => this.chat.markRead(x.id).subscribe());
    });
  }
  send(): void {
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
