import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AppError } from '../../../core/api';
import { DIFFICULTY_LABELS, Difficulty, InterviewService, StudentPurchase, InterviewType, Overview, RECOMMENDATION_LABELS, TYPE_LABELS } from '../../../services/interview.service';
import { ToastService } from '../../../services/toast.service';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { UpiPay, UpiPayment } from './upi-pay';

/**
 * Preparation > AI interview: one free interview every few days (with a countdown to the next one), buying extra interviews,
 * starting a voice interview at a chosen level (general role or a specific drive), and past reports.
 */
@Component({
  selector: 'pm-interview-home',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, EmptyState, Skeleton, UpiPay],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <section class="iv-hero st-card mb-3">
      <div class="iv-hero-icon" aria-hidden="true"><i class="bi bi-mic-fill"></i></div>
      <div class="min-w-0 flex-grow-1">
        <h2 class="h5 st-h mb-1">AI mock interview</h2>
        <p class="small st-muted mb-0">Answer out loud like a real interview. The AI interviewer asks questions for your role, follows up on what you say, and gives you a scored report with tips for every answer.</p>
      </div>
      @if (data(); as d) {
        <div class="iv-credits" aria-label="Interviews left">
          <span class="iv-num">{{ d.credits.freeLeft + d.credits.paidBalance }}</span>
          <span class="small st-muted">interview{{ d.credits.freeLeft + d.credits.paidBalance === 1 ? '' : 's' }} available</span>
        </div>
      }
    </section>

    @if (loading()) { <pm-skeleton [rows]="6" /> } @else if (data(); as d) {
      @if (d.openSessionId) {
        <div class="st-card iv-resume mb-3">
          <i class="bi bi-record-circle text-danger"></i>
          <span class="flex-grow-1 small"><strong class="st-h">You have an interview in progress.</strong> Continue where you left off.</span>
          <a class="btn btn-sm btn-primary" [routerLink]="['/student/interview-room', d.openSessionId]">Resume interview</a>
        </div>
      } @else {
        @if (!servicesReady()) {
          <div class="alert alert-warning small d-flex gap-2 align-items-start" role="status">
            <i class="bi bi-exclamation-triangle-fill mt-1"></i>
            <span>The AI interviewer is offline right now, so new interviews cannot start. Please try again later. Your credits are safe.</span>
          </div>
        }
        <div class="iv-plan mb-3">
          <div class="st-card iv-free" [class.ready]="d.credits.freeLeft > 0">
            @if (d.credits.freeLeft > 0) {
              <div class="iv-plan-head"><i class="bi bi-gift-fill"></i> Free interview ready</div>
              <p class="small st-muted mb-0">You get one free interview every {{ d.credits.freeEveryDays }} days. Your next one unlocks {{ d.credits.freeEveryDays }} days after you finish this one.</p>
            } @else if (countdown(); as c) {
              <div class="iv-plan-head"><i class="bi bi-hourglass-split"></i> Next free interview in</div>
              <div class="iv-timer" role="timer" aria-live="off" [attr.aria-label]="c.days + ' days ' + c.hours + ' hours ' + c.minutes + ' minutes'">
                <span><b>{{ c.days }}</b>days</span><span><b>{{ c.hours }}</b>hrs</span><span><b>{{ c.minutes }}</b>min</span><span><b>{{ c.seconds }}</b>sec</span>
              </div>
              <p class="small st-muted mb-0">Unlocks on {{ d.credits.nextFreeAt | date: 'd MMM, h:mm a' }}. One free interview every {{ d.credits.freeEveryDays }} days.</p>
            } @else {
              <div class="iv-plan-head"><i class="bi bi-hourglass-split"></i> Free interview in use</div>
              <p class="small st-muted mb-0">The next one unlocks {{ d.credits.freeEveryDays }} days after you finish it.</p>
            }
          </div>
          <div class="st-card iv-buy">
            <div class="iv-plan-head"><i class="bi bi-bag-plus-fill"></i> Buy interviews · ₹{{ d.credits.pricePerInterview }} each</div>
            <p class="small st-muted mb-2">
              @if (d.credits.paidBalance > 0) { You have <strong class="st-h">{{ d.credits.paidBalance }}</strong> bought interview{{ d.credits.paidBalance === 1 ? '' : 's' }}. They never expire. }
              @else { Practise again without waiting. Bought interviews never expire. }
            </p>
            @if (!d.credits.paymentsReady) {
              <p class="small text-body-secondary mb-0"><i class="bi bi-info-circle me-1"></i>Buying interviews is not available yet. It will open soon.</p>
            } @else {
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <div class="iv-qty" role="group" aria-label="Number of interviews">
                <button type="button" class="btn btn-sm btn-outline-secondary" (click)="setQty(qty() - 1)" [disabled]="qty() <= 1 || buying()" aria-label="One less">−</button>
                <span class="iv-qty-n" aria-live="polite">{{ qty() }}</span>
                <button type="button" class="btn btn-sm btn-outline-secondary" (click)="setQty(qty() + 1)" [disabled]="qty() >= d.credits.maxPerPurchase || buying()" aria-label="One more">+</button>
              </div>
              <button type="button" class="btn btn-sm btn-success" (click)="buy()" [disabled]="buying()">
                @if (buying()) { <span class="spinner-border spinner-border-sm me-1"></span> Processing… } @else { <i class="bi bi-qr-code me-1"></i> Pay ₹{{ qty() * d.credits.pricePerInterview }} }
              </button>
            </div>
            <div class="form-text">Pay by UPI QR (GPay, PhonePe, Paytm…). No extra charges.</div>
            }
            @if (d.purchases.length) {
              <ul class="iv-pays list-unstyled mb-0 mt-2" aria-label="My payments">
                @for (p of d.purchases; track p.id) {
                  <li class="iv-pay" [attr.data-status]="p.status">
                    <span class="flex-grow-1 min-w-0">₹{{ p.amountRupees }} · {{ p.quantity }} interview{{ p.quantity === 1 ? '' : 's' }}
                      <span class="st-muted">· {{ (p.submittedAt ?? p.createdAt) | date: 'd MMM, h:mm a' }}</span>
                      @if (p.status === 'REJECTED' && p.note) { <span class="d-block text-danger">Not verified: {{ p.note }}</span> }
                    </span>
                    @switch (p.status) {
                      @case ('PENDING') { <button type="button" class="btn btn-sm btn-outline-primary" (click)="openPending(p)">Enter UTR</button> }
                      @case ('SUBMITTED') { <span class="st-chip iv-chip-wait"><i class="bi bi-hourglass-split"></i> Verifying</span> }
                      @case ('PAID') { <span class="st-chip iv-chip-ok"><i class="bi bi-check-circle-fill"></i> Added</span> }
                      @case ('REJECTED') { <span class="st-chip iv-chip-bad">Rejected</span> }
                    }
                  </li>
                }
              </ul>
            }
          </div>
        </div>

        <form class="st-card mb-3" (ngSubmit)="start()">
          <h3 class="h6 st-h mb-3">Start a new interview</h3>
          <div class="iv-label">Interview type</div>
          <div class="iv-types mb-3" role="radiogroup" aria-label="Interview type">
            @for (t of types; track t.key) {
              <button type="button" class="iv-type" role="radio" [attr.aria-checked]="type() === t.key" [class.sel]="type() === t.key" (click)="type.set(t.key)">
                <i class="bi" [class]="t.icon"></i><span class="fw-semibold st-h">{{ t.label }}</span><span class="small st-muted">{{ t.hint }}</span>
              </button>
            }
          </div>
          <div class="iv-label" id="iv-level-label">Level</div>
          <div class="iv-levels mb-3" role="radiogroup" aria-labelledby="iv-level-label">
            @for (l of levels; track l.key) {
              <button type="button" class="iv-level" role="radio" [attr.aria-checked]="level() === l.key" [class.sel]="level() === l.key" [attr.data-level]="l.key" (click)="level.set(l.key)">
                <span class="fw-semibold st-h">{{ l.label }}</span><span class="small st-muted">{{ l.hint }}</span>
              </button>
            }
          </div>
          <div class="row g-3 mb-3">
            <div class="col-md-6">
              <label class="iv-label" for="iv-drive">Practise for</label>
              <select id="iv-drive" class="form-select" [ngModel]="driveId()" (ngModelChange)="driveId.set($event)" name="drive">
                <option [ngValue]="null">A general role</option>
                @for (o of d.drives; track o.driveId) {
                  <option [ngValue]="o.driveId">{{ o.companyName ?? o.title }} · {{ o.roleTitle ?? o.title }}{{ o.applied ? ' (applied)' : '' }}</option>
                }
              </select>
              <div class="form-text">Pick a drive and the questions come from its job description.</div>
            </div>
            @if (!driveId()) {
              <div class="col-md-6">
                <label class="iv-label" for="iv-role">Role</label>
                <input id="iv-role" class="form-control" name="role" maxlength="120" [ngModel]="role()" (ngModelChange)="role.set($event)"
                  placeholder="Software Engineer, Data Analyst, ..." />
              </div>
            }
          </div>
          <ul class="iv-how small st-muted mb-3">
            <li>About {{ minutes() }} minutes, 5 main questions plus a couple of follow-ups. You can type instead of speaking.</li>
            <li>Use Chrome or Edge, sit somewhere quiet and allow the microphone when asked.</li>
          </ul>
          <div class="form-check mb-3">
            <input class="form-check-input" type="checkbox" id="iv-consent" name="consent" [ngModel]="consent()" (ngModelChange)="consent.set($event)" />
            <label class="form-check-label small" for="iv-consent">I agree that my answers are recorded and evaluated by AI to give me feedback. My voice and answers may be processed by a third-party AI service for this. Recordings are deleted after 30 days; the transcript and scores stay in my account.</label>
          </div>
          <div class="d-flex align-items-center gap-2 flex-wrap">
            <button class="btn btn-primary" [disabled]="starting() || !consent() || !d.credits.canStart || !servicesReady()">
              @if (starting()) { <span class="spinner-border spinner-border-sm me-1"></span> Preparing your questions… } @else { <i class="bi bi-mic-fill me-1"></i> Start interview }
            </button>
            @if (!d.credits.canStart) {
              <span class="small st-muted">Your free interview is used. Wait for the timer, or buy an interview above for ₹{{ d.credits.pricePerInterview }}.</span>
            } @else if (d.credits.freeLeft > 0) {
              <span class="small st-muted">This uses your free interview.</span>
            } @else {
              <span class="small st-muted">This uses 1 of your {{ d.credits.paidBalance }} bought interview{{ d.credits.paidBalance === 1 ? '' : 's' }}.</span>
            }
          </div>
          @if (starting()) { <p class="small st-muted mt-2 mb-0" aria-live="polite">The AI is reading the role and writing questions for you. This can take up to a minute.</p> }
        </form>
      }

      <h2 class="h6 st-h mt-4 mb-2">My interviews</h2>
      @for (h of d.history; track h.id) {
        <a class="st-row text-decoration-none iv-hist" [routerLink]="h.status === 'IN_PROGRESS' ? ['/student/interview-room', h.id] : ['/student/ai-interview/report', h.id]">
          <span class="iv-hist-icon" aria-hidden="true"><i class="bi bi-person-video3"></i></span>
          <span class="flex-grow-1 small min-w-0"><strong class="st-h">{{ typeLabels[h.type] }} · {{ h.companyName ? h.companyName + ' ' : '' }}{{ h.roleTitle }}</strong><br>
            <span class="st-muted">{{ h.startedAt | date: 'medium' }} · {{ h.answered }} answer{{ h.answered === 1 ? '' : 's' }} · <span class="iv-lv" [attr.data-level]="h.difficulty">{{ levelLabels[h.difficulty] }}</span></span></span>
          @switch (h.status) {
            @case ('COMPLETED') {
              <span class="iv-score" [class.good]="(h.overallScore ?? 0) >= 75" [class.mid]="(h.overallScore ?? 0) >= 55 && (h.overallScore ?? 0) < 75">{{ h.overallScore | number: '1.0-0' }}</span>
              <span class="st-chip d-none d-sm-inline">{{ recLabels[h.recommendation!] }}</span>
            }
            @case ('EVALUATING') { <span class="st-chip">Scoring…</span> }
            @case ('FAILED') { <span class="st-chip text-danger">Scoring failed</span> }
            @default { <span class="st-chip text-danger">In progress</span> }
          }
          <i class="bi bi-chevron-right st-muted"></i>
        </a>
      } @empty {
        <pm-empty-state icon="bi-person-video3" title="No interviews yet" text="Your interview reports will appear here." />
      }
    }
    @if (upi(); as u) { <pm-upi-pay [pay]="u" (submitted)="upiSubmitted($event)" (closed)="closeUpi()" /> }`,
  styles: [`
    .iv-hero { display: flex; gap: 1rem; align-items: center; flex-wrap: wrap;
      background: linear-gradient(120deg, rgba(124,58,237,.10), rgba(11,92,255,.07)), var(--pm-card-bg); }
    .iv-hero-icon { width: 3rem; height: 3rem; border-radius: 14px; display: grid; place-items: center; font-size: 1.35rem; color: #fff;
      background: linear-gradient(135deg, #7c3aed, #0b5cff); flex-shrink: 0; }
    .iv-credits { display: grid; text-align: center; padding: .4rem 1rem; border-radius: 12px; background: var(--pm-soft); }
    .iv-num { font-size: 1.6rem; font-weight: 800; line-height: 1.1; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .iv-resume { display: flex; gap: .75rem; align-items: center; flex-wrap: wrap; border-color: rgba(225,29,72,.35); }
    .iv-label { font-size: .75rem; font-weight: 700; color: var(--pm-label); margin-bottom: .35rem; display: block; }
    .iv-types { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: .6rem; }
    .iv-type { all: unset; box-sizing: border-box; display: grid; gap: .15rem; padding: .85rem 1rem; border-radius: 12px; cursor: pointer;
      border: 1.5px solid var(--pm-card-border); background: var(--pm-card-bg); transition: border-color .12s, background .12s; }
    .iv-type i { font-size: 1.15rem; color: var(--pm-link); }
    .iv-type:hover { border-color: var(--pm-link); }
    .iv-type:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .iv-type.sel { border-color: var(--pm-link); background: rgba(11,92,255,.08); }
    .iv-how { padding-left: 1.1rem; display: grid; gap: .2rem; }
    .iv-plan { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: .75rem; }
    .iv-plan > .st-card { margin: 0; }
    .iv-plan-head { font-weight: 700; color: var(--pm-heading); margin-bottom: .4rem; display: flex; gap: .45rem; align-items: center; }
    .iv-free .iv-plan-head i { color: #d97706; } .iv-free.ready { border-color: rgba(22,163,74,.4); } .iv-free.ready .iv-plan-head i { color: #16a34a; }
    .iv-buy .iv-plan-head i { color: #16a34a; }
    .iv-timer { display: flex; gap: .5rem; margin-bottom: .5rem; }
    .iv-timer span { display: grid; text-align: center; min-width: 3.4rem; padding: .35rem .4rem; border-radius: 10px; background: var(--pm-soft);
      font-size: .7rem; color: var(--pm-label); text-transform: uppercase; letter-spacing: .03em; }
    .iv-timer b { font-size: 1.35rem; line-height: 1.15; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .iv-qty { display: inline-flex; align-items: center; gap: .4rem; }
    .iv-qty-n { min-width: 1.6rem; text-align: center; font-weight: 700; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .iv-levels { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .5rem; }
    .iv-level { all: unset; box-sizing: border-box; display: grid; gap: .1rem; padding: .6rem .8rem; border-radius: 12px; cursor: pointer; min-width: 0;
      border: 1.5px solid var(--pm-card-border); background: var(--pm-card-bg); }
    .iv-level:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .iv-level:hover { border-color: var(--pm-link); }
    .iv-level.sel[data-level='EASY'] { border-color: #16a34a; background: rgba(22,163,74,.08); }
    .iv-level.sel[data-level='MEDIUM'] { border-color: #d97706; background: rgba(217,119,6,.08); }
    .iv-level.sel[data-level='HARD'] { border-color: #e11d48; background: rgba(225,29,72,.08); }
    .iv-lv { font-weight: 600; } .iv-lv[data-level='EASY'] { color: #16a34a; } .iv-lv[data-level='MEDIUM'] { color: #d97706; } .iv-lv[data-level='HARD'] { color: #e11d48; }
    .iv-pays { display: grid; gap: .35rem; border-top: 1px solid var(--pm-card-border); padding-top: .5rem; }
    .iv-pay { display: flex; align-items: center; gap: .5rem; font-size: .8rem; }
    .iv-chip-wait { color: #b45309; } .iv-chip-ok { color: #15803d; } .iv-chip-bad { color: #e11d48; }
    @media (max-width: 420px) { .iv-levels { grid-template-columns: 1fr; } .iv-timer span { min-width: 0; flex: 1; } }
    .iv-hist:hover { border-color: var(--pm-link); }
    .iv-hist-icon { width: 2.25rem; height: 2.25rem; border-radius: 10px; display: grid; place-items: center; background: var(--pm-soft); color: var(--pm-link); flex-shrink: 0; }
    .iv-score { font-weight: 800; font-size: 1.1rem; font-variant-numeric: tabular-nums; color: #e11d48; min-width: 2.5rem; text-align: right; }
    .iv-score.mid { color: #d97706; } .iv-score.good { color: #16a34a; }
  `],
})
export class InterviewHome implements OnInit {
  private api = inject(InterviewService);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly typeLabels = TYPE_LABELS;
  readonly levelLabels = DIFFICULTY_LABELS;
  readonly levels: { key: Difficulty; label: string; hint: string }[] = [
    { key: 'EASY', label: 'Easy', hint: 'Basics, a gentle start' },
    { key: 'MEDIUM', label: 'Medium', hint: 'A normal campus interview' },
    { key: 'HARD', label: 'Hard', hint: 'Deep why/how and scenarios' },
  ];
  readonly recLabels = RECOMMENDATION_LABELS;
  readonly types: { key: InterviewType; label: string; hint: string; icon: string }[] = [
    { key: 'MIXED', label: 'Technical + HR', hint: 'Like a real campus interview', icon: 'bi-shuffle' },
    { key: 'TECHNICAL', label: 'Technical', hint: 'Skills, projects, problem solving', icon: 'bi-cpu' },
    { key: 'HR', label: 'HR', hint: 'About you, teamwork, goals', icon: 'bi-people' },
  ];
  loading = signal(true);
  data = signal<Overview | null>(null);
  type = signal<InterviewType>('MIXED');
  driveId = signal<string | null>(null);
  role = signal('');
  consent = signal(false);
  starting = signal(false);
  level = signal<Difficulty>('MEDIUM');
  qty = signal(1);
  buying = signal(false);
  upi = signal<UpiPayment | null>(null);
  private now = signal(Date.now());
  private unlockedFor: string | null = null;
  /** Live countdown to the next free interview; null when none is pending. */
  countdown = computed(() => {
    const at = this.data()?.credits.nextFreeAt;
    if (!at) return null;
    const ms = Math.max(0, new Date(at).getTime() - this.now());
    const t = Math.floor(ms / 1000), pad = (n: number) => String(n).padStart(2, '0');
    return { days: Math.floor(t / 86400), hours: pad(Math.floor(t / 3600) % 24), minutes: pad(Math.floor(t / 60) % 60), seconds: pad(t % 60), done: ms === 0 };
  });
  servicesReady = computed(() => { const s = this.data()?.services; return !!s && s.llm && s.speechToText; });
  minutes = computed(() => (this.type() === 'HR' ? 10 : 15));

  constructor() {
    const timer = setInterval(() => {
      this.now.set(Date.now());
      const at = this.data()?.credits.nextFreeAt;
      if (at && this.countdown()?.done && this.unlockedFor !== at) { this.unlockedFor = at; this.reload(); } // the free interview just unlocked
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading.set(this.data() === null);
    this.api.overview().subscribe({ next: d => { this.data.set(d); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  /** Reopen the QR of a payment the student has not entered the UTR for. */
  openPending(p: StudentPurchase): void {
    if (!p.upiUri) return;
    this.upi.set({ purchaseId: p.id, reference: p.reference, quantity: p.quantity, amountRupees: p.amountRupees, upiUri: p.upiUri,
      payeeName: p.payeeName ?? 'ProveYu', upiId: p.upiId ?? '' });
  }

  upiSubmitted(p: StudentPurchase): void {
    this.upi.set(null);
    this.toast.success(`Payment submitted (UTR ${p.utr}). Your ${p.quantity === 1 ? 'interview is' : p.quantity + ' interviews are'} added once it is verified.`);
    this.qty.set(1);
    this.reload();
  }

  closeUpi(): void {
    this.upi.set(null);
    this.reload(); // the open payment now shows in the list with "Enter UTR"
  }

  setQty(n: number): void {
    const max = this.data()?.credits.maxPerPurchase ?? 10;
    this.qty.set(Math.min(max, Math.max(1, n)));
  }

  /** Opens the UPI QR for the chosen number of interviews (pressing Pay again reuses the open request). */
  buy(): void {
    if (this.buying()) return;
    this.buying.set(true);
    this.api.buy(this.qty()).subscribe({
      next: o => {
        this.buying.set(false);
        this.upi.set({ purchaseId: o.purchaseId, reference: o.reference, quantity: o.quantity, amountRupees: o.amountRupees, upiUri: o.upiUri,
          payeeName: o.payeeName, upiId: o.upiId });
      },
      error: (e: AppError) => { this.buying.set(false); this.toast.error(e.message); },
    });
  }


  start(): void {
    if (this.starting() || !this.consent()) return;
    this.starting.set(true);
    this.api.start({ type: this.type(), difficulty: this.level(), driveId: this.driveId(), roleTitle: this.role().trim() || undefined, consent: true }).subscribe({
      next: s => this.router.navigate(['/student/interview-room', s.id]),
      error: (e: AppError) => { this.starting.set(false); this.toast.error(e.message); },
    });
  }
}
