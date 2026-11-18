import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService, Audience } from '../services/admin.service';
import { CollegeSummary } from '../../../core/models/college.models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';

/** A message from the ProveYu team: an in-app notification (the bell) to a group of users, and optionally an e-mail. */
@Component({
  selector: 'pm-admin-broadcast',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-lg-7">
        <form class="bg-body border rounded-3 p-3" (ngSubmit)="send()">
          <div class="mb-3">
            <span class="form-label small fw-semibold d-block">Send to</span>
            <div class="ab-aud" role="radiogroup" aria-label="Audience">
              @for (a of audiences; track a.key) {
                <button type="button" role="radio" class="ab-opt" [class.sel]="audience() === a.key" [attr.aria-checked]="audience() === a.key"
                  (click)="pick(a.key)"><i class="bi {{ a.icon }}"></i><span><strong>{{ a.label }}</strong><small>{{ a.hint }}</small></span></button>
              }
            </div>
          </div>
          @if (audience() === 'COLLEGE') {
            <div class="mb-3">
              <label class="form-label small fw-semibold" for="ab-college">College</label>
              <select id="ab-college" class="form-select form-select-sm" [ngModel]="collegeId()" (ngModelChange)="collegeId.set($event); count()" name="college">
                <option [ngValue]="null">Choose a college</option>
                @for (c of colleges(); track c.collegeId) { <option [ngValue]="c.collegeId">{{ c.collegeName }} · {{ c.city }}</option> }
              </select>
            </div>
          }
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="ab-title">Title</label>
            <input id="ab-title" class="form-control form-control-sm" maxlength="120" name="title" [ngModel]="title()" (ngModelChange)="title.set($event)" placeholder="e.g. Scheduled maintenance" />
          </div>
          <div class="mb-2">
            <label class="form-label small fw-semibold" for="ab-msg">Message</label>
            <textarea id="ab-msg" class="form-control form-control-sm" rows="5" maxlength="1000" name="message" [ngModel]="message()" (ngModelChange)="message.set($event)"></textarea>
            <div class="form-text text-end">{{ message().length }}/1000</div>
          </div>
          <div class="form-check mb-3">
            <input class="form-check-input" type="checkbox" id="ab-mail" name="mail" [ngModel]="email()" (ngModelChange)="email.set($event)" />
            <label class="form-check-label small" for="ab-mail">Also send it by e-mail</label>
          </div>
          <div class="d-flex align-items-center gap-2">
            <span class="small text-body-secondary">@if (recipients() !== null) { Reaches <strong>{{ recipients() }}</strong> user{{ recipients() === 1 ? '' : 's' }} } </span>
            <button class="btn btn-primary btn-sm ms-auto" [disabled]="!canSend() || busy()">
              @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } <i class="bi bi-send me-1"></i>Send</button>
          </div>
        </form>
      </div>
      <div class="col-lg-5">
        <div class="bg-body border rounded-3 p-3">
          <h2 class="h6 fw-bold">How it reaches people</h2>
          <ul class="small text-body-secondary mb-0 ps-3">
            <li>Everyone gets it in the bell <i class="bi bi-bell"></i> of their portal, as "Title: message".</li>
            <li>With e-mail, it is also sent to their sign-in address, in the background.</li>
            <li>Blocked users are skipped. Platform admins are not included in "Everyone".</li>
            <li>For the website announcement bar, use Settings → Website.</li>
          </ul>
        </div>
      </div>
    </div>`,
  styles: [`
    .ab-aud { display: grid; gap: .5rem; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
    .ab-opt { all: unset; box-sizing: border-box; display: flex; gap: .65rem; align-items: center; padding: .65rem .8rem; border-radius: 12px; cursor: pointer;
      border: 1.5px solid var(--bs-border-color); }
    .ab-opt i { font-size: 1.2rem; color: var(--bs-primary); } .ab-opt span { display: grid; } .ab-opt small { color: var(--bs-secondary-color); font-size: .75rem; }
    .ab-opt.sel { border-color: var(--bs-primary); background: rgba(13,110,253,.06); }
    .ab-opt:focus-visible { outline: 2px solid var(--bs-primary); outline-offset: 2px; }
  `],
})
export class AdminBroadcastPage implements OnInit {
  private svc = inject(AdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  /** ?audience=COLLEGE&collegeId=... from a college's "Message this college". */
  qAudience = input<string>(undefined, { alias: 'audience' });
  qCollege = input<string>(undefined, { alias: 'collegeId' });
  readonly audiences: { key: Audience; label: string; hint: string; icon: string }[] = [
    { key: 'ALL', label: 'Everyone', hint: 'Students, colleges and companies', icon: 'bi-globe2' },
    { key: 'STUDENTS', label: 'All students', hint: 'Every student account', icon: 'bi-mortarboard' },
    { key: 'COLLEGE_STAFF', label: 'All placement cells', hint: 'TPOs, coordinators and viewers', icon: 'bi-building' },
    { key: 'COLLEGE', label: 'One college', hint: 'Its students and placement team', icon: 'bi-bullseye' },
  ];
  audience = signal<Audience>('ALL');
  collegeId = signal<string | null>(null);
  colleges = signal<CollegeSummary[]>([]);
  title = signal('');
  message = signal('');
  email = signal(false);
  recipients = signal<number | null>(null);
  busy = signal(false);
  canSend = computed(() => !!this.title().trim() && !!this.message().trim() && (this.audience() !== 'COLLEGE' || !!this.collegeId()) && (this.recipients() ?? 0) > 0);

  ngOnInit(): void {
    this.svc.colleges('APPROVED').subscribe(c => this.colleges.set(c));
    if (this.qAudience() === 'COLLEGE' && this.qCollege()) { this.audience.set('COLLEGE'); this.collegeId.set(this.qCollege()!); }
    this.count();
  }
  pick(a: Audience): void { this.audience.set(a); this.count(); }
  count(): void {
    this.recipients.set(null);
    if (this.audience() === 'COLLEGE' && !this.collegeId()) return;
    this.svc.recipients(this.audience(), this.collegeId()).subscribe(n => this.recipients.set(n));
  }

  async send(): Promise<void> {
    if (!this.canSend()) return;
    const r = await this.confirm.ask({ title: `Send to ${this.recipients()} user(s)?`, confirmText: 'Send',
      message: `"${this.title().trim()}"${this.email() ? ' goes to their bell and by e-mail.' : ' goes to their bell.'} This cannot be undone.` });
    if (!r.ok) return;
    this.busy.set(true);
    this.svc.broadcast({ audience: this.audience(), collegeId: this.collegeId(), title: this.title().trim(), message: this.message().trim(), sendEmail: this.email() })
      .subscribe({
        next: x => { this.busy.set(false); this.toast.success(`Sent to ${x.recipients} user(s)${x.emails ? `, ${x.emails} e-mail(s) queued` : ''}`); this.title.set(''); this.message.set(''); },
        error: () => this.busy.set(false),
      });
  }
}
