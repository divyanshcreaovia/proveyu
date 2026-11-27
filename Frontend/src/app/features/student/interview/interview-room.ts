import { ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, OnInit, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AppError } from '../../../core/api';
import { AskedQuestion, InterviewService, SessionView, TYPE_LABELS, DIFFICULTY_LABELS } from '../services/interview.service';
import { ThemeService } from '../../../core/services/theme.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { WavRecorder } from './wav-recorder';

type Phase = 'loading' | 'check' | 'asking' | 'countdown' | 'ready' | 'recording' | 'processing' | 'error';

/** Thinking time after each question; recording then starts on its own. */
const PREP_SECONDS = 10;

/**
 * Full-screen AI interview: the interviewer speaks each question (Piper, or the browser voice as a fallback), then a 10-second
 * countdown runs and recording starts by itself; the student only presses Submit, or Skip (a skipped question scores 0). Answers are recorded as WAV and transcribed on the
 * server, or typed. The camera must stay on for the whole interview ("Monitoring on"); video is shown only to the student and is
 * never recorded or uploaded.
 */
@Component({
  selector: 'pm-interview-room',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../college/layout/college-theme.scss', '../../college/layout/portal-exam.scss', '../layout/student-theme.scss', '../layout/student-dark.scss'],
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
            <div class="ir-sub">Question {{ Math.min(v.answered + 1, v.plannedQuestions) }} of {{ v.plannedQuestions }}@if (v.current?.followUp) { · follow-up }@if (v.focus.length) { <span class="d-none d-md-inline"> · Focus: {{ v.focus.join(', ') }}</span> }</div>
          </div>
          <div class="ir-progress d-none d-sm-flex" aria-hidden="true">
            @for (i of steps(); track i) { <span [class.done]="i < v.answered" [class.now]="i === v.answered"></span> }
          </div>
          @if (camOn()) { <span class="ir-mon" role="status"><span class="ir-mon-dot" aria-hidden="true"></span>Monitoring on</span> }
          <button class="ir-icon" (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Light mode' : 'Dark mode'">
            <i class="bi" [class.bi-moon-stars]="!theme.dark()" [class.bi-sun]="theme.dark()"></i></button>
          <button class="ir-end" (click)="endInterview()" [disabled]="phase() === 'processing'">End interview</button>
        </header>

        <div class="ir-body pm-shell-content">
          <main class="ir-stage">
            @if (camLost() && phase() !== 'check') {
              <div class="alert alert-warning d-flex align-items-center gap-2 flex-wrap mb-0" role="alert">
                <i class="bi bi-camera-video-off-fill"></i><span class="flex-grow-1">Your camera turned off. Keep it on during the interview.</span>
                <button class="btn btn-sm btn-warning" (click)="enableCamera()">Turn camera on</button>
              </div>
            }
            @if (phase() === 'check') {
              <section class="ir-card ir-check">
                <h1 class="h4 st-h mb-1">Before you start</h1>
                <p class="st-muted mb-3">Sit somewhere quiet with your face clearly visible. Allow the <strong>camera and microphone</strong>, then say something to
                  check that the bar moves. The camera stays on during the whole interview for monitoring; it is not recorded or saved.</p>
                <p class="small mb-2" [class.text-success]="camOn()" [class.st-muted]="!camOn()">
                  @if (camError()) { <span class="text-danger">{{ camError() }}</span> }
                  @else if (camOn()) { <i class="bi bi-camera-video-fill"></i> Camera is on }
                  @else { <i class="bi bi-camera-video-off"></i> Camera not on yet }</p>
                <div class="ir-meter mb-2" role="meter" aria-label="Microphone level" [attr.aria-valuenow]="Math.round(level() * 100)" aria-valuemin="0" aria-valuemax="100">
                  <div [style.width.%]="level() * 100"></div></div>
                <p class="small mb-3" [class.text-success]="micOk()" [class.st-muted]="!micOk()">
                  @if (micError()) { <span class="text-danger">{{ micError() }}</span> }
                  @else if (micOk()) { <i class="bi bi-check-circle-fill"></i> Microphone is working }
                  @else if (micReady()) { Say "hello" to test your microphone }
                  @else { Microphone not connected yet }</p>
                <div class="d-flex gap-2 flex-wrap">
                  @if (!micReady() || !camOn()) { <button class="btn btn-outline-primary" (click)="enableDevices()"><i class="bi bi-camera-video"></i> Allow camera and microphone</button> }
                  <button class="btn btn-primary" (click)="begin(false)" [disabled]="!micReady() || !camOn()"><i class="bi bi-play-fill"></i> {{ v.done.length ? 'Continue interview' : 'Start interview' }}</button>
                  <button class="btn btn-link" (click)="begin(true)" [disabled]="!camOn()">I will type my answers</button>
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
                    <div class="text-center"><button class="btn btn-sm btn-link" (click)="skipAudio()">Skip to answer</button>
                      <button class="btn btn-sm btn-link text-body-secondary" (click)="skip()"><i class="bi bi-skip-forward"></i> Skip question</button></div> }
                  @case ('processing') {
                    <div class="text-center py-2" aria-live="polite"><span class="spinner-border spinner-border-sm text-primary me-2"></span><span class="st-h">{{ processingLabel() }}</span></div>
                  }
                  @case ('countdown') {
                    <div class="text-center ir-count" aria-live="polite">
                      @if (hint()) { <p class="small text-danger mb-2" role="alert">{{ hint() }}</p> }
                      <div class="ir-count-num" role="timer" [attr.aria-label]="'Recording starts in ' + countdown() + ' seconds'">{{ countdown() }}</div>
                      <p class="st-h fw-semibold mb-0">Think about your answer. Recording starts in {{ countdown() }} second{{ countdown() === 1 ? '' : 's' }}.</p>
                      <p class="small st-muted mb-2">Then just speak, and press Submit when you finish.</p>
                      <button class="btn btn-sm btn-link" (click)="startNow()">I'm ready, start now</button>
                      <button class="btn btn-sm btn-link" (click)="useTyping()"><i class="bi bi-keyboard"></i> Type instead</button>
                      <button class="btn btn-sm btn-link text-body-secondary" (click)="skip()"><i class="bi bi-skip-forward"></i> Skip question</button>
                    </div>
                  }
                  @case ('recording') {
                    <p class="text-center st-h fw-semibold mb-2"><i class="bi bi-record-circle text-danger"></i> Recording, speak your answer</p>
                    <div class="ir-rec">
                      <span class="ir-dot" aria-hidden="true"></span>
                      <span class="ir-clock" role="timer">{{ clock(recSeconds()) }} <span class="st-muted">/ {{ clock(v.maxAnswerSeconds) }}</span></span>
                      <div class="ir-meter flex-grow-1" aria-hidden="true"><div [style.width.%]="level() * 100"></div></div>
                    </div>
                    <div class="d-flex justify-content-center align-items-center gap-2 flex-wrap mt-3">
                      <button class="btn btn-primary px-4" (click)="finishRecording()"><i class="bi bi-send-fill"></i> Submit answer</button>
                      <button class="btn btn-link text-body-secondary" (click)="skip()"><i class="bi bi-skip-forward"></i> Skip question</button>
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
                        <p class="st-h fw-semibold mt-2 mb-0">Tap to answer</p>
                        <p class="small st-muted mb-2">Speak clearly and press Submit when you finish.</p>
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

          @if (camOn()) {
            <div class="ir-cam" aria-label="Your camera (monitoring on, not recorded)">
              <video #selfView autoplay muted playsinline></video>
              <span class="ir-cam-tag"><span class="ir-mon-dot" aria-hidden="true"></span>Live</span>
            </div>
          }
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
    .ir-mon { display: inline-flex; align-items: center; gap: .35rem; padding: .3rem .7rem; border-radius: 999px; font-size: .75rem; font-weight: 700;
      color: #b91c1c; background: rgba(225,29,72,.1); white-space: nowrap; flex-shrink: 0; }
    .ir-mon-dot { width: .5rem; height: .5rem; border-radius: 50%; background: #e11d48; animation: ir-breathe 1.2s infinite; }
    .ir-cam { position: fixed; right: 16px; bottom: 16px; z-index: 30; width: 168px; aspect-ratio: 4 / 3; border-radius: 12px; overflow: hidden;
      background: #000; box-shadow: 0 8px 24px rgba(0,0,0,.35); border: 2px solid rgba(225,29,72,.6); }
    .ir-cam video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); display: block; }
    .ir-cam-tag { position: absolute; left: 6px; top: 6px; display: inline-flex; align-items: center; gap: .3rem; padding: .1rem .45rem; border-radius: 999px;
      font-size: .65rem; font-weight: 700; color: #fff; background: rgba(0,0,0,.55); }
    @media (max-width: 575.98px) { .ir-cam { width: 96px; right: 10px; bottom: 10px; } .ir-mon { padding: .25rem .5rem; font-size: .68rem; } }
    .ir-count-num { font-size: 3rem; font-weight: 800; line-height: 1; color: var(--pm-link); font-variant-numeric: tabular-nums; margin-bottom: .5rem; }
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
  private selfView = viewChild<ElementRef<HTMLVideoElement>>('selfView');

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
  countdown = signal(PREP_SECONDS);
  /** Camera for monitoring: kept on and shown to the student only; never recorded or uploaded. */
  private camStream = signal<MediaStream | null>(null);
  camOn = computed(() => !!this.camStream());
  camError = signal('');
  camLost = signal(false);
  processingLabel = signal('');
  steps = computed(() => Array.from({ length: this.view()?.plannedQuestions ?? 0 }, (_, i) => i));
  draft = '';
  private skipping = false;
  recorder = new WavRecorder(l => {
    this.level.set(l);
    if (l > 0.12 && !this.micOk()) this.micOk.set(true);
  });
  private audioEl?: HTMLAudioElement;
  private audioUrl?: string;
  private timer?: ReturnType<typeof setInterval>;
  private prepTimer?: ReturnType<typeof setInterval>;
  private labelTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // The <video> appears once the camera is on; attach the stream whenever either changes.
    effect(() => {
      const el = this.selfView()?.nativeElement;
      const s = this.camStream();
      if (el && s && el.srcObject !== s) { el.srcObject = s; el.play().catch(() => undefined); }
    });
  }

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
    this.stopCountdown();
    this.stopCamera();
    clearTimeout(this.labelTimer);
    this.recorder.close();
  }

  @HostListener('window:beforeunload', ['$event'])
  warn(e: BeforeUnloadEvent): void {
    if (this.phase() === 'recording' || this.phase() === 'processing') e.preventDefault();
  }

  // ------------------------------------------------------------------ set-up

  /** Camera and microphone together, from the "Before you start" step. */
  async enableDevices(): Promise<void> {
    await this.enableCamera();
    if (!this.micReady()) await this.enableMic();
  }

  async enableMic(): Promise<void> {
    this.micError.set('');
    try {
      await this.recorder.open();
      this.micReady.set(true);
    } catch (e) {
      this.micError.set(micProblem(e));
    }
  }

  async enableCamera(): Promise<void> {
    this.camError.set('');
    if (this.camStream()) return;
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }, audio: false });
      // If the camera is unplugged or blocked mid-interview, ask the student to turn it back on.
      s.getVideoTracks().forEach(t => t.addEventListener('ended', () => { this.camStream.set(null); this.camLost.set(true); }));
      this.camStream.set(s);
      this.camLost.set(false);
    } catch (e) {
      this.camError.set(cameraProblem(e));
      if (this.phase() !== 'check') this.toast.error(cameraProblem(e));
    }
  }

  private stopCamera(): void {
    this.camStream()?.getTracks().forEach(t => t.stop());
    this.camStream.set(null);
  }

  begin(typeOnly: boolean): void {
    if (!this.camOn()) { this.camError.set('Turn on your camera to start the interview.'); return; }
    this.typing.set(typeOnly || !this.micReady());
    const q = this.view()?.current;
    if (q) this.playQuestion(q);
  }

  // ------------------------------------------------------------------ question audio

  playQuestion(q: AskedQuestion): void {
    this.stopAudio();
    this.stopCountdown();
    this.hint.set('');
    this.phase.set('asking');
    const done = () => { if (this.phase() === 'asking') this.afterQuestion(); };
    // local voice switched off on the server (AI_LOCAL_MODELS_ENABLED=false): the browser reads the question itself
    if (this.view()?.speechAvailable === false) { this.browserVoice(q.text, done); return; }
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

  skipAudio(): void { this.stopAudio(); this.afterQuestion(); }

  /** After the question: typed answers wait for the text box; voice answers get the countdown, then recording starts by itself. */
  private afterQuestion(): void {
    if (this.typing()) { this.phase.set('ready'); this.focusText(); return; }
    this.startCountdown();
  }

  private startCountdown(): void {
    this.stopCountdown();
    this.countdown.set(PREP_SECONDS);
    this.phase.set('countdown');
    this.prepTimer = setInterval(() => {
      this.countdown.update(n => n - 1);
      if (this.countdown() <= 0) { this.stopCountdown(); void this.startRecording(); }
    }, 1000);
  }

  startNow(): void { this.stopCountdown(); void this.startRecording(); }

  private stopCountdown(): void { if (this.prepTimer) { clearInterval(this.prepTimer); this.prepTimer = undefined; } }

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
    if (this.phase() === 'recording' || this.phase() === 'processing') return;
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

  /** Skip: scores 0. Recording (if any) keeps going while the student decides, and is thrown away on Skip. */
  async skip(): Promise<void> {
    if (this.phase() === 'processing' || this.skipping) return;
    this.skipping = true;
    const r = await this.confirm.ask({ title: 'Skip this question?', message: 'A skipped question scores 0. Even a short attempt earns marks.', confirmText: 'Skip question' });
    this.skipping = false;
    const v = this.view();
    if (!r.ok || !v?.current || this.phase() === 'processing') return;
    this.stopAudio();
    this.stopCountdown();
    if (this.phase() === 'recording') { this.stopTimer(); this.recorder.cancel(); }
    this.submit(this.api.answerText(v.id, v.current.id, ''), false);
  }

  useTyping(): void { this.stopCountdown(); this.typing.set(true); this.phase.set('ready'); this.focusText(); }

  private submit(call: ReturnType<InterviewService['answerText']>, voice: boolean): void {
    this.phase.set('processing');
    this.processingLabel.set(voice ? 'Listening to your answer…' : 'Next question…');
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
        if (voice && e.code === 'NO_SPEECH' && !this.typing()) {
          // Nothing was heard: say so and give a fresh countdown, no click needed.
          this.startCountdown();
          this.hint.set(e.message);
          return;
        }
        this.hint.set(e.message);
        this.toast.error(e.message);
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
    this.stopCountdown();
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

/** Same idea for the camera. */
function cameraProblem(e: unknown): string {
  const name = (e as { name?: string })?.name ?? '';
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'Camera permission is blocked. Click the lock or camera icon in the address bar, set Camera to Allow, then reload the page.';
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'No camera was found. Use a laptop or phone with a working camera for the interview.';
    case 'NotReadableError':
    case 'AbortError':
      return 'Your camera is being used by another app (Zoom, Teams, another tab). Close it and try again.';
    default:
      return 'The camera could not be started. Check your browser and system camera settings, then try again.';
  }
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
