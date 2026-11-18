import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const TONES: Record<string, string> = {
  ACTIVE: 'blue', PLACED: 'green', DEBARRED: 'rose', OPTED_OUT: 'slate',
  DRAFT: 'slate', PUBLISHED: 'blue', ONGOING: 'amber', COMPLETED: 'green', CANCELLED: 'rose',
  APPLIED: 'blue', SHORTLISTED: 'violet', WAITLISTED: 'amber', IN_PROCESS: 'amber', SELECTED: 'green', REJECTED: 'rose', WITHDRAWN: 'slate',
  PENDING: 'slate', PASS: 'green', FAIL: 'rose', ABSENT: 'slate', HOLD: 'amber',
  SCHEDULED: 'blue', NO_SHOW: 'rose', OFFERED: 'violet', ACCEPTED: 'green', DECLINED: 'slate', JOINED: 'green', REVOKED: 'rose',
  VERIFIED: 'green', APPROVED: 'green', SIGNED: 'green', IN_PROGRESS: 'amber', EXPIRED: 'rose', NONE: 'slate', SUSPENDED: 'rose',
  OPEN: 'amber', ANSWERED: 'blue', CLOSED: 'slate',
};

/** Colour-coded pill for any enum status. */
@Component({
  selector: 'pm-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="pm-badge pm-badge-{{ tone() }}">{{ text() }}</span>`,
  styles: [`
    .pm-badge { display: inline-block; padding: .2rem .55rem; border-radius: 999px; font-size: .7rem; font-weight: 600; letter-spacing: .02em; white-space: nowrap; }
    .pm-badge-blue { background: rgba(37,99,235,.12); color: #1d4ed8; } .pm-badge-green { background: rgba(22,163,74,.14); color: #15803d; }
    .pm-badge-amber { background: rgba(217,119,6,.16); color: #b45309; } .pm-badge-violet { background: rgba(124,58,237,.14); color: #6d28d9; }
    .pm-badge-rose { background: rgba(225,29,72,.13); color: #be123c; } .pm-badge-slate { background: rgba(100,116,139,.16); color: #475569; }
    :host-context([data-bs-theme=dark]) .pm-badge { filter: brightness(1.45); }
  `],
})
export class StatusBadge {
  value = input<string | null | undefined>('');
  tone = computed(() => TONES[this.value() ?? ''] ?? 'slate');
  text = computed(() => (this.value() ?? '-').replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase()));
}
