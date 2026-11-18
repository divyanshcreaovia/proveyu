import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AppError } from '../../../core/api';
import { AttemptView, LANGUAGE_LABELS, PrepAnswer, PrepLanguage, PrepService, QuestionView, RunResponse } from '../services/prep.service';
import { ThemeService } from '../../../core/services/theme.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { StudentPortalService } from '../services/student-portal.service';
import { enterFullscreen, exitFullscreen, fullscreenSupported, isFullscreen } from '../../../shared/fullscreen';

/**
 * Full-screen, timed mock test: MCQ options, coding editor with sample runs, question palette, autosave and auto-submit.
 * The test stays in browser full screen; leaving it pauses the screen until the student returns or exits. Questions carry
 * a faint ProveYu watermark (and copied question text a ProveYu tag) so screenshots pasted into AI tools are traceable.
 */
@Component({
  selector: 'pm-test-player',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../../college/layout/college-theme.scss',
  template: `
    <div class="pm-shell tp" [attr.data-bs-theme]="theme.dark() ? 'dark' : 'light'">
      @if (!view()) {
        <div class="tp-loading">@if (error()) { <p>{{ error() }}</p><button class="btn btn-primary" (click)="exit()">Back to Preparation</button> }
          @else { <span class="spinner-border"></span> }</div>
      } @else {
        <header class="tp-top">
          <img src="images/proveyu-mark.png" alt="" class="tp-mark" />
          <div class="min-w-0 flex-grow-1"><div class="tp-title text-truncate">{{ view()!.title }}</div>
            <div class="tp-sub">{{ answeredCount() }} of {{ qs().length }} answered · <span [class.text-danger]="saveState() === 'error'">{{ saveLabel() }}</span></div></div>
          <div class="tp-timer" [class.warn]="remaining() <= 300" [class.crit]="remaining() <= 60" role="timer" [attr.aria-label]="'Time left ' + clock()">
            <i class="bi bi-stopwatch"></i> {{ clock() }}</div>
          <button class="tp-icon d-none d-sm-inline-grid" (click)="theme.toggle()" [attr.aria-label]="theme.dark() ? 'Light mode' : 'Dark mode'">
            <i class="bi" [class.bi-moon-stars]="!theme.dark()" [class.bi-sun]="theme.dark()"></i></button>
          <button class="tp-exit" (click)="confirmExit()" [disabled]="submitting()" title="Leave the test (timer keeps running)"><i class="bi bi-box-arrow-left"></i><span class="d-none d-md-inline"> Exit</span></button>
          <button class="tp-submit" (click)="confirmSubmit()" [disabled]="submitting()">
            @if (submitting()) { <span class="spinner-border spinner-border-sm me-1"></span> } Submit test</button>
        </header>

        <div class="tp-body pm-shell-content">
          <main class="tp-main" [class.tp-main-code]="cur().type === 'CODING'">
            @let q = cur();
            @if (q.type === 'MCQ') {
              <section class="tp-card tp-wmk" (copy)="onCopy($event)">
                <div class="tp-wm" aria-hidden="true" [style.background-image]="watermark()"></div>
                <div class="tp-qhead"><span class="tp-qno">Question {{ idx() + 1 }} of {{ qs().length }}</span>
                  <span class="tp-tag">{{ q.section }}</span><span class="tp-tag">{{ q.marks }} mark{{ q.marks > 1 ? 's' : '' }}</span></div>
                <p class="tp-qtext">{{ q.text }}</p>
                <div class="tp-options" role="radiogroup" [attr.aria-label]="'Options for question ' + (idx() + 1)">
                  @for (o of q.options; track $index) {
                    <button type="button" class="tp-option" role="radio" [attr.aria-checked]="answers()[q.id]?.option === $index"
                      [class.sel]="answers()[q.id]?.option === $index" (click)="choose(q, $index)">
                      <span class="tp-letter">{{ letters[$index] }}</span><span class="tp-otext">{{ o }}</span></button>
                  }
                </div>
                <div class="tp-brand">© ProveYu mock test{{ roll() ? ' · ' + roll() : '' }}</div>
              </section>
            } @else {
              <section class="tp-card tp-problem tp-wmk" (copy)="onCopy($event)">
                <div class="tp-wm" aria-hidden="true" [style.background-image]="watermark()"></div>
                <div class="tp-qhead"><span class="tp-qno">Question {{ idx() + 1 }} of {{ qs().length }}</span>
                  <span class="tp-tag">{{ q.section }}</span><span class="tp-tag">{{ q.marks }} marks</span></div>
                <h2 class="h5 st-h mb-2">{{ q.title }}</h2>
                <p class="tp-qtext">{{ q.text }}</p>
                @for (s of q.samples ?? []; track $index) {
                  <div class="tp-sample"><div><div class="tp-label">Sample input {{ $index + 1 }}</div><pre>{{ s.input }}</pre></div>
                    <div><div class="tp-label">Sample output {{ $index + 1 }}</div><pre>{{ s.output }}</pre></div></div>
                }
                <p class="small text-body-secondary mb-0">Read from standard input and print to standard output. Your code is also checked against hidden test cases when you submit.
                  @if (lang(q) === 'java') { In Java the class must be named <code>Main</code>. }</p>
                <div class="tp-brand">© ProveYu mock test{{ roll() ? ' · ' + roll() : '' }}</div>
              </section>
              <section class="tp-card tp-editor-card">
                <div class="tp-ed-bar">
                  <label class="visually-hidden" for="tp-lang">Language</label>
                  <select id="tp-lang" class="form-select form-select-sm w-auto" [ngModel]="lang(q)" (ngModelChange)="setLang(q, $event)">
                    @for (l of view()!.languages; track l) { <option [value]="l">{{ langLabels[l] }}</option> }</select>
                  <button class="btn btn-sm btn-outline-secondary" (click)="resetCode(q)" title="Replace your code with the starter code"><i class="bi bi-arrow-counterclockwise"></i> Reset</button>
                  <button class="btn btn-sm btn-primary ms-auto" (click)="runCode(q)" [disabled]="running()">
                    @if (running()) { <span class="spinner-border spinner-border-sm me-1"></span> Running… } @else { <i class="bi bi-play-fill"></i> Run samples }</button>
                </div>
                <div class="tp-editor">
                  <div class="tp-gutter" #gutter aria-hidden="true">@for (n of lineNumbers(); track n) { <div>{{ n }}</div> }</div>
                  <textarea #code class="tp-code" spellcheck="false" autocapitalize="off" autocomplete="off" aria-label="Code editor"
                    [value]="codeOf(q)" (input)="typeCode(q, $any($event.target).value)" (keydown)="editorKey($event, q)" (scroll)="syncGutter()"></textarea>
                </div>
                @if (runResult(); as rr) {
                  <div class="tp-run" aria-live="polite">
                    @if (rr.compileError) { <div class="tp-label text-danger">Compilation error</div><pre class="tp-err">{{ rr.compileError }}</pre> }
                    @for (c of rr.cases; track c.index) {
                      <div class="tp-case" [class.ok]="c.passed">
                        <div class="tp-case-head"><i class="bi" [class.bi-check-circle-fill]="c.passed" [class.bi-x-circle-fill]="!c.passed"></i>
                          Sample {{ c.index }}: {{ c.passed ? 'Passed' : (c.timedOut ? 'Time limit exceeded' : 'Wrong answer') }}</div>
                        @if (!c.passed) {
                          <div class="tp-sample"><div><div class="tp-label">Expected</div><pre>{{ c.expected }}</pre></div>
                            <div><div class="tp-label">Your output</div><pre>{{ c.actual || ' ' }}</pre></div></div>
                          @if (c.error && !c.timedOut) { <pre class="tp-err">{{ c.error }}</pre> }
                        }
                      </div>
                    }
                  </div>
                }
              </section>
            }
            <nav class="tp-nav" aria-label="Question navigation">
              <button class="btn btn-sm btn-outline-secondary" (click)="go(idx() - 1)" [disabled]="idx() === 0"><i class="bi bi-chevron-left"></i> Previous</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="toggleMark()" [class.active]="marked().has(cur().id)">
                <i class="bi" [class.bi-bookmark]="!marked().has(cur().id)" [class.bi-bookmark-fill]="marked().has(cur().id)"></i> Mark for review</button>
              @if (cur().type === 'MCQ' && answers()[cur().id]?.option !== undefined) {
                <button class="btn btn-sm btn-link" (click)="clear(cur())">Clear answer</button> }
              <button class="btn btn-sm btn-primary ms-auto" (click)="go(idx() + 1)" [disabled]="idx() === qs().length - 1">Next <i class="bi bi-chevron-right"></i></button>
            </nav>
          </main>

          <aside class="tp-side tp-card" aria-label="Question palette">
            @for (g of groups(); track g.name) {
              <div class="tp-label mt-1">{{ g.name }}</div>
              <div class="tp-palette">
                @for (i of g.items; track i) {
                  <button type="button" class="tp-pal" [class.cur]="i === idx()" [class.ans]="isAnswered(qs()[i])" [class.mark]="marked().has(qs()[i].id)"
                    (click)="go(i)" [attr.aria-label]="'Question ' + (i + 1) + (isAnswered(qs()[i]) ? ', answered' : ', not answered')">{{ i + 1 }}</button>
                }
              </div>
            }
            <div class="tp-legend">
              <span><i class="tp-dot ans"></i> Answered {{ answeredCount() }}</span>
              <span><i class="tp-dot mark"></i> For review {{ marked().size }}</span>
              <span><i class="tp-dot"></i> Not answered {{ qs().length - answeredCount() }}</span>
            </div>
          </aside>
        </div>
        @if (locked()) {
          <div class="tp-lock" role="dialog" aria-modal="true" aria-labelledby="tp-lock-title" aria-describedby="tp-lock-text">
            <div class="tp-lock-card">
              <div class="tp-lock-icon" aria-hidden="true"><i class="bi bi-arrows-fullscreen"></i></div>
              <h2 id="tp-lock-title" class="h5 st-h mb-2">Return to full screen to continue</h2>
              <p id="tp-lock-text" class="small mb-2">This test runs in full screen. The timer is still running ({{ clock() }} left) and your answers are saved.</p>
              @if (exits()) { <p class="small text-danger mb-3">You left full screen {{ exits() }} time{{ exits() > 1 ? 's' : '' }} during this test.</p> }
              <button class="btn btn-primary w-100 mb-2" (click)="returnToFullscreen()"><i class="bi bi-arrows-fullscreen me-1"></i> Return to full screen</button>
              <button class="btn btn-link btn-sm text-body-secondary" (click)="confirmExit()">Leave the test</button>
            </div>
          </div>
        }
      }
    </div>`,
  styles: [`
    :host { display: block; }
    .tp { min-height: 100vh; background: var(--pm-page-bg); color: var(--pm-text); font-family: 'Inter', sans-serif; display: flex; flex-direction: column; }
    .tp-loading { flex: 1; display: grid; place-items: center; text-align: center; gap: 1rem; padding: 2rem; }
    .tp-top { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: .75rem; padding: .7rem max(16px, env(safe-area-inset-right, 0px)) .7rem max(16px, env(safe-area-inset-left, 0px));
      background: var(--pm-card-bg); border-bottom: 1px solid var(--pm-card-border); }
    .tp-mark { width: 2rem; height: 2rem; object-fit: contain; }
    .tp-title { font-weight: 800; color: var(--pm-heading); }
    .tp-sub { font-size: .75rem; color: var(--pm-muted); }
    .tp-timer { font-weight: 800; font-variant-numeric: tabular-nums; font-size: 1.05rem; padding: .35rem .85rem; border-radius: 999px;
      background: var(--pm-soft); color: var(--pm-heading); white-space: nowrap; }
    .tp-timer.warn { background: rgba(217,119,6,.15); color: #b45309; } .tp-timer.crit { background: rgba(225,29,72,.15); color: #be123c; animation: tp-pulse 1s infinite; }
    @keyframes tp-pulse { 50% { opacity: .55; } }
    @media (prefers-reduced-motion: reduce) { .tp-timer.crit { animation: none; } }
    .tp-icon { all: unset; box-sizing: border-box; width: 38px; height: 38px; border-radius: 50%; place-items: center; cursor: pointer;
      background: var(--pm-soft); color: var(--pm-heading); }
    .tp-icon:focus-visible, .tp-submit:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .tp-submit { all: unset; box-sizing: border-box; cursor: pointer; padding: .5rem 1.15rem; border-radius: 999px; font-weight: 700; font-size: .85rem;
      background: var(--pm-primary); color: var(--pm-primary-text); white-space: nowrap; display: inline-flex; align-items: center; }
    .tp-submit:hover { background: var(--pm-primary-hover); }
    .tp-exit { all: unset; box-sizing: border-box; cursor: pointer; padding: .45rem .9rem; border-radius: 999px; font-weight: 600; font-size: .85rem;
      border: 1.5px solid var(--pm-card-border); color: var(--pm-label); white-space: nowrap; flex-shrink: 0; }
    .tp-exit:hover { border-color: var(--pm-link); color: var(--pm-heading); }
    .tp-exit:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .tp-exit:disabled { opacity: .5; cursor: default; }
    .tp-wmk { position: relative; overflow: hidden; }
    .tp-wm { position: absolute; inset: 0; pointer-events: none; z-index: 1; background-repeat: repeat; }
    .tp-brand { position: relative; z-index: 2; margin-top: .9rem; text-align: right; font-size: .68rem; font-weight: 600; letter-spacing: .03em; color: var(--pm-muted); }
    .tp-lock { position: fixed; inset: 0; z-index: 1500; display: grid; place-items: center; padding: 16px; background: var(--pm-page-bg); }
    .tp-lock-card { width: min(420px, 100%); text-align: center; background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); border-radius: 18px;
      padding: 1.75rem 1.5rem 1.25rem; box-shadow: var(--pm-card-shadow); color: var(--pm-text); }
    .tp-lock-icon { width: 3.25rem; height: 3.25rem; margin: 0 auto .9rem; border-radius: 14px; display: grid; place-items: center; font-size: 1.4rem;
      color: #fff; background: #0d6efd; }
    .tp-submit:disabled { opacity: .5; cursor: default; }
    .tp-body { flex: 1; display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 1rem; padding: 1rem max(16px, env(safe-area-inset-right, 0px)) 1.5rem max(16px, env(safe-area-inset-left, 0px)); align-items: start; }
    @media (max-width: 991.98px) { .tp-body { grid-template-columns: minmax(0, 1fr); } }
    .tp-main { display: grid; gap: 1rem; min-width: 0; }
    .tp-main-code { grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr); }
    .tp-main-code .tp-nav { grid-column: 1 / -1; }
    @media (max-width: 1199.98px) { .tp-main-code { grid-template-columns: minmax(0, 1fr); } }
    .tp-card { background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); border-radius: 16px; padding: 1.25rem; box-shadow: var(--pm-card-shadow); min-width: 0; }
    .tp-qhead { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; margin-bottom: .75rem; }
    .tp-qno { font-weight: 700; font-size: .85rem; color: var(--pm-link); }
    .tp-tag { font-size: .7rem; font-weight: 600; padding: .15rem .55rem; border-radius: 999px; background: var(--pm-soft); color: var(--pm-label); }
    .tp-qtext { white-space: pre-wrap; font-size: 1rem; line-height: 1.6; color: var(--pm-heading); }
    .tp-options { display: grid; gap: .6rem; }
    .tp-option { all: unset; box-sizing: border-box; display: flex; gap: .85rem; align-items: center; padding: .85rem 1rem; border-radius: 12px; cursor: pointer;
      border: 1.5px solid var(--pm-card-border); background: var(--pm-card-bg); transition: border-color .12s, background .12s; }
    .tp-option:hover { border-color: var(--pm-link); }
    .tp-option:focus-visible { outline: 2px solid var(--pm-focus); outline-offset: 2px; }
    .tp-option.sel { border-color: var(--pm-link); background: rgba(11,92,255,.08); }
    .tp-letter { width: 2rem; height: 2rem; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; font-weight: 700; font-size: .85rem;
      background: var(--pm-soft); color: var(--pm-label); }
    .tp-option.sel .tp-letter { background: var(--pm-link); color: #fff; }
    .tp-otext { color: var(--pm-heading); }
    .tp-sample { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: .6rem; margin-bottom: .75rem; }
    .tp-sample pre, .tp-err { margin: 0; padding: .6rem .75rem; border-radius: 10px; background: var(--pm-soft); color: var(--pm-heading);
      font-size: .82rem; white-space: pre-wrap; overflow-x: auto; max-height: 180px; }
    .tp-err { color: #be123c; background: rgba(225,29,72,.08); margin-top: .4rem; }
    .tp-label { font-size: .68rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--pm-muted); margin-bottom: .3rem; }
    .tp-editor-card { display: flex; flex-direction: column; gap: .75rem; padding: 1rem; }
    .tp-ed-bar { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; }
    .tp-editor { display: flex; border-radius: 12px; overflow: hidden; border: 1px solid #1e293b; background: #0f172a; height: 420px; }
    .tp-gutter { padding: .75rem .5rem; text-align: right; color: #64748b; background: #0b1220; font: .82rem/1.55 ui-monospace, 'JetBrains Mono', Consolas, monospace;
      overflow: hidden; user-select: none; min-width: 2.6rem; }
    .tp-code { flex: 1; border: 0; outline: 0; resize: none; padding: .75rem; background: transparent; color: #e2e8f0; caret-color: #38bdf8; tab-size: 4;
      font: .82rem/1.55 ui-monospace, 'JetBrains Mono', Consolas, monospace; white-space: pre; overflow: auto; }
    .tp-run { display: grid; gap: .5rem; }
    .tp-case { border: 1px solid rgba(225,29,72,.35); border-radius: 10px; padding: .6rem .75rem; }
    .tp-case.ok { border-color: rgba(22,163,74,.35); }
    .tp-case-head { font-weight: 700; font-size: .85rem; margin-bottom: .3rem; color: #be123c; }
    .tp-case.ok .tp-case-head { color: #15803d; margin-bottom: 0; }
    .tp-nav { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; }
    .tp-side { position: sticky; top: 76px; display: grid; gap: .4rem; }
    @media (max-width: 991.98px) { .tp-side { position: static; } }
    .tp-palette { display: grid; grid-template-columns: repeat(auto-fill, minmax(36px, 1fr)); gap: .4rem; margin-bottom: .5rem; }
    .tp-pal { all: unset; box-sizing: border-box; height: 36px; border-radius: 10px; display: grid; place-items: center; cursor: pointer; font-size: .8rem; font-weight: 700;
      background: var(--pm-soft); color: var(--pm-label); border: 2px solid transparent; position: relative; font-variant-numeric: tabular-nums; }
    .tp-pal:focus-visible { outline: 2px solid var(--pm-focus); }
    .tp-pal.ans { background: #16a34a; color: #fff; }
    .tp-pal.mark::after { content: ''; position: absolute; top: -3px; right: -3px; width: 10px; height: 10px; border-radius: 50%; background: #f59e0b; border: 2px solid var(--pm-card-bg); }
    .tp-pal.cur { border-color: var(--pm-link); }
    .tp-legend { display: grid; gap: .3rem; font-size: .78rem; color: var(--pm-muted); border-top: 1px solid var(--pm-divider); padding-top: .6rem; }
    .tp-dot { display: inline-block; width: .7rem; height: .7rem; border-radius: 3px; background: var(--pm-soft); margin-right: .35rem; vertical-align: -1px; }
    .tp-dot.ans { background: #16a34a; } .tp-dot.mark { background: #f59e0b; border-radius: 50%; }
    .st-h { color: var(--pm-heading); font-weight: 800; }
  `],
})
export class TestPlayer implements OnInit {
  readonly id = input.required<string>();
  private prep = inject(PrepService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private destroyRef = inject(DestroyRef);
  theme = inject(ThemeService);
  private portal = inject(StudentPortalService);
  private codeEl = viewChild<ElementRef<HTMLTextAreaElement>>('code');
  private gutterEl = viewChild<ElementRef<HTMLElement>>('gutter');

  readonly letters = ['A', 'B', 'C', 'D', 'E', 'F'];
  readonly langLabels = LANGUAGE_LABELS;
  view = signal<AttemptView | null>(null);
  error = signal('');
  idx = signal(0);
  answers = signal<Record<string, PrepAnswer>>({});
  marked = signal<Set<string>>(new Set());
  /** Per question, per language drafts, so switching language does not lose code. */
  private drafts: Record<string, Partial<Record<PrepLanguage, string>>> = {};
  remaining = signal(0);
  running = signal(false);
  runResult = signal<RunResponse | null>(null);
  submitting = signal(false);
  saveState = signal<'saved' | 'saving' | 'dirty' | 'error'>('saved');
  private dirty = new Set<string>();
  private saveTimer?: ReturnType<typeof setTimeout>;
  private clockOffset = 0;
  /** Full screen: on browsers without support (iPhone Safari) the test simply runs without the lock. */
  readonly fsSupported = fullscreenSupported();
  isFull = signal(isFullscreen());
  exits = signal(0);
  private leaving = signal(false);
  locked = computed(() => this.fsSupported && !this.isFull() && !!this.view() && !this.submitting() && !this.leaving());
  roll = computed(() => this.portal.me()?.rollNumber ?? '');
  /** Tiled "ProveYu" mark, faint enough to read through, visible to OCR / AI tools in a screenshot. */
  watermark = computed(() => watermarkUrl(this.theme.dark()));

  qs = computed(() => this.view()?.questions ?? []);
  cur = computed(() => this.qs()[this.idx()]);
  answeredCount = computed(() => this.qs().filter(q => this.isAnswered(q)).length);
  groups = computed(() => {
    const out: { name: string; items: number[] }[] = [];
    this.qs().forEach((q, i) => {
      const last = out[out.length - 1];
      if (last && last.name === q.section) last.items.push(i); else out.push({ name: q.section, items: [i] });
    });
    return out;
  });
  clock = computed(() => {
    const s = Math.max(0, this.remaining());
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return (h ? h + ':' : '') + String(m).padStart(2, '0') + ':' + String(sec).padStart(2, '0');
  });
  saveLabel = computed(() => ({ saved: 'All answers saved', saving: 'Saving…', dirty: 'Saving…', error: 'Not saved, retrying' }[this.saveState()]));
  lineNumbers = computed(() => {
    const q = this.cur();
    this.answers(); // re-evaluate when code changes
    const lines = q?.type === 'CODING' ? this.codeOf(q).split('\n').length : 0;
    return Array.from({ length: Math.max(lines, 1) }, (_, i) => i + 1);
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => exitFullscreen());
    this.prep.attempt(this.id()).subscribe({
      next: v => {
        this.clockOffset = new Date(v.serverNow).getTime() - Date.now();
        this.answers.set(v.answers ?? {});
        for (const [qid, a] of Object.entries(v.answers ?? {})) if (a.language && a.code !== undefined) this.drafts[qid] = { [a.language]: a.code };
        this.view.set(v);
        this.tick();
        const t = setInterval(() => this.tick(), 1000);
        this.destroyRef.onDestroy(() => { clearInterval(t); clearTimeout(this.saveTimer); });
        if (!this.portal.me()) this.portal.profile().subscribe({ error: () => undefined }); // roll number for the watermark
      },
      error: (e: AppError) => {
        if (e.code === 'ATTEMPT_SUBMITTED') { this.router.navigate(['/student/prep/result', this.id()]); return; }
        this.error.set(e.message || 'Could not open this test.');
      },
    });
  }

  // ---------------------------------------------------------------- full screen
  @HostListener('document:fullscreenchange')
  onFullscreenChange(): void {
    const full = isFullscreen();
    if (this.isFull() && !full && !this.leaving() && !this.submitting()) {
      this.exits.update(n => n + 1);
      this.flush();
    }
    this.isFull.set(full);
  }

  async returnToFullscreen(): Promise<void> {
    if (!(await enterFullscreen())) this.toast.error('Your browser did not allow full screen. Check its settings and try again.');
  }

  async confirmExit(): Promise<void> {
    const r = await this.confirm.ask({
      title: 'Leave the test?',
      message: 'The timer keeps running. You can resume from Preparation before time is up; after that your saved answers are submitted.',
      confirmText: 'Leave test', tone: 'danger',
    });
    if (!r.ok) return;
    this.leaving.set(true);
    this.flush();
    exitFullscreen();
    this.router.navigateByUrl('/student/prep');
  }

  /** Copied question text carries a ProveYu tag, so pasting it into an AI tool still shows where it came from. */
  onCopy(e: ClipboardEvent): void {
    const text = window.getSelection()?.toString() ?? '';
    if (!text.trim() || !e.clipboardData) return;
    e.preventDefault();
    e.clipboardData.setData('text/plain', `${text}\n\n[ProveYu mock test question${this.roll() ? ' · ' + this.roll() : ''} · © ProveYu]`);
  }

  @HostListener('document:visibilitychange')
  onHide(): void { if (document.hidden) this.flush(); }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(e: BeforeUnloadEvent): void { if (this.dirty.size) e.preventDefault(); }

  private tick(): void {
    const v = this.view();
    if (!v) return;
    const left = Math.round((new Date(v.deadlineAt).getTime() - (Date.now() + this.clockOffset)) / 1000);
    this.remaining.set(left);
    if (left === 300) this.toast.warning('5 minutes left');
    if (left <= 0 && !this.submitting()) this.submit(true);
  }

  // ---------------------------------------------------------------- answers
  isAnswered(q: QuestionView): boolean {
    const a = this.answers()[q.id];
    if (!a) return false;
    return q.type === 'MCQ' ? a.option !== undefined && a.option !== null : !!a.code && a.code.trim() !== (q.starterCode?.[a.language!] ?? '').trim();
  }
  choose(q: QuestionView, option: number): void { this.setAnswer(q.id, { option }); }
  clear(q: QuestionView): void {
    this.answers.update(a => { const { [q.id]: _, ...rest } = a; return rest; });
    this.dirty.add(q.id); this.queueSave();
  }
  lang(q: QuestionView): PrepLanguage { return this.answers()[q.id]?.language ?? (this.view()!.languages.find(l => q.starterCode?.[l]) ?? 'python'); }
  codeOf(q: QuestionView): string {
    const l = this.lang(q);
    return this.drafts[q.id]?.[l] ?? q.starterCode?.[l] ?? '';
  }
  typeCode(q: QuestionView, code: string): void {
    const l = this.lang(q);
    this.drafts[q.id] = { ...this.drafts[q.id], [l]: code };
    this.setAnswer(q.id, { language: l, code });
  }
  setLang(q: QuestionView, l: PrepLanguage): void {
    this.setAnswer(q.id, { language: l, code: this.drafts[q.id]?.[l] ?? q.starterCode?.[l] ?? '' });
    this.runResult.set(null);
  }
  async resetCode(q: QuestionView): Promise<void> {
    const r = await this.confirm.ask({ title: 'Reset to starter code?', message: 'Your code for this language will be replaced.', tone: 'danger', confirmText: 'Reset' });
    if (!r.ok) return;
    const l = this.lang(q);
    this.drafts[q.id] = { ...this.drafts[q.id], [l]: q.starterCode?.[l] ?? '' };
    this.setAnswer(q.id, { language: l, code: q.starterCode?.[l] ?? '' });
  }
  private setAnswer(qid: string, a: PrepAnswer): void {
    this.answers.update(all => ({ ...all, [qid]: a }));
    this.dirty.add(qid);
    this.queueSave();
  }

  // ---------------------------------------------------------------- autosave
  private queueSave(): void {
    this.saveState.set('dirty');
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.flush(), 1000);
  }
  private flush(): void {
    if (!this.dirty.size || this.submitting()) return;
    const ids = [...this.dirty];
    this.dirty.clear();
    const payload: Record<string, PrepAnswer> = {};
    ids.forEach(id => payload[id] = this.answers()[id] ?? {});
    this.saveState.set('saving');
    this.prep.save(this.id(), payload).subscribe({
      next: () => this.saveState.set(this.dirty.size ? 'dirty' : 'saved'),
      error: () => { ids.forEach(id => this.dirty.add(id)); this.saveState.set('error'); this.saveTimer = setTimeout(() => this.flush(), 4000); },
    });
  }

  // ---------------------------------------------------------------- navigation
  go(i: number): void {
    if (i < 0 || i >= this.qs().length) return;
    this.idx.set(i);
    this.runResult.set(null);
    window.scrollTo({ top: 0 });
  }
  toggleMark(): void {
    const id = this.cur().id;
    this.marked.update(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }

  // ---------------------------------------------------------------- code editor
  editorKey(e: KeyboardEvent, q: QuestionView): void {
    const el = e.target as HTMLTextAreaElement;
    if (e.key === 'Tab' && !e.shiftKey) {
      e.preventDefault();
      el.setRangeText('    ', el.selectionStart, el.selectionEnd, 'end');
      this.typeCode(q, el.value);
    } else if (e.key === 'Enter') {
      // keep the current line's indentation
      e.preventDefault();
      const before = el.value.slice(0, el.selectionStart);
      const indent = (before.split('\n').pop() ?? '').match(/^\s*/)?.[0] ?? '';
      const extra = /[{:]\s*$/.test(before) ? '    ' : '';
      el.setRangeText('\n' + indent + extra, el.selectionStart, el.selectionEnd, 'end');
      this.typeCode(q, el.value);
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      this.runCode(q);
    }
  }
  syncGutter(): void {
    const c = this.codeEl()?.nativeElement, g = this.gutterEl()?.nativeElement;
    if (c && g) g.scrollTop = c.scrollTop;
  }
  runCode(q: QuestionView): void {
    this.running.set(true);
    this.runResult.set(null);
    this.flush();
    this.prep.run(this.id(), q.id, this.lang(q), this.codeOf(q)).subscribe({
      next: r => { this.running.set(false); this.runResult.set(r); },
      error: () => this.running.set(false),
    });
  }

  // ---------------------------------------------------------------- submit
  async confirmSubmit(): Promise<void> {
    const left = this.qs().length - this.answeredCount();
    const r = await this.confirm.ask({
      title: 'Submit your test?',
      message: `${this.answeredCount()} answered, ${left} not answered${this.marked().size ? `, ${this.marked().size} marked for review` : ''}. You cannot change answers after submitting.`,
      confirmText: 'Submit test',
    });
    if (r.ok) this.submit(false);
  }
  private submit(auto: boolean): void {
    this.submitting.set(true);
    clearTimeout(this.saveTimer);
    if (auto) this.toast.info('Time is up. Submitting your test…');
    this.prep.submit(this.id(), this.answers()).subscribe({
      next: () => { this.dirty.clear(); this.router.navigate(['/student/prep/result', this.id()], { replaceUrl: true }); },
      error: () => this.submitting.set(false),
    });
  }
  exit(): void { exitFullscreen(); this.router.navigateByUrl('/student/prep'); }
}

function watermarkUrl(dark: boolean): string {
  const fill = dark ? '#ffffff' : '#0b1220';
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='200' height='110'><text x='100' y='60' text-anchor='middle' transform='rotate(-22 100 55)' `
    + `fill='${fill}' fill-opacity='0.085' font-family='Inter, Arial, sans-serif' font-size='15' font-weight='700' letter-spacing='1'>ProveYu</text></svg>`;
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}
