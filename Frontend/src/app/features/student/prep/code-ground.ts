import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { LANGUAGE_LABELS, PlaygroundResult, PrepLanguage, PrepService } from '../services/prep.service';

const DRAFT_KEY = 'pm-code-ground-draft';

/**
 * Code ground: a plain compiler. Type a program, give it an input, press Run and see what it prints. Nothing is scored.
 */
@Component({
  selector: 'pm-code-ground',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student-pages.scss',
  template: `
    <section class="st-card mb-3">
      <h2 class="h5 st-h mb-1">Code ground</h2>
      <p class="small st-muted mb-0">Write a program, give it an input and run it. It is practice only: nothing is scored and nothing is saved on the server.
        Read the input with <code>input()</code> (Python), <code>Scanner</code> (Java), <code>cin</code> (C++) or <code>readFileSync(0)</code> (JavaScript).</p>
    </section>

    <section class="st-card mb-3" aria-label="Editor">
      <div class="d-flex flex-wrap gap-2 align-items-center mb-2">
        <label for="cg-lang" class="small st-muted mb-0">Language</label>
        <select id="cg-lang" class="form-select form-select-sm w-auto" [value]="language()" (change)="setLanguage($any($event.target).value)">
          @for (l of languages; track l) { <option [value]="l">{{ labels[l] }}</option> }
        </select>
        <button type="button" class="btn btn-sm btn-outline-secondary ms-auto" (click)="clearCode()">Clear</button>
      </div>
      @if (language() === 'java') {
        <p class="small st-muted mb-2">In Java the class must be named <code>Main</code>.</p>
      }
      <textarea class="form-control font-monospace small" rows="14" spellcheck="false" aria-label="Code"
        [value]="code()" (input)="setCode($any($event.target).value)" (keydown)="onKey($event)"></textarea>

      <label for="cg-stdin" class="form-label small st-muted mt-3 mb-1">Input (what the program reads)</label>
      <textarea id="cg-stdin" class="form-control font-monospace small" rows="3" spellcheck="false"
        [value]="stdin()" (input)="stdin.set($any($event.target).value)" placeholder="e.g. 5 9"></textarea>

      <div class="d-flex align-items-center gap-2 mt-3">
        <button type="button" class="btn btn-primary" [disabled]="running() || !code().trim()" (click)="run()">
          @if (running()) { <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span> Running… } @else { <i class="bi bi-play-fill me-1" aria-hidden="true"></i>Run }
        </button>
        <span class="small st-muted">Ctrl + Enter also runs</span>
      </div>
    </section>

    <section class="st-card" aria-live="polite" aria-label="Output">
      <h3 class="h6 st-h mb-2">Output</h3>
      @if (!result() && !running()) {
        <p class="small st-muted mb-0">Press Run to see the result here.</p>
      }
      @if (result(); as r) {
        @if (r.compileError) {
          <div class="alert alert-danger small mb-0">
            <strong>Compile error</strong>
            <pre class="cg-out mb-0 mt-2">{{ r.compileError }}</pre>
          </div>
        } @else {
          @if (r.timedOut) {
            <div class="alert alert-warning small">Time limit exceeded: the program ran too long. Check for an endless loop.</div>
          }
          @if (r.exitCode !== 0 && r.exitCode !== null && !r.timedOut) {
            <div class="alert alert-danger small">The program stopped with an error. The message is below.</div>
          }
          <div class="small st-muted mb-1">Output</div>
          <pre class="cg-out">{{ r.stdout || '(nothing printed)' }}</pre>
          @if (r.stderr) {
            <div class="small st-muted mb-1 mt-2">Error message</div>
            <pre class="cg-out cg-err mb-0">{{ r.stderr }}</pre>
          }
        }
      }
    </section>
  `,
  styles: [`
    .cg-out { background: var(--pm-card-bg); border: 1px solid var(--pm-card-border); border-radius: 10px; padding: .75rem; white-space: pre-wrap; word-break: break-word; min-height: 3rem; font-size: .85rem; }
    .cg-err { color: var(--bs-danger); }
  `],
})
export class CodeGround {
  private prep = inject(PrepService);

  readonly labels = LANGUAGE_LABELS;
  readonly languages = Object.keys(LANGUAGE_LABELS) as PrepLanguage[];

  language = signal<PrepLanguage>(this.loadDraft().language);
  code = signal<string>(this.loadDraft().code);
  stdin = signal<string>('');
  running = signal(false);
  result = signal<PlaygroundResult | null>(null);

  setLanguage(value: string): void {
    this.language.set(value as PrepLanguage);
    this.saveDraft();
  }

  setCode(value: string): void {
    this.code.set(value);
    this.saveDraft();
  }

  clearCode(): void {
    this.code.set('');
    this.result.set(null);
    this.saveDraft();
  }

  onKey(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      this.run();
    }
  }

  run(): void {
    if (this.running() || !this.code().trim()) return;
    this.running.set(true);
    this.result.set(null);
    this.prep.playground(this.language(), this.code(), this.stdin()).subscribe({
      next: r => { this.result.set(r); this.running.set(false); },
      error: () => this.running.set(false), // the error interceptor already shows why (for example "too fast")
    });
  }

  // The last code is kept in this browser so a refresh does not lose it. Storage can be blocked, so every access is guarded.
  private loadDraft(): { language: PrepLanguage; code: string } {
    const fallback = { language: 'python' as PrepLanguage, code: 'a, b = map(int, input().split())\nprint(a + b)\n' };
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return fallback;
      const d = JSON.parse(raw) as { language?: PrepLanguage; code?: string };
      return { language: d.language && d.language in LANGUAGE_LABELS ? d.language : fallback.language, code: d.code ?? fallback.code };
    } catch {
      return fallback;
    }
  }

  private saveDraft(): void {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ language: this.language(), code: this.code() }));
    } catch {
      /* storage blocked: the code simply is not kept */
    }
  }
}
