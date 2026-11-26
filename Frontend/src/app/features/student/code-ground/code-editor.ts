import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, viewChild } from '@angular/core';
import { indentWithTab } from '@codemirror/commands';
import { cpp } from '@codemirror/lang-cpp';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { indentUnit } from '@codemirror/language';
import { Annotation, Compartment, EditorState, Extension, Prec, Range, StateEffect, StateField, Text, Transaction } from '@codemirror/state';
import { oneDark } from '@codemirror/theme-one-dark';
import { Decoration, DecorationSet, WidgetType, keymap } from '@codemirror/view';
import { EditorView, basicSetup } from 'codemirror';

const MODES: Record<string, () => Extension> = { javascript: () => javascript(), python: () => python(), java: () => java(), cpp: () => cpp() };
// the starters indent with 2 spaces in JavaScript and 4 in the others
const mode = (language: string): Extension => [(MODES[language] ?? MODES['javascript'])(), indentUnit.of(language === 'javascript' ? '  ' : '    ')];

/** The parts of a starter the student must not change: `head` at the top (Java / C++ only) and `tail`, the input / print code below solve(). */
export function lockedParts(starter: string, language: string): { head: string; tail: string } {
  const marker = starter.indexOf('no need to change below');
  if (marker < 0) return { head: '', tail: '' };
  // from the newline before the marker comment, so nothing can be typed into that line
  const tail = starter.slice(starter.lastIndexOf('\n', marker));
  const top = language === 'java' ? /^[\s\S]*?public class Main \{\n/
    : language === 'cpp' ? /^[\s\S]*?using namespace std;\n(\nconst int NONE[^\n]*\n)?/ : null;
  return { head: top?.exec(starter)?.[0] ?? '', tail };
}

/** Marks a change made by the app itself (new problem, other language, Reset), which may replace locked text. */
const fromApp = Annotation.define<boolean>();
const lockedLine = Decoration.line({ class: 'cm-locked', attributes: { title: 'Locked: this code reads the input and prints the answer' } });

/** The input / print code under solve() is folded away until the student asks to see it. */
const showHarness = StateEffect.define<boolean>();
const harnessShown = StateField.define<boolean>({
  create: () => false,
  update: (shown, tr) => tr.effects.reduce((v, e) => (e.is(showHarness) ? e.value : v), shown),
});

class HarnessToggle extends WidgetType {
  constructor(private shown: boolean) { super(); }
  override eq(other: HarnessToggle): boolean { return other.shown === this.shown; }
  override ignoreEvent(): boolean { return true; }
  toDOM(view: EditorView): HTMLElement {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'cm-harness';
    button.textContent = this.shown ? 'Hide input / print code' : 'Show input / print code (locked)';
    button.setAttribute('aria-expanded', String(this.shown));
    button.onclick = () => view.dispatch({ effects: showHarness.of(!this.shown) });
    return button;
  }
}

/**
 * Code editor (CodeMirror 6) for JavaScript, Python, Java and C++. Tab indents; Escape then Tab leaves the editor, so keyboard users are never trapped.
 * Ctrl / Cmd + Enter asks to run.
 */
@Component({
  selector: 'pm-code-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="ce-host"></div>`,
  styles: [`
    :host { display: block; height: 100%; min-height: 0; }
    .ce-host { height: 100%; }
    .ce-host ::ng-deep .cm-editor { height: 100%; font-size: 13.5px; background: transparent; }
    .ce-host ::ng-deep .cm-gutters { background: transparent; border-right: 0; color: var(--pm-muted); }
    .ce-host ::ng-deep .cm-activeLineGutter { background: transparent; color: var(--pm-heading); }
    .ce-host ::ng-deep .cm-editor:not(.cm-focused) .cm-activeLine { background: transparent; }
    .ce-host ::ng-deep .cm-scroller { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; line-height: 1.55; }
    .ce-host ::ng-deep .cm-editor.cm-focused { outline: none; }
    .ce-host ::ng-deep .cm-harness { display: block; margin: .35rem 0 .35rem .5rem; padding: .2rem .7rem; border-radius: 7px; cursor: pointer;
      font: 600 .74rem system-ui, sans-serif; color: var(--pm-muted); background: color-mix(in srgb, var(--pm-muted) 10%, transparent);
      border: 1px dashed color-mix(in srgb, var(--pm-muted) 45%, transparent); }
    .ce-host ::ng-deep .cm-harness:hover { color: var(--pm-heading); }
    .ce-host ::ng-deep .cm-line.cm-locked { background: color-mix(in srgb, var(--pm-muted) 10%, transparent); opacity: .65; cursor: default; }
  `],
})
export class CodeEditor implements AfterViewInit, OnDestroy {
  value = input.required<string>();
  dark = input(false);
  language = input('javascript');
  label = input('Code editor');
  /** The starter code of this problem and language; its input / print code is locked. */
  starter = input('');
  valueChange = output<string>();
  run = output<void>();
  submit = output<void>();
  /** The student tried to copy, cut, paste or drop. */
  clipboardBlocked = output<void>();

  private host = viewChild.required<ElementRef<HTMLElement>>('host');
  private view?: EditorView;
  private theme = new Compartment();
  private lang = new Compartment();

  constructor() {
    // a new problem or Reset replaces the text; what the student types is already in the editor
    effect(() => {
      const next = this.value();
      const view = this.view;
      if (view && next !== view.state.doc.toString()) {
        view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: next }, annotations: [fromApp.of(true), Transaction.addToHistory.of(false)] });
      }
    });
    effect(() => {
      const language = this.language();
      this.view?.dispatch({ effects: this.lang.reconfigure(mode(language)) });
    });
    effect(() => {
      const dark = this.dark();
      this.view?.dispatch({ effects: this.theme.reconfigure(dark ? oneDark : []) });
    });
  }

  ngAfterViewInit(): void {
    this.view = new EditorView({
      parent: this.host().nativeElement,
      state: EditorState.create({
        doc: this.value(),
        extensions: [
          // code is typed here: nothing is copied out, cut, pasted or dragged in
          Prec.highest(EditorView.domEventHandlers({
            copy: e => this.block(e), cut: e => this.block(e), paste: e => this.block(e), drop: e => this.block(e), dragstart: e => this.block(e),
          })),
          Prec.highest(keymap.of([
            { key: 'Mod-Enter', run: () => { this.run.emit(); return true; } },
            { key: 'Mod-Shift-Enter', run: () => { this.submit.emit(); return true; } },
          ])),
          basicSetup,
          keymap.of([indentWithTab]),
          EditorState.tabSize.of(4),
          this.lang.of(mode(this.language())),
          this.theme.of(this.dark() ? oneDark : []),
          EditorState.changeFilter.of(tr => tr.annotation(fromApp) === true || this.allowed(tr)),
          harnessShown,
          EditorView.decorations.compute(['doc', harnessShown], state => this.decorate(state.doc, state.field(harnessShown))),
          EditorView.contentAttributes.of({ 'aria-label': this.label() }),
          EditorView.updateListener.of(u => { if (u.docChanged) this.valueChange.emit(u.state.doc.toString()); }),
        ],
      }),
    });
  }

  private block(e: Event): boolean {
    e.preventDefault();
    this.clipboardBlocked.emit();
    return true; // CodeMirror's own clipboard handling is skipped
  }

  /** Lengths of the locked top and bottom in this text; 0 when the text does not hold them intact (an old, already edited draft). */
  private locked(doc: Text): { head: number; tail: number } {
    const { head, tail } = lockedParts(this.starter(), this.language());
    const text = doc.toString();
    return { head: head && text.startsWith(head) ? head.length : 0, tail: tail && text.endsWith(tail) ? tail.length : 0 };
  }

  private allowed(tr: Transaction): boolean {
    if (!tr.docChanged) return true;
    const { head, tail } = this.locked(tr.startState.doc);
    const end = tr.startState.doc.length - tail;
    let ok = true;
    tr.changes.iterChangedRanges((from, to) => { if (from < head || to > end) ok = false; });
    return ok;
  }

  private decorate(doc: Text, shown: boolean): DecorationSet {
    const { head, tail } = this.locked(doc);
    const marks: Range<Decoration>[] = [];
    // the tail starts with the newline before its first line
    const from = tail ? doc.length - tail + 1 : doc.length + 1;
    for (let n = 1; n <= doc.lines; n++) {
      const line = doc.line(n);
      if (line.from < head || (shown && line.from >= from)) marks.push(lockedLine.range(line.from));
    }
    if (tail) {
      marks.push(shown
        ? Decoration.widget({ widget: new HarnessToggle(true), block: true, side: -1 }).range(from)
        : Decoration.replace({ widget: new HarnessToggle(false), block: true }).range(from, doc.length));
    }
    return Decoration.set(marks, true);
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }
}
