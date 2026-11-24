import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NotificationService } from '../../core/services/notification.service';
import { AppNotification } from '../../core/models/college.models';

const ICONS: Record<string, { icon: string; tone: string }> = {
  CAMPUS_DRIVE: { icon: 'bi-briefcase', tone: 'blue' },
  OFF_CAMPUS: { icon: 'bi-globe2', tone: 'violet' },
  COLLEGE_TEST: { icon: 'bi-pencil-square', tone: 'blue' },
  DRIVE: { icon: 'bi-briefcase', tone: 'blue' },
  ANNOUNCEMENT: { icon: 'bi-megaphone', tone: 'violet' },
  INTERVIEW: { icon: 'bi-calendar-event', tone: 'amber' },
  OFFER: { icon: 'bi-award', tone: 'green' },
  APPLICATION: { icon: 'bi-list-check', tone: 'blue' },
  DOCUMENT: { icon: 'bi-file-earmark-check', tone: 'green' },
};

/** Notification dropdown body shared by the student and college shells: unread items stand out, read ones stay quiet. */
@Component({
  selector: 'pm-notification-panel',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="np-head">
      <span class="np-title">Notifications</span>
      @if (unread()) { <span class="np-new">{{ unread() }} new</span>
        <button type="button" class="np-all" (click)="markAll(); $event.stopPropagation()">Mark all read</button> }
    </div>
    <div class="np-list">
      @for (n of items(); track n.id) {
        <button type="button" class="np-item" [class.np-unread]="!n.read" (click)="open(n); $event.stopPropagation()"
          [attr.aria-label]="(n.read ? '' : 'Unread: ') + n.message">
          <span class="np-icon np-{{ look(n).tone }}"><i class="bi {{ look(n).icon }}"></i></span>
          <span class="np-body">
            <span class="np-msg">{{ n.message }}</span>
            <span class="np-time" [title]="n.createdAt | date: 'medium'">{{ ago(n.createdAt) }}</span>
          </span>
          @if (!n.read) { <span class="np-dot" aria-hidden="true"></span> }
        </button>
      } @empty {
        <div class="np-empty"><i class="bi bi-bell-slash"></i><span>You're all caught up.</span></div>
      }
    </div>`,
  styles: [`
    :host { display: block; }
    .np-head { display: flex; align-items: center; gap: .5rem; padding: .8rem 1rem; border-bottom: 1px solid var(--pm-divider, #ebedf2); }
    .np-title { font-weight: 800; font-size: .92rem; color: var(--pm-heading); margin-right: auto; }
    .np-new { font-size: .68rem; font-weight: 700; padding: .15rem .5rem; border-radius: 999px; background: rgba(37,99,235,.12); color: #2563eb; }
    .np-all { all: unset; cursor: pointer; font-size: .75rem; font-weight: 600; color: var(--pm-link); }
    .np-all:hover { text-decoration: underline; } .np-all:focus-visible { outline: 2px solid var(--pm-focus); border-radius: 4px; }
    .np-list { max-height: min(60vh, 440px); overflow-y: auto; padding: .35rem; }
    .np-item { all: unset; box-sizing: border-box; display: flex; gap: .7rem; align-items: flex-start; width: 100%; padding: .65rem .7rem; border-radius: 12px;
      cursor: pointer; position: relative; }
    .np-item + .np-item { margin-top: 2px; }
    .np-item:hover { background: var(--pm-soft, #f1f5f9); }
    .np-item:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: -2px; }
    .np-unread { background: rgba(37,99,235,.07); }
    .np-unread:hover { background: rgba(37,99,235,.12); }
    .np-icon { width: 2.1rem; height: 2.1rem; border-radius: 10px; display: grid; place-items: center; flex-shrink: 0; font-size: .95rem; }
    .np-blue { background: rgba(37,99,235,.12); color: #2563eb; } .np-violet { background: rgba(124,58,237,.12); color: #7c3aed; }
    .np-amber { background: rgba(217,119,6,.14); color: #b45309; } .np-green { background: rgba(22,163,74,.13); color: #15803d; }
    .np-slate { background: var(--pm-soft, #f1f5f9); color: var(--pm-muted); }
    .np-item:not(.np-unread) .np-icon { opacity: .6; }
    .np-body { flex: 1; min-width: 0; display: grid; gap: .15rem; padding-right: .6rem; }
    .np-msg { font-size: .83rem; line-height: 1.4; color: var(--pm-muted); white-space: normal; overflow-wrap: anywhere; } /* the whole message, never cut */
    .np-unread .np-msg { color: var(--pm-heading); font-weight: 600; }
    .np-time { font-size: .7rem; color: var(--pm-muted); }
    .np-unread .np-time { color: #2563eb; font-weight: 600; }
    .np-dot { position: absolute; top: 50%; right: .6rem; width: .5rem; height: .5rem; margin-top: -.25rem; border-radius: 50%; background: #2563eb; }
    .np-empty { display: grid; justify-items: center; gap: .4rem; padding: 1.6rem 1rem; color: var(--pm-muted); font-size: .85rem; }
    .np-empty i { font-size: 1.4rem; opacity: .6; }
    :host-context([data-bs-theme=dark]) .np-new, :host-context([data-bs-theme=dark]) .np-time { filter: brightness(1.5); }
    :host-context([data-bs-theme=dark]) .np-unread { background: rgba(59,130,246,.14); }
    :host-context([data-bs-theme=dark]) .np-dot { background: #60a5fa; }
  `],
})
export class NotificationPanel {
  private notifications = inject(NotificationService);
  items = computed(() => this.notifications.items().slice(0, 20));
  unread = this.notifications.unread;

  look(n: AppNotification): { icon: string; tone: string } { return ICONS[n.type] ?? { icon: 'bi-bell', tone: 'slate' }; }
  open(n: AppNotification): void { if (!n.read) this.notifications.markRead(n.id).subscribe(); }
  markAll(): void { this.notifications.items().filter(n => !n.read).forEach(n => this.notifications.markRead(n.id).subscribe()); }

  ago(iso: string): string {
    const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
    if (s < 172800) return 'yesterday';
    if (s < 604800) return `${Math.floor(s / 86400)} days ago`;
    return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  }
}
