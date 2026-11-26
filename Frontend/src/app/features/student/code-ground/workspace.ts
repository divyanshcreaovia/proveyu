import { DatePipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { ThemeService } from '../../../core/services/theme.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { CodeEditor, lockedParts } from './code-editor';
import { Language, LanguageKey, Problem, RunResult, Sample, Submission, TopicCard } from './code-ground.models';
import { CodeGroundService, CodeLanguage } from './code-ground.service';
import { ProblemTable } from './problem-table';

// JavaScript drafts keep the key they had before other languages were added
const draftKey = (problem: string, language: LanguageKey) => `proveyu_cg_${read('userId')}_${problem}${language === 'javascript' ? '' : '_' + language}`;
const LAYOUT_KEY = 'proveyu_cg_layout';
function read(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function store(key: string, value: string | null): void {
  try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* storage blocked */ }
}
/** Set while the copy in this browser has not reached the server; that copy then wins over the server's. */
const unsaved = (key: string) => key + '_unsaved';
const SAVE_AFTER_MS = 1500;
/** An older draft keeps the student's code but gets today's input / print code. */
function withCurrentHarness(code: string, starter: string, language: LanguageKey): string {
  const { tail } = lockedParts(starter, language);
  const marker = code.indexOf('no need to change below');
  return !tail || marker < 0 || code.endsWith(tail) ? code : code.slice(0, code.lastIndexOf('\n', marker)) + tail;
}

type Verdict = { label: string; tone: 'ok' | 'bad' };

/**
 * The problem workspace, laid out like the big practice platforms: description on the left; code editor and a test console on the right,
 * separated by draggable (and keyboard-adjustable) dividers. Run tests checks every test on the server; Show hint and Reset code too.
 */
@Component({
  selector: 'pm-workspace',
  imports: [CodeEditor, ProblemTable, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  host: { '(document:keydown.escape)': 'drawer.set(false)' },
  template: `
    @let p = problem();
    <div class="ws" #root [style.height.px]="height()">
      <!-- toolbar -->
      <div class="ws-bar">
        <div class="ws-bar-l">
          <button type="button" class="cg-btn cg-btn-ghost" (click)="back.emit()"><i class="bi bi-arrow-left" aria-hidden="true"></i>
            <span class="d-none d-md-inline">{{ topic().title }}</span></button>
          <span class="ws-sep" aria-hidden="true"></span>
          <button type="button" class="cg-btn cg-btn-ghost" [attr.aria-expanded]="drawer()" (click)="openDrawer()">
            <i class="bi bi-list-ul" aria-hidden="true"></i><span class="d-none d-sm-inline">Problem list</span></button>
          <button type="button" class="cg-btn cg-btn-ghost cg-btn-icon" [disabled]="!prev()" (click)="open.emit(prev()!)" aria-label="Previous problem">
            <i class="bi bi-chevron-left" aria-hidden="true"></i></button>
          <button type="button" class="cg-btn cg-btn-ghost cg-btn-icon" [disabled]="!next()" (click)="open.emit(next()!)" aria-label="Next problem">
            <i class="bi bi-chevron-right" aria-hidden="true"></i></button>
        </div>
        <div class="ws-bar-r">
          <button type="button" class="cg-btn cg-btn-ghost ws-hist" (click)="showHistory()" [attr.aria-pressed]="tab() === 'history'" title="Your past submissions">
            <i class="bi bi-clock-history" aria-hidden="true"></i> <span class="d-none d-sm-inline">Submissions</span></button>
          <button type="button" class="cg-btn ws-run" [disabled]="running()" (click)="run()" aria-keyshortcuts="Control+Enter" title="Try one input (Ctrl + Enter)">
            @if (running() === 'run') { <span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Running… }
            @else { <i class="bi bi-play-fill" aria-hidden="true"></i> Run }
          </button>
          <button type="button" class="cg-btn cg-btn-run ws-submit" [disabled]="running()" (click)="submit()" aria-keyshortcuts="Control+Shift+Enter"
                  title="Check all tests (Ctrl + Shift + Enter)">
            @if (running() === 'submit') { <span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Submitting… }
            @else { <i class="bi bi-cloud-upload" aria-hidden="true"></i> Submit }
          </button>
        </div>
      </div>

      <div class="ws-body" [style.--left.%]="left()" [style.--console.px]="consoleH()">
        <!-- description -->
        <section class="cg-panel ws-pane ws-desc" aria-label="Problem description">
          <div class="cg-tabs" role="tablist"><span class="cg-tab" role="tab" aria-selected="true"><i class="bi bi-file-text" aria-hidden="true"></i> Description</span></div>
          <div class="ws-scroll">
            <h2 class="ws-title">{{ number() }}. {{ p.title }}</h2>
            <div class="ws-meta">
              <span class="cg-diff-pill" [attr.data-level]="p.difficulty">{{ level() }}</span>
              <span class="ws-chip"><i class="bi bi-tag" aria-hidden="true"></i> {{ topic().title }}</span>
              @if (solved()) { <span class="ws-solved"><i class="bi bi-check-circle-fill" aria-hidden="true"></i> Solved</span> }
            </div>
            <p class="ws-text">{{ p.statement }}</p>
            @if (p.note) { <p class="ws-text ws-muted">{{ p.note }}</p> }

            <h3 class="ws-h">Example 1</h3>
            <div class="ws-example cg-code">
              <div><strong>Input:</strong></div><pre>{{ p.example.input || '(empty)' }}</pre>
              <div><strong>Output:</strong></div><pre>{{ p.example.output || '(nothing)' }}</pre>
            </div>

            <h3 class="ws-h">Input format</h3>
            <ul class="ws-list">@for (f of p.inputFormat; track $index) { <li><code>{{ f.split(':')[0] }}</code>{{ f.slice(f.indexOf(':')) }}</li> }</ul>
            <p class="ws-muted ws-small">Write <code>solve()</code> and return the answer; reading the input and printing are done for you. Arrays print space separated.</p>

            <div class="ws-hint">
              <button type="button" class="ws-hint-head" [attr.aria-expanded]="hint()" (click)="hint.set(!hint())">
                <span><i class="bi bi-lightbulb" aria-hidden="true"></i> {{ hint() ? 'Hide hint' : 'Show hint' }}</span>
                <i class="bi" [class.bi-chevron-down]="!hint()" [class.bi-chevron-up]="hint()" aria-hidden="true"></i></button>
              @if (hint()) { <div class="ws-hint-body" role="status">{{ p.hint }}</div> }
            </div>
          </div>
        </section>

        <div class="ws-split ws-split-v" role="separator" aria-orientation="vertical" aria-label="Resize the description" tabindex="0"
             [attr.aria-valuenow]="left()" aria-valuemin="25" aria-valuemax="65" (pointerdown)="drag($event, 'left')" (keydown)="nudge($event, 'left')"></div>

        <div class="ws-right">
          <!-- editor -->
          <section class="cg-panel ws-pane ws-editor" aria-label="Your code">
            <div class="cg-tabs">
              <span class="cg-tab" aria-selected="true"><i class="bi bi-code-slash" aria-hidden="true"></i> Code</span>
              <label class="ws-lang">
                <span class="visually-hidden">Language</span>
                <select [disabled]="running()" (change)="choose($any($event.target).value)" aria-label="Programming language">
                  @for (l of languageList(); track l.key) { <option [value]="l.key" [selected]="l.key === lang()">{{ l.label }}</option> }
                </select>
                <i class="bi bi-chevron-down" aria-hidden="true"></i>
              </label>
              <span class="ws-grow"></span>
              <span class="ws-save" role="status">{{ saveState() }}</span>
              <button type="button" class="cg-btn cg-btn-ghost ws-tool" (click)="reset()"><i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i> Reset code</button>
            </div>
            <div class="ws-code"><pm-code-editor [value]="code()" [language]="lang()" [starter]="starter()" [dark]="theme.dark()" [label]="'Code editor for ' + p.title" (valueChange)="edit($event)" (run)="run()" (submit)="submit()" (clipboardBlocked)="noClipboard()" /></div>
          </section>

          <div class="ws-split ws-split-h" role="separator" aria-orientation="horizontal" aria-label="Resize the test console" tabindex="0"
               [attr.aria-valuenow]="consoleH()" aria-valuemin="120" aria-valuemax="560" (pointerdown)="drag($event, 'console')" (keydown)="nudge($event, 'console')"></div>

          <!-- console -->
          <section class="cg-panel ws-pane ws-console" aria-label="Tests">
            <div class="cg-tabs" role="tablist">
              <button type="button" role="tab" class="cg-tab" [attr.aria-selected]="tab() === 'case'" (click)="tab.set('case')">
                <i class="bi bi-ui-checks" aria-hidden="true"></i> Testcase</button>
              <button type="button" role="tab" class="cg-tab" [attr.aria-selected]="tab() === 'result'" (click)="tab.set('result')">
                <i class="bi bi-terminal" aria-hidden="true"></i> Test result</button>
              <button type="button" role="tab" class="cg-tab" [attr.aria-selected]="tab() === 'history'" (click)="showHistory()">
                <i class="bi bi-clock-history" aria-hidden="true"></i> Submissions</button>
            </div>
            <div class="ws-scroll ws-console-body" aria-live="polite">
              @if (tab() === 'case') {
                <div class="ws-io">
                  <div class="ws-k ws-k-row"><label for="ws-input">Input (change it to try your own)</label>
                    @if (!isExample()) { <button type="button" class="ws-link" (click)="input.set(p.example.input)">Use the example</button> }</div>
                  <textarea id="ws-input" class="cg-code ws-input" rows="3" spellcheck="false" [value]="input()" (input)="input.set($any($event.target).value)"
                            (keydown.control.enter)="run()" (keydown.meta.enter)="run()"></textarea>
                </div>
                @if (isExample()) { <div class="ws-io"><div class="ws-k">Expected output</div><pre class="cg-code">{{ p.example.output || '(nothing)' }}</pre></div> }
                <p class="ws-muted ws-small mb-0"><strong>Run</strong> tries this input. <strong>Submit</strong> checks {{ p.testCount }} testcases and then
                  {{ p.hiddenCount }} hidden ones. Print anything you like to debug: it is shown separately and does not affect the answer.</p>
                <p class="ws-own ws-small mb-0"><i class="bi bi-info-circle" aria-hidden="true"></i> Write your own code. Every submission is saved with its code and time.</p>
              } @else if (tab() === 'history') {
                @if (history(); as list) {
                  @for (s of list; track s.id) {
                    <div class="ws-sub">
                      <button type="button" class="ws-sub-row" [attr.aria-expanded]="openSub() === s.id" (click)="openSub.set(openSub() === s.id ? null : s.id)">
                        <span class="ws-sub-verdict" [class.ws-v-ok]="s.verdict === 'ACCEPTED'" [class.ws-v-bad]="s.verdict !== 'ACCEPTED'">{{ verdictName[s.verdict] }}</span>
                        <span class="ws-muted">{{ s.passed }} / {{ s.total }}</span>
                        <span class="ws-muted">{{ languageName(s.language) }}</span>
                        <span class="ws-muted ws-sub-when">{{ s.createdAt | date: 'd MMM, h:mm a' }}</span>
                        <i class="bi" [class.bi-chevron-down]="openSub() !== s.id" [class.bi-chevron-up]="openSub() === s.id" aria-hidden="true"></i>
                      </button>
                      @if (openSub() === s.id) {
                        <pre class="cg-code ws-sub-code">{{ ownCode(s.code) }}</pre>
                        <button type="button" class="cg-btn ws-sub-use" (click)="useSubmission(s)"><i class="bi bi-arrow-up-square" aria-hidden="true"></i> Put this code in the editor</button>
                      }
                    </div>
                  } @empty { <div class="ws-empty"><i class="bi bi-clock-history" aria-hidden="true"></i> Nothing submitted yet.</div> }
                } @else if (historyFailed()) {
                  <div class="ws-empty">Could not load your submissions. <button type="button" class="ws-link" (click)="showHistory()">Try again</button></div>
                } @else { <div class="ws-empty">Loading…</div> }
              } @else if (running()) {
                <div class="ws-judging"><span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  {{ running() === 'run' ? 'Running your code…' : 'Judging your code on all testcases…' }}</div>
              } @else if (sample(); as s) {
                @if (verdict(); as v) { <div class="ws-verdict" [class.ws-v-ok]="v.tone === 'ok'" [class.ws-v-bad]="v.tone === 'bad'">{{ v.label }}</div> }
                @if (s.compileError) {
                  <pre class="cg-code ws-err">{{ s.compileError }}</pre>
                } @else {
                  <div class="ws-io"><div class="ws-k">Input</div><pre class="cg-code">{{ s.input || '(empty)' }}</pre></div>
                  <div class="ws-io"><div class="ws-k">Your answer</div><pre class="cg-code" [class.ws-wrong]="s.passed === false">{{ s.actual || '(nothing)' }}</pre></div>
                  @if (s.expected !== null) { <div class="ws-io"><div class="ws-k">Expected</div><pre class="cg-code">{{ s.expected || '(nothing)' }}</pre></div> }
                  @if (s.printed) { <div class="ws-io"><div class="ws-k">Printed by your code</div><pre class="cg-code ws-printed">{{ s.printed }}</pre></div> }
                  @if (s.error) { <div class="ws-io"><div class="ws-k">Error</div><pre class="cg-code ws-err">{{ s.error }}</pre></div> }
                  @if (s.passed) { <p class="ws-muted ws-small mb-0">The example works. Submit to check every testcase.</p> }
                }
              } @else if (result(); as r) {
                @if (verdict(); as v) {
                  @if (r.bonus) {
                    <p class="ws-bonus" role="status"><i class="bi bi-gift-fill" aria-hidden="true"></i> Problem of the day solved: +{{ r.bonus }} bonus points</p>
                  }
                  <div class="ws-verdict" [class.ws-v-ok]="v.tone === 'ok'" [class.ws-v-bad]="v.tone === 'bad'">
                    {{ v.label }}@if (!r.compileError) { <span>{{ passed() }} / {{ r.cases.length + p.hiddenCount }} testcases passed</span> }
                  </div>
                }
                @if (r.compileError) {
                  <pre class="cg-code ws-err">{{ r.compileError }}</pre>
                } @else {
                  @if (r.hidden.length) {
                    <div class="ws-hidden" aria-label="Hidden testcases">
                      <span class="ws-k">Hidden testcases</span>
                      @for (h of r.hidden; track h.index) {
                        <span class="ws-chip" [class.ws-h-ok]="h.passed" [class.ws-h-bad]="!h.passed">
                          <i class="bi" [class.bi-check-lg]="h.passed" [class.bi-x-lg]="!h.passed" aria-hidden="true"></i>
                          Hidden {{ h.index }} {{ h.passed ? 'passed' : h.timedOut ? 'timed out' : 'failed' }}</span>
                      }
                    </div>
                    @if (!r.allPassed) { <p class="ws-muted ws-small">Your code works on the examples but not on every input. Think about edge cases.</p> }
                  }
                  <div class="ws-cases" role="tablist" aria-label="Testcases">
                    @for (c of r.cases; track c.index) {
                      <button type="button" role="tab" class="ws-case" [class.ws-case-on]="c.index === caseNo()" [attr.aria-selected]="c.index === caseNo()"
                              (click)="caseNo.set(c.index)">
                        <span class="ws-dot" [class.ws-dot-ok]="c.passed" aria-hidden="true"></span> Case {{ c.index }}
                        <span class="visually-hidden">{{ c.passed ? 'passed' : 'failed' }}</span></button>
                    }
                  </div>
                  @if (shown(); as c) {
                    <div class="ws-io"><div class="ws-k">Input</div><pre class="cg-code">{{ c.input || '(empty)' }}</pre></div>
                    <div class="ws-io"><div class="ws-k">Output</div><pre class="cg-code" [class.ws-wrong]="!c.passed">{{ c.actual || '(nothing printed)' }}</pre></div>
                    <div class="ws-io"><div class="ws-k">Expected</div><pre class="cg-code">{{ c.expected || '(nothing)' }}</pre></div>
                    @if (c.printed) { <div class="ws-io"><div class="ws-k">Printed by your code</div><pre class="cg-code ws-printed">{{ c.printed }}</pre></div> }
                    @if (c.error) { <div class="ws-io"><div class="ws-k">Error</div><pre class="cg-code ws-err">{{ c.error }}</pre></div> }
                  }
                }
              } @else {
                <div class="ws-empty"><i class="bi bi-play-circle" aria-hidden="true"></i> Run or Submit to see the result here.</div>
              }
            </div>
          </section>
        </div>
      </div>
    </div>

    @if (drawer()) {
      <div class="ws-scrim" (click)="drawer.set(false)"></div>
      <aside class="ws-drawer" role="dialog" aria-modal="true" [attr.aria-label]="topic().title + ' problems'">
        <div class="ws-drawer-head">
          <div><div class="cg-kicker">Problem list</div><strong>{{ topic().title }}</strong></div>
          <button type="button" class="cg-btn cg-btn-ghost cg-btn-icon" (click)="drawer.set(false)" aria-label="Close the problem list"><i class="bi bi-x-lg"></i></button>
        </div>
        <pm-problem-table #list [topic]="topic()" [selected]="p.key" [compact]="true" (open)="drawer.set(false); open.emit($event)" />
      </aside>
    }`,
  styles: [`
    :host { display: block; }
    .ws { display: flex; flex-direction: column; gap: .5rem; min-height: 560px; }
    .ws-bar { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
    .ws-bar-l, .ws-bar-r { display: flex; align-items: center; gap: .25rem; min-width: 0; }
    .ws-sep { width: 1px; height: 20px; background: var(--cg-line); margin: 0 .25rem; }
    .ws-body { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(0, var(--left, 42%)) 8px minmax(0, 1fr); }
    .ws-right { min-height: 0; display: grid; grid-template-rows: minmax(0, 1fr) 8px var(--console, 240px); }
    .ws-pane { min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
    .ws-scroll { flex: 1; min-height: 0; overflow: auto; padding: 1rem 1.25rem; }
    .ws-split { position: relative; touch-action: none; }
    .ws-split::after { content: ''; position: absolute; border-radius: 2px; background: transparent; transition: background-color .15s ease; }
    .ws-split-v { cursor: col-resize; } .ws-split-v::after { top: 30%; bottom: 30%; left: 3px; width: 2px; }
    .ws-split-h { cursor: row-resize; } .ws-split-h::after { left: 40%; right: 40%; top: 3px; height: 2px; }
    .ws-split:hover::after, .ws-split:focus-visible::after, .ws-dragging::after { background: var(--pm-link); }
    .ws-split:focus-visible { outline: none; }

    .ws-title { font-size: 1.3rem; font-weight: 700; letter-spacing: -.01em; color: var(--pm-heading); margin: 0 0 .6rem; }
    .ws-meta { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-bottom: 1.1rem; }
    .ws-chip { display: inline-flex; align-items: center; gap: .3rem; padding: .12rem .55rem; border-radius: 999px; font-size: .72rem; font-weight: 500;
      background: var(--cg-sub); color: var(--pm-muted); }
    .ws-solved { font-size: .78rem; font-weight: 600; color: var(--cg-ok); }
    .ws-text { font-size: .92rem; line-height: 1.65; color: var(--pm-label); margin-bottom: .9rem; }
    .ws-muted { color: var(--pm-muted); }
    .ws-own { margin-top: .6rem; color: var(--cg-medium); font-weight: 600; }
    .ws-small { font-size: .8rem; }
    .ws-h { font-size: .9rem; font-weight: 700; color: var(--pm-heading); margin: 1.2rem 0 .55rem; }
    .ws-example { border-left: 2px solid var(--cg-line); padding: .1rem 0 .1rem .9rem; color: var(--pm-heading); }
    .ws-example { font-size: .86rem; }
    .ws-example pre { margin: .15rem 0 .6rem; padding: 0; background: none; border: 0; font-family: var(--cg-mono); font-size: .86rem; color: var(--pm-label);
      white-space: pre-wrap; word-break: break-word; }
    .ws-list { margin: 0 0 .6rem; padding-left: 1.1rem; font-size: .86rem; color: var(--pm-label); display: grid; gap: .25rem; }
    code { font-family: var(--cg-mono); font-size: .82em; padding: .05rem .35rem; border-radius: 5px; background: var(--cg-sub); color: var(--pm-heading); }
    .ws-hint { margin-top: 1.25rem; border-top: 1px solid var(--cg-line); }
    .ws-hint-head { all: unset; box-sizing: border-box; cursor: pointer; display: flex; justify-content: space-between; align-items: center; width: 100%;
      padding: .75rem 0; font-size: .86rem; font-weight: 600; color: var(--pm-heading); }
    .ws-hint-head:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 2px; border-radius: 4px; }
    .ws-hint-body { padding: 0 0 .75rem; font-size: .88rem; line-height: 1.6; color: var(--pm-label); }

    .ws-lang { position: relative; align-self: center; flex: 0 0 auto; margin: 0 0 0 .25rem; }
    .ws-lang select { appearance: none; height: 28px; padding: 0 1.7rem 0 .65rem; border-radius: 7px; font-size: .78rem; font-weight: 600; cursor: pointer;
      color: var(--pm-heading); background: var(--cg-panel); border: 1px solid var(--cg-line); }
    .ws-lang select:hover { background: var(--cg-sub); }
    .ws-lang select:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 1px; }
    .ws-lang i { position: absolute; right: .55rem; top: 50%; transform: translateY(-50%); font-size: .65rem; color: var(--pm-muted); pointer-events: none; }
    .ws-grow { flex: 1; }
    .ws-save { align-self: center; font-size: .72rem; color: var(--pm-muted); margin-right: .5rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ws-run { margin-right: .25rem; }
    .ws-k-row { display: flex; justify-content: space-between; gap: .5rem; }
    .ws-link { all: unset; cursor: pointer; color: var(--pm-link); font-weight: 600; }
    .ws-link:focus-visible { outline: 2px solid var(--pm-link); }
    .ws-input { width: 100%; resize: vertical; padding: .55rem .75rem; border-radius: 8px; border: 1px solid var(--cg-line); background: var(--cg-sub);
      color: var(--pm-heading); }
    .ws-input:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 1px; }
    .ws-printed { border-left: 3px solid var(--cg-warn); }
    .ws-tool { align-self: center; height: 28px; font-size: .78rem; flex: 0 0 auto; }
    .ws-pane .cg-tabs { overflow-x: auto; scrollbar-width: none; }
    .ws-pane .cg-tab { flex: 0 0 auto; }
    .ws-code { flex: 1; min-height: 0; }

    .ws-console-body { padding-top: .8rem; }
    .ws-cases { display: flex; flex-wrap: wrap; gap: .35rem; margin-bottom: .8rem; }
    .ws-case { all: unset; cursor: pointer; display: inline-flex; align-items: center; gap: .4rem; padding: .3rem .7rem; border-radius: 7px; font-size: .78rem;
      font-weight: 600; color: var(--pm-muted); }
    .ws-case:hover { background: var(--cg-sub); }
    .ws-case-on { background: var(--cg-sub); color: var(--pm-heading); }
    .ws-case:focus-visible { outline: 2px solid var(--pm-link); }
    .ws-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--cg-bad); }
    .ws-dot-ok { background: var(--cg-ok); }
    .ws-hidden { display: flex; flex-wrap: wrap; align-items: center; gap: .4rem; margin-bottom: .8rem; }
    .ws-hidden .ws-k { margin: 0 .25rem 0 0; }
    .ws-h-ok { color: var(--cg-ok); background: var(--cg-ok-bg); }
    .ws-h-bad { color: var(--cg-bad); background: var(--cg-bad-bg); }
    .ws-bonus { display: flex; align-items: center; gap: .5rem; margin: 0 0 .7rem; padding: .5rem .75rem; border-radius: 8px; font-size: .84rem; font-weight: 700;
      color: var(--cg-medium); background: var(--cg-medium-bg); }
    .ws-sub { border-bottom: 1px solid var(--cg-line); }
    .ws-sub-row { all: unset; box-sizing: border-box; cursor: pointer; display: flex; flex-wrap: wrap; align-items: center; gap: .35rem .9rem; width: 100%;
      padding: .55rem .1rem; font-size: .8rem; }
    .ws-sub-row:focus-visible { outline: 2px solid var(--pm-link); }
    .ws-sub-verdict { font-weight: 700; min-width: 8.5rem; }
    .ws-sub-when { margin-left: auto; }
    .ws-sub-code { margin: 0 0 .5rem; padding: .55rem .75rem; border-radius: 8px; background: var(--cg-sub); color: var(--pm-heading); max-height: 220px; overflow: auto; }
    .ws-sub-use { margin-bottom: .6rem; }
    .ws-io { margin-bottom: .7rem; }
    .ws-k { font-size: .74rem; font-weight: 600; color: var(--pm-muted); margin-bottom: .3rem; }
    .ws-io pre { margin: 0; padding: .55rem .75rem; border-radius: 8px; background: var(--cg-sub); color: var(--pm-heading); white-space: pre-wrap;
      word-break: break-word; max-height: 160px; overflow: auto; }
    .ws-wrong { color: var(--cg-bad) !important; }
    .ws-err { color: var(--cg-bad) !important; background: var(--cg-bad-bg) !important; }
    .ws-verdict { display: flex; align-items: baseline; gap: .85rem; flex-wrap: wrap; font-size: 1.15rem; font-weight: 700; margin-bottom: .85rem; }
    .ws-verdict span { font-size: .8rem; font-weight: 500; color: var(--pm-muted); }
    .ws-v-ok { color: var(--cg-ok); } .ws-v-bad { color: var(--cg-bad); }
    .ws-judging, .ws-empty { display: flex; align-items: center; gap: .6rem; height: 100%; min-height: 80px; justify-content: center; font-size: .86rem; color: var(--pm-muted); }

    .ws-scrim { position: fixed; inset: 0; z-index: 1040; background: rgba(15, 23, 42, .35); animation: cgFade .15s ease; }
    .ws-drawer { position: fixed; top: 0; bottom: 0; left: 0; z-index: 1045; width: min(420px, 92vw); display: flex; flex-direction: column; gap: .75rem;
      padding: 1rem; overflow: auto; background: var(--cg-panel); border-right: 1px solid var(--cg-line); box-shadow: 0 10px 40px rgba(0,0,0,.25);
      animation: cgSlide .18s ease; }
    .ws-drawer-head { display: flex; justify-content: space-between; align-items: center; color: var(--pm-heading); }
    @keyframes cgFade { from { opacity: 0; } }
    @keyframes cgSlide { from { transform: translateX(-24px); opacity: 0; } }

    @media (max-width: 991.98px) {
      .ws { height: auto !important; min-height: 0; }
      .ws-body { display: flex; flex-direction: column; gap: .5rem; }
      .ws-right { display: flex; flex-direction: column; gap: .5rem; }
      .ws-split { display: none; }
      .ws-desc .ws-scroll { max-height: 26rem; }
      .ws-editor { height: 380px; }
      .ws-console { min-height: 220px; }
      .ws-save { display: none; }
    }
  `],
})
export class Workspace implements AfterViewInit {
  problem = input.required<Problem>();
  topic = input.required<TopicCard>();
  languages = input<Language[]>([]);
  /** The server marked the problem solved: progress should be reloaded. */
  solvedNow = output<string>();
  open = output<string>();
  back = output<void>();

  protected theme = inject(ThemeService);
  private api = inject(CodeGroundService);
  private confirm = inject(ConfirmService);
  private toast = inject(ToastService);
  private warnedAt = 0;
  private destroyRef = inject(DestroyRef);
  private language = inject(CodeLanguage);
  lang = this.language.current;
  languageList = computed<Language[]>(() => this.languages().length ? this.languages() : [{ key: 'javascript', label: 'JavaScript' }]);
  private root = viewChild.required<ElementRef<HTMLElement>>('root');
  private list = viewChild<ProblemTable>('list');

  private key = computed(() => this.problem().key);
  /** The problem and language: a new one starts with a clean console. */
  private slot = computed(() => this.problem().key + '/' + this.lang());
  protected starter = computed(() => this.problem().starters[this.lang()] ?? '');
  /** Copies the server's saved code into this browser, except where this browser still has changes the server has not got. */
  private synced = computed(() => {
    const p = this.problem();
    for (const [language, code] of Object.entries(p.drafts ?? {}) as [LanguageKey, string][]) {
      const k = draftKey(p.key, language);
      if (!read(unsaved(k))) store(k, code);
    }
    return p;
  });
  /** The student's code for this problem and language, else the starter code. */
  code = linkedSignal(() => {
    const saved = read(draftKey(this.synced().key, this.lang()));
    return saved === null ? this.starter() : withCurrentHarness(saved, this.starter(), this.lang());
  });
  saveState = linkedSignal<string, string>({ source: this.slot, computation: () => '' });
  private pending: { key: string; language: LanguageKey; code: string } | null = null;
  private saveTimer?: ReturnType<typeof setTimeout>;
  /** What Run is given: the example until the student changes it. */
  input = linkedSignal<string, string>({ source: this.key, computation: () => this.problem().example.input });
  isExample = computed(() => this.input().trimEnd() === this.problem().example.input.trimEnd());
  hint = linkedSignal<string, boolean>({ source: this.key, computation: () => false });
  result = linkedSignal<string, RunResult | null>({ source: this.slot, computation: () => null });
  tab = linkedSignal<string, 'case' | 'result' | 'history'>({ source: this.key, computation: () => 'case' });
  /** Past submissions of this problem; null until asked for. */
  history = linkedSignal<string, Submission[] | null>({ source: this.key, computation: () => null });
  historyFailed = linkedSignal<string, boolean>({ source: this.key, computation: () => false });
  openSub = linkedSignal<string, number | null>({ source: this.key, computation: () => null });
  protected verdictName = {
    ACCEPTED: 'Accepted', WRONG_ANSWER: 'Wrong Answer', TIME_LIMIT: 'Time Limit Exceeded', RUNTIME_ERROR: 'Runtime Error', COMPILE_ERROR: 'Compile Error',
  };
  /** The last Run (Submit's result is in result); only one of the two is set. */
  sample = linkedSignal<string, Sample | null>({ source: this.slot, computation: () => null });
  caseNo = linkedSignal<RunResult | null, number>({ source: this.result, computation: r => r?.cases.find(c => !c.passed)?.index ?? 1 });
  solved = linkedSignal(() => this.problem().solved);
  drawer = signal(false);
  private busy = signal<{ key: string; kind: 'run' | 'submit' } | null>(null);
  running = computed(() => (this.busy()?.key === this.problem().key ? this.busy()!.kind : null));
  passed = computed(() => [...(this.result()?.cases ?? []), ...(this.result()?.hidden ?? [])].filter(c => c.passed).length);
  shown = computed(() => this.result()?.cases.find(c => c.index === this.caseNo()) ?? null);

  private stored = (() => { try { return JSON.parse(read(LAYOUT_KEY) ?? '{}') as { left?: number; console?: number }; } catch { return {}; } })();
  left = signal(this.stored.left ?? 42);
  consoleH = signal(this.stored.console ?? 250);
  height = signal(640);

  private index = computed(() => this.topic().problems.findIndex(x => x.key === this.problem().key));
  number = computed(() => this.index() + 1);
  prev = computed(() => this.topic().problems[this.index() - 1]?.key ?? null);
  next = computed(() => this.topic().problems[this.index() + 1]?.key ?? null);
  label = computed(() => this.languageList().find(l => l.key === this.lang())?.label ?? this.lang());
  level = computed(() => ({ EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' })[this.problem().difficulty]);
  verdict = computed<Verdict | null>(() => {
    const s = this.sample();
    if (s) {
      if (s.compileError) return { label: 'Compile Error', tone: 'bad' };
      if (s.timedOut) return { label: 'Time Limit Exceeded', tone: 'bad' };
      if (s.error) return { label: 'Runtime Error', tone: 'bad' };
      if (s.passed === null) return { label: 'Finished', tone: 'ok' };
      return s.passed ? { label: 'Example passed', tone: 'ok' } : { label: 'Wrong Answer', tone: 'bad' };
    }
    const r = this.result();
    if (!r) return null;
    if (r.compileError) return { label: 'Compile Error', tone: 'bad' };
    if (r.allPassed) return { label: 'Accepted', tone: 'ok' };
    if (r.cases.some(c => c.timedOut) || r.hidden.some(h => h.timedOut)) return { label: 'Time Limit Exceeded', tone: 'bad' };
    if (r.cases.some(c => c.error)) return { label: 'Runtime Error', tone: 'bad' };
    return { label: 'Wrong Answer', tone: 'bad' };
  });

  ngAfterViewInit(): void {
    this.destroyRef.onDestroy(() => this.flush());
    const fit = () => {
      const top = this.root().nativeElement.getBoundingClientRect().top + window.scrollY;
      this.height.set(Math.max(560, window.innerHeight - top - 16));
    };
    fit();
    window.addEventListener('resize', fit);
    this.destroyRef.onDestroy(() => window.removeEventListener('resize', fit));
  }

  edit(code: string): void {
    this.code.set(code);
    const key = this.problem().key, language = this.lang(), k = draftKey(key, language);
    store(k, code);
    store(unsaved(k), '1');
    // a change in another problem or language must not be dropped by this one's timer
    if (this.pending && (this.pending.key !== key || this.pending.language !== language)) this.flush();
    this.pending = { key, language, code };
    this.saveState.set('Saving…');
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.flush(), SAVE_AFTER_MS);
  }

  /** Sends the last change to the server. */
  private flush(): void {
    clearTimeout(this.saveTimer);
    const change = this.pending;
    if (!change) return;
    this.pending = null;
    const k = draftKey(change.key, change.language);
    const here = () => this.problem().key === change.key && this.lang() === change.language;
    this.api.saveDraft(change.key, change.language, change.code).subscribe({
      next: () => {
        if (read(k) === change.code) store(unsaved(k), null);
        if (here()) this.saveState.set('Saved');
      },
      error: () => { if (here()) this.saveState.set('Saved on this computer only'); },
    });
  }

  choose(language: LanguageKey): void {
    if (this.busy()) return;
    this.flush();
    this.language.set(language);
  }

  run(): void {
    const key = this.problem().key, slot = this.slot();
    if (this.busy() || !this.code().trim()) return;
    this.start(key, 'run');
    this.api.run(key, this.lang(), this.code(), this.isExample() ? null : this.input()).subscribe({
      next: s => {
        this.busy.set(null);
        if (this.slot() === slot) this.sample.set(s); // not if the student has moved on meanwhile
      },
      error: () => this.busy.set(null), // the error interceptor already shows why (too fast, judge busy)
    });
  }

  submit(): void {
    const key = this.problem().key, slot = this.slot();
    if (this.busy() || !this.code().trim()) return;
    this.start(key, 'submit');
    this.api.submit(key, this.lang(), this.code()).subscribe({
      next: r => {
        this.busy.set(null);
        if (r.solved) this.solvedNow.emit(key); // progress is reloaded even if the student has moved on
        if (this.history() && this.problem().key === key) this.loadHistory();
        if (this.slot() !== slot) return;
        this.result.set(r);
        if (r.solved) this.solved.set(true);
      },
      error: () => this.busy.set(null),
    });
  }

  private start(key: string, kind: 'run' | 'submit'): void {
    this.flush();
    this.busy.set({ key, kind });
    this.sample.set(null);
    this.result.set(null);
    this.tab.set('result');
  }

  async reset(): Promise<void> {
    const key = this.problem().key;
    const lang = this.lang();
    const answer = await this.confirm.ask({ title: 'Reset to starter code?', message: 'Your ' + this.label() + ' code for this problem will be replaced.', tone: 'danger', confirmText: 'Reset' });
    if (!answer.ok || this.problem().key !== key || this.lang() !== lang) return;
    clearTimeout(this.saveTimer);
    this.pending = null;
    const k = draftKey(key, lang);
    store(k, null);
    store(unsaved(k), null);
    delete this.problem().drafts?.[lang];
    this.api.saveDraft(key, lang, '').subscribe({ error: () => this.saveState.set('Could not remove the saved copy') });
    this.code.set(this.starter());
    this.sample.set(null);
    this.result.set(null);
    this.tab.set('case');
  }

  showHistory(): void {
    this.tab.set('history');
    // on a phone the console is below the editor
    this.root().nativeElement.querySelector('.ws-console')?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    this.loadHistory();
  }

  private loadHistory(): void {
    const key = this.problem().key;
    this.historyFailed.set(false);
    this.api.submissions(key).subscribe({
      next: list => { if (this.problem().key === key) this.history.set(list); },
      error: () => { if (this.problem().key === key) this.historyFailed.set(true); },
    });
  }

  languageName(language: LanguageKey): string {
    return this.languageList().find(l => l.key === language)?.label ?? language;
  }

  /** The student's own part of a submitted program (without the input / print code). */
  ownCode(code: string): string {
    const marker = code.indexOf('no need to change below');
    return (marker < 0 ? code : code.slice(0, code.lastIndexOf('\n', marker))).trimEnd();
  }

  async useSubmission(s: Submission): Promise<void> {
    const key = this.problem().key;
    const answer = await this.confirm.ask({ title: 'Use this submission?', message: 'Your current ' + this.languageName(s.language) + ' code for this problem will be replaced.', confirmText: 'Replace' });
    if (!answer.ok || this.problem().key !== key || this.busy()) return;
    this.choose(s.language);
    this.edit(withCurrentHarness(s.code, this.starter(), s.language));
  }

  /** Said at most every few seconds, so holding Ctrl+V does not stack messages. */
  noClipboard(): void {
    if (Date.now() - this.warnedAt < 4000) return;
    this.warnedAt = Date.now();
    this.toast.error('Copy and paste are turned off in the code editor. Type your solution.');
  }

  openDrawer(): void {
    this.drawer.set(true);
    setTimeout(() => this.list()?.focus());
  }

  // ---------------------------------------------------------------- resizable panes (pointer and keyboard)
  drag(e: PointerEvent, which: 'left' | 'console'): void {
    const handle = e.currentTarget as HTMLElement;
    const body = handle.parentElement!.closest('.ws-body') as HTMLElement;
    handle.setPointerCapture(e.pointerId);
    handle.classList.add('ws-dragging');
    const move = (ev: PointerEvent) => {
      if (which === 'left') {
        const r = body.getBoundingClientRect();
        this.left.set(Math.round(Math.min(65, Math.max(25, (ev.clientX - r.left) / r.width * 100))));
      } else {
        const r = handle.parentElement!.getBoundingClientRect();
        this.consoleH.set(Math.round(Math.min(560, Math.max(120, r.bottom - ev.clientY))));
      }
    };
    const up = () => {
      handle.classList.remove('ws-dragging');
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', up);
      this.saveLayout();
    };
    handle.addEventListener('pointermove', move);
    handle.addEventListener('pointerup', up);
  }

  nudge(e: KeyboardEvent, which: 'left' | 'console'): void {
    const step = e.shiftKey ? 10 : 2;
    if (which === 'left' && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
      e.preventDefault();
      this.left.update(v => Math.min(65, Math.max(25, v + (e.key === 'ArrowRight' ? step : -step))));
    } else if (which === 'console' && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
      e.preventDefault();
      this.consoleH.update(v => Math.min(560, Math.max(120, v + (e.key === 'ArrowUp' ? step * 10 : -step * 10))));
    } else return;
    this.saveLayout();
  }

  private saveLayout(): void {
    store(LAYOUT_KEY, JSON.stringify({ left: this.left(), console: this.consoleH() }));
  }
}
