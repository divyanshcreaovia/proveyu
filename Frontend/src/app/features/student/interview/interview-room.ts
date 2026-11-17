import { ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AppError } from '../../../core/api';
import { AskedQuestion, InterviewService, SessionView, TYPE_LABELS, DIFFICULTY_LABELS } from '../../../services/interview.service';
import { ThemeService } from '../../../services/theme.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { WavRecorder } from './wav-recorder';

type Phase = 'loading' | 'check' | 'asking' | 'ready' | 'recording' | 'processing' | 'error';

/**
 * Full-screen AI interview: the interviewer speaks each question (Piper, or the browser voice as a fallback), the student
 * answers by voice (recorded as WAV and transcribed on the server) or by typing, and follow-ups arrive as the AI decides.
 */
@Component({
  selector: 'pm-interview-room',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../../college/layout/college-theme.scss',
  template: `
    <div class="pm-shell ir" [attr.data-bs-theme]="theme.dark() ? 'dark' : 'light'">
      @if (!view()) {
        <div class="ir-center">@if (phase() === 'error') { <p>{{ error() }}</p><button class="btn btn-primary" (click)="exit()">Back to AI interview</button> }
          @else { <span class="spinner-border" role="status" aria-label="Loading"></span> }</div>
      } @else {
        @let v = view()!;
        <header class="ir-top">
          <img src="images/proveyu-mark.png" alt="" class="ir-mark" />
          <div class="ir-head">
            <div class="ir-title text-truncate">{{ typeLabels[v.type] }} interview · {{ levelLabels[v.difficulty] }} · {{ v.companyName ? v.companyName + ' · ' : '' }}{{ v.roleTitle }}</div>
            <div class="ir-sub">Question {{ Math.min(v.mainAnswered + 1, v.plannedQuestions) }} of {{ v.plannedQuestions }}@if (v.current?.followUp) { · follow-up }</div>
          </div>
          <div class="ir-progress d-none d-sm-flex" aria-hidden="true">
            @for (i of steps(); track i) { <span [class.done]="i < v.mainAnswered" [class.now]="i === v.mainAnswered"></span> }
          </div>
          <button class="ir-icon" (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Light mode' : 'Dark mode'">
            <i class="bi" [class.bi-moon-stars]="!theme.dark()" [class.bi-sun]="theme.dark()"></i></button>
          <button class="ir-end" (click)="endInterview()" [disabled]="phase() === 'processing'">End interview</button>
        </header>

        <div class="ir-body pm-shell-content">
          <main class="ir-stage">
            @if (phase() === 'check') {
              <section class="ir-card ir-check">
                <h1 class="h4 st-h mb-1">Before you start</h1>
                <p class="st-muted mb-3">Sit somewhere quiet, allow the microphone, and say something to check that the bar moves.</p>
                <div class="ir-meter mb-2" role="meter" aria-label="Microphone level" [attr.aria-valuenow]="Math.round(level() * 100)" aria-valuemin="0" aria-valuemax="100">
                  <div [style.width.%]="level() * 100"></div></div>
                <p class="small mb-3" [class.text-success]="micOk()" [class.st-muted]="!micOk()">
                  @if (micError()) { <span class="text-danger">{{ micError() }}</span> }
                  @else if (micOk()) { <i class="bi bi-check-circle-fill"></i> Microphone is working }
                  @else if (micReady()) { Say "hello" to test your microphone }
                  @else { Microphone not connected yet }</p>
                <div class="d-flex gap-2 flex-wrap">
                  @if (!micReady()) { <button class="btn btn-outline-primary" (click)="enableMic()"><i class="bi bi-mic"></i> Allow microphone</button> }
                  <button class="btn btn-primary" (click)="begin(false)" [disabled]="!micReady()"><i class="bi bi-play-fill"></i> {{ v.done.length ? 'Continue interview' : 'Start interview' }}</button>
                  <button class="btn btn-link" (click)="begin(true)">I will type my answers</button>
                </div>
              </section>
            } @else if (v.current; as q) {
              <section class="ir-card ir-question">
                <div class="ir-avatar" [class.speaking]="phase() === 'asking'" [class.thinking]="phase() === 'processing'" aria-hidden="true">
                  <i class="bi bi-person-fill"></i><span class="ir-ring"></span></div>
                <div class="ir-who">AI Interviewer @if (q.topic) { <span class="ir-tag">{{ q.topic }}</span> } @if (q.followUp) { <span class="ir-tag ir-tag-fu">Follow-up</span> }</div>
                <p class="ir-qtext" aria-live="polite">{{ q.text }}</p>
                <button class="btn btn-sm btn-link p-0" (click)="playQuestion(q)" [disabled]="phase() === 'recording' || phase() === 'processing'"><i class="bi bi-volume-up"></i> Hear again</button>
              </section>

              <section class="ir-card ir-answer">
                @switch (phase()) {
                  @case ('asking') { <p class="st-muted mb-0 text-center"><i class="bi bi-soundwave"></i> The interviewer is asking the question…</p>
                    <button class="btn btn-sm btn-link d-block mx-auto" (click)="skipAudio()">Skip to answer</button> }
                  @case ('processing') {
                    <div class="text-center py-2" aria-live="polite"><span class="spinner-border spinner-border-sm text-primary me-2"></span><span class="st-h">{{ processingLabel() }}</span></div>
                  }
                  @case ('recording') {
                    <div class="ir-rec">
                      <span class="ir-dot" aria-hidden="true"></span>
                      <span class="ir-clock" role="timer">{{ clock(recSeconds()) }} <span class="st-muted">/ {{ clock(v.maxAnswerSeconds) }}</span></span>
                      <div class="ir-meter flex-grow-1" aria-hidden="true"><div [style.width.%]="level() * 100"></div></div>
                    </div>
                    <div class="d-flex gap-2 justify-content-center mt-3">
                      <button class="btn btn-outline-secondary" (click)="cancelRecording()">Re-record</button>
                      <button class="btn btn-primary px-4" (click)="finishRecording()"><i class="bi bi-stop-fill"></i> Done answering</button>
                    </div>
                  }
                  @default {
                    @if (typing()) {
                      <label class="visually-hidden" for="ir-text">Your answer</label>
                      <textarea id="ir-text" #textBox class="form-control mb-2" rows="5" maxlength="4000" [(ngModel)]="draft" placeholder="Type your answer as you would say it…"></textarea>
                      <div class="d-flex gap-2 flex-wrap align-items-center">
                        <button class="btn btn-primary" (click)="sendText()" [disabled]="!draft.trim()">Submit answer</button>
                        @if (micReady()) { <button class="btn btn-link" (click)="typing.set(false)"><i class="bi bi-mic"></i> Answer by voice</button> }
                        <button class="btn btn-link text-body-secondary ms-auto" (click)="skip()">Skip question</button>
                      </div>
                    } @else {
                      <div class="text-center">
                        <button class="ir-mic" (click)="startRecording()" aria-label="Start answering"><i class="bi bi-mic-fill"></i></button>
                        <p class="st-h fw-semibold mt-2 mb-0">Tap to start answering</p>
                        <p class="small st-muted mb-2">Speak clearly. Tap "Done answering" when you finish.</p>
                        @if (hint()) { <p class="small text-danger mb-2" role="alert">{{ hint() }}</p> }
                        <button class="btn btn-sm btn-link" (click)="useTyping()"><i class="bi bi-keyboard"></i> Type instead</button>
                        <button class="btn btn-sm btn-link text-body-secondary" (click)="skip()">Skip question</button>
                      </div>
                    }
                  }
                }
              </section>
            }
          </main>

          <aside class="ir-card ir-log" aria-label="Interview so far">
            <div class="ir-label">Interview so far</div>
            @for (d of v.done; track d.id) {
              <div class="ir-turn">
                <div class="ir-q"><span>Q{{ d.number }}</span> {{ d.text }}</div>
                <div class="ir-a">@if (d.transcript) { {{ d.transcript }} } @else { <em class="st-muted">Skipped</em> }</div>
              </div>
            } @empty { <p class="small st-muted mb-0">Your answers will appear here as text, so you can see what the AI heard.</p> }
          </aside>
        </div>
      }
    </div>`,
  styles: [`
    :host { display: block; }
    .ir { min-height: 100vh; background: var(--pm-page-bg); color: var(--pm-text); font-family: 'Inter', sans-serif; display: flex; flex-direction: column; }
    .ir-center { flex: 1; display: grid; place-items: center; text-align: center; gap: 1rem; padding: 2rem; }
    .ir-top { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: .75rem; padding: .7rem 16px;
      background: var(--pm-card-bg); border-bottom: 1px solid var(--pm-card-border); }
    .ir-mark { width: 2rem; height: 2rem; object-fit: contain; }
    .ir-head { flex: 1 1 auto; min-width: 0; }
    .ir-title { font-weight: 800; color: var(--pm-heading); }
    .ir-top > button, .ir-mark { flex-shrink: 0; }
    @media (max-width: 575.98px) { .ir-end { padding: .4rem .75rem; font-size: .8rem; } .ir-qtext { font-size: 1.1rem; } }
    .ir-sub { font-size: .75rem; color: var(--pm-muted); }
    .ir-progress { gap: .3rem; }
    .ir-progress span { width: 1.6rem; height: .35rem; border-radius: 999px; background: var(--pm-soft); }
    .ir-progress span.done { background: #16a34a; } .ir-progress span.now { background: var(--pm-link); }
    .ir-icon { all: unset; box-sizing: border-box; width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; cursor: pointer;
      background: var(--pm-soft); color: var(--pm-heading); }
    .ir-end { all: unset; box-sizing: border-box; cursor: pointer; padding: .5rem 1rem; border-radius: 999px; font-weight: 700; font-size: .85rem;
      border: 1.5px solid rgba(225,29,72,.5); color: #e11d48; white-space: nowrap; }
    .ir-end:disabled { opacity: .5; cursor: default; }
    .ir-icon:focus-visible, .ir-end:focus-visible, .ir-mic:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .ir-body { flex: 1; display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 1rem; padding: 1rem 16px 1.5rem; align-items: start;
      max-width: 1200px; width: 100%; margin: 0 auto; }
    @media (max-width: 991.98px) { .ir-body { grid-template-columns: minmax(0, 1fr); } }
    .ir-stage { display: grid; gap: 1rem; min-width: 0; }
    .ir-body > * { min-width: 0; }
    .ir-card { background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); border-radius: 16px; padding: 1.25rem; box-shadow: var(--pm-card-shadow); min-width: 0; }
    .ir-check { padding: 1.75rem; }
    .ir-question { text-align: center; padding: 1.75rem 1.25rem; }
    .ir-avatar { position: relative; width: 84px; height: 84px; margin: 0 auto .75rem; border-radius: 50%; display: grid; place-items: center;
      font-size: 2.4rem; color: #fff; background: linear-gradient(135deg, #7c3aed, #0b5cff); }
    .ir-ring { position: absolute; inset: -6px; border-radius: 50%; border: 3px solid rgba(124,58,237,.45); opacity: 0; }
    .ir-avatar.speaking .ir-ring { animation: ir-pulse 1.2s ease-out infinite; }
    .ir-avatar.thinking { animation: ir-breathe 1.6s ease-in-out infinite; }
    @keyframes ir-pulse { 0% { transform: scale(.95); opacity: .9; } 100% { transform: scale(1.25); opacity: 0; } }
    @keyframes ir-breathe { 50% { opacity: .6; } }
    @media (prefers-reduced-motion: reduce) { .ir-avatar.speaking .ir-ring, .ir-avatar.thinking, .ir-dot { animation: none; } .ir-avatar.speaking .ir-ring { opacity: .8; } }
    .ir-who { font-size: .8rem; font-weight: 700; color: var(--pm-label); display: flex; gap: .4rem; justify-content: center; align-items: center; flex-wrap: wrap; }
    .ir-tag { font-size: .68rem; font-weight: 600; padding: .12rem .5rem; border-radius: 999px; background: var(--pm-soft); color: var(--pm-label); }
    .ir-tag-fu { background: rgba(124,58,237,.14); color: #7c3aed; }
    .ir-qtext { font-size: 1.3rem; line-height: 1.5; font-weight: 600; color: var(--pm-heading); margin: .75rem auto .5rem; max-width: 40rem; }
    .ir-mic { all: unset; box-sizing: border-box; width: 76px; height: 76px; border-radius: 50%; display: inline-grid; place-items: center; cursor: pointer;
      font-size: 1.9rem; color: #fff; background: #e11d48; box-shadow: 0 8px 24px rgba(225,29,72,.35); transition: transform .12s; }
    .ir-mic:hover { transform: scale(1.05); }
    .ir-rec { display: flex; align-items: center; gap: .75rem; }
    .ir-dot { width: .75rem; height: .75rem; border-radius: 50%; background: #e11d48; animation: ir-breathe 1s infinite; flex-shrink: 0; }
    .ir-clock { font-weight: 800; font-variant-numeric: tabular-nums; color: var(--pm-heading); white-space: nowrap; }
    .ir-meter { height: .5rem; border-radius: 999px; background: var(--pm-soft); overflow: hidden; }
    .ir-meter > div { height: 100%; background: linear-gradient(90deg, #16a34a, #84cc16); transition: width .08s linear; }
    .ir-log { position: sticky; top: 76px; max-height: calc(100vh - 100px); overflow-y: auto; display: grid; gap: .75rem; align-content: start; }
    .ir-label { font-size: .68rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--pm-muted); }
    .ir-turn { display: grid; gap: .35rem; }
    .ir-q { font-size: .8rem; font-weight: 600; color: var(--pm-heading); }
    .ir-q span { color: var(--pm-link); margin-right: .2rem; }
    .ir-a { font-size: .8rem; padding: .5rem .7rem; border-radius: 10px 10px 2px 10px; background: rgba(11,92,255,.08); color: var(--pm-text); }
  `],
})
export class InterviewRoom implements OnInit, OnDestroy {
  readonly id = input.required<string>();
  readonly Math = Math;
  readonly typeLabels = TYPE_LABELS;
  readonly levelLabels = DIFFICULTY_LABELS;
  theme = inject(ThemeService);
  private api = inject(InterviewService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private textBox = viewChild<ElementRef<HTMLTextAreaElement>>('textBox');

  view = signal<SessionView | null>(null);
  phase = signal<Phase>('loading');
  error = signal('');
  level = signal(0);
  micOk = signal(false);
  micError = signal('');
  micReady = signal(false);
  typing = signal(false);
  hint = signal('');
  recSeconds = signal(0);
  processingLabel = signal('');
  steps = computed(() => Array.from({ length: this.view()?.plannedQuestions ?? 0 }, (_, i) => i));
  draft = '';
  recorder = new WavRecorder(l => {
    this.level.set(l);
    if (l > 0.12 && !this.micOk()) this.micOk.set(true);
  });
  private audioEl?: HTMLAudioElement;
  private audioUrl?: string;
  private timer?: ReturnType<typeof setInterval>;
  private labelTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.api.session(this.id()).subscribe({
      next: v => {
        if (v.status !== 'IN_PROGRESS') { this.toReport(); return; }
        this.view.set(v);
        this.phase.set('check');
      },
      error: (e: AppError) => { this.error.set(e.message); this.phase.set('error'); },
    });
  }

  ngOnDestroy(): void {
    this.stopAudio();
    this.stopTimer();
    clearTimeout(this.labelTimer);
    this.recorder.close();
  }

  @HostListener('window:beforeunload', ['$event'])
  warn(e: BeforeUnloadEvent): void {
    if (this.phase() === 'recording' || this.phase() === 'processing') e.preventDefault();
  }

  // ------------------------------------------------------------------ set-up

  async enableMic(): Promise<void> {
    this.micError.set('');
    try {
      await this.recorder.open();
      this.micReady.set(true);
    } catch (e) {
      this.micError.set(micProblem(e));
    }
  }

  begin(typeOnly: boolean): void {
    this.typing.set(typeOnly || !this.micReady());
    const q = this.view()?.current;
    if (q) this.playQuestion(q);
  }

  // ------------------------------------------------------------------ question audio

  playQuestion(q: AskedQuestion): void {
    this.stopAudio();
    this.hint.set('');
    this.phase.set('asking');
    const done = () => { if (this.phase() === 'asking') { this.phase.set('ready'); this.focusText(); } };
    this.api.questionSpeech(q.id).subscribe({
      next: url => {
        this.audioUrl = url;
        this.audioEl = new Audio(url);
        this.audioEl.onended = done;
        this.audioEl.onerror = () => this.browserVoice(q.text, done);
        this.audioEl.play().catch(() => this.browserVoice(q.text, done));
      },
      error: () => this.browserVoice(q.text, done),
    });
  }

  skipAudio(): void { this.stopAudio(); this.phase.set('ready'); this.focusText(); }

  /** Piper is offline: the browser's own voice reads the question. */
  private browserVoice(text: string, done: () => void): void {
    if (!('speechSynthesis' in window)) { done(); return; }
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'en-IN';
    u.rate = 0.98;
    u.onend = done;
    u.onerror = done;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  }

  private stopAudio(): void {
    if (this.audioEl) { this.audioEl.onended = null; this.audioEl.onerror = null; this.audioEl.pause(); this.audioEl = undefined; }
    if (this.audioUrl) { URL.revokeObjectURL(this.audioUrl); this.audioUrl = undefined; }
    if ('speechSynthesis' in window) speechSynthesis.cancel();
  }

  // ------------------------------------------------------------------ answering

  async startRecording(): Promise<void> {
    this.hint.set('');
    try {
      await this.recorder.start();
    } catch (e) {
      this.hint.set(micProblem(e) + ' You can type your answer instead.');
      this.typing.set(true);
      return;
    }
    this.phase.set('recording');
    this.recSeconds.set(0);
    const max = this.view()!.maxAnswerSeconds;
    this.timer = setInterval(() => {
      this.recSeconds.update(s => s + 1);
      if (this.recSeconds() >= max) this.finishRecording();
    }, 1000);
  }

  cancelRecording(): void {
    this.stopTimer();
    this.recorder.cancel();
    this.phase.set('ready');
  }

  finishRecording(): void {
    if (this.phase() !== 'recording') return;
    this.stopTimer();
    const { wav, seconds } = this.recorder.stop();
    const v = this.view()!;
    this.submit(this.api.answerVoice(v.id, v.current!.id, wav, seconds), true);
  }

  sendText(): void {
    const v = this.view()!;
    this.submit(this.api.answerText(v.id, v.current!.id, this.draft.trim()), false);
  }

  async skip(): Promise<void> {
    const r = await this.confirm.ask({ title: 'Skip this question?', message: 'A skipped question scores zero. Even a short attempt is better.', confirmText: 'Skip' });
    if (!r.ok) return;
    const v = this.view()!;
    this.submit(this.api.answerText(v.id, v.current!.id, ''), false);
  }

  useTyping(): void { this.typing.set(true); this.focusText(); }

  private submit(call: ReturnType<InterviewService['answerText']>, voice: boolean): void {
    this.phase.set('processing');
    this.processingLabel.set(voice ? 'Listening to your answer…' : 'Reading your answer…');
    clearTimeout(this.labelTimer);
    this.labelTimer = setTimeout(() => this.processingLabel.set('The interviewer is thinking…'), voice ? 2500 : 800);
    call.subscribe({
      next: v => {
        clearTimeout(this.labelTimer);
        this.draft = '';
        this.view.set(v);
        if (v.status !== 'IN_PROGRESS' || !v.current) { this.toReport(); return; }
        this.playQuestion(v.current);
      },
      error: (e: AppError) => {
        clearTimeout(this.labelTimer);
        this.phase.set('ready');
        if (e.code === 'ALREADY_ANSWERED' || e.code === 'INTERVIEW_ENDED') { this.reload(); return; }
        this.hint.set(e.message);
        if (!voice || e.code !== 'NO_SPEECH') this.toast.error(e.message);
      },
    });
  }

  async endInterview(): Promise<void> {
    const v = this.view()!;
    const none = v.done.length === 0;
    const r = await this.confirm.ask({
      title: 'End the interview now?',
      message: none ? 'You have not answered anything yet, so the interview is cancelled and your credit is returned.'
        : 'Questions you have not answered count as zero in your score. You will see the report for what you answered.',
      confirmText: 'End interview', tone: 'danger',
    });
    if (!r.ok) return;
    this.stopAudio();
    this.recorder.cancel();
    this.api.end(v.id).subscribe({
      next: res => { if (res) this.toReport(); else { this.toast.success('Interview cancelled. Your credit was returned.'); this.exit(); } },
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  // ------------------------------------------------------------------ helpers

  clock(s: number): string { return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; }
  exit(): void { this.router.navigateByUrl('/student/ai-interview'); }
  private toReport(): void { this.router.navigate(['/student/ai-interview/report', this.id()]); }
  private reload(): void { this.api.session(this.id()).subscribe(v => { this.view.set(v); if (v.status !== 'IN_PROGRESS' || !v.current) this.toReport(); else this.playQuestion(v.current); }); }
  private stopTimer(): void { if (this.timer) { clearInterval(this.timer); this.timer = undefined; } }
  private focusText(): void { if (this.typing()) setTimeout(() => this.textBox()?.nativeElement.focus(), 0); }
}

/** Tells the student what is actually wrong with the microphone, since each cause has a different fix. */
function micProblem(e: unknown): string {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    return 'The microphone only works on a secure address. Open the portal with https:// (or on this computer via localhost).';
  }
  const name = (e as { name?: string })?.name ?? '';
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'Microphone permission is blocked. Click the lock or mic icon in the address bar, set Microphone to Allow, then reload the page.';
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'No microphone was found. Plug in a headset or check that your microphone is turned on in system settings.';
    case 'NotReadableError':
    case 'AbortError':
      return 'Your microphone is being used by another app (Zoom, Teams, another tab). Close it and try again.';
    default:
      return 'The microphone could not be started. Check your browser and system microphone settings, then try again.';
  }
}
