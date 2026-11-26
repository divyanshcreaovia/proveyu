import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output } from '@angular/core';
import { Guide, LanguageKey } from './code-ground.models';
import { CodeLanguage } from './code-ground.service';

const TABS: { key: LanguageKey; label: string; file: string }[] = [
  { key: 'javascript', label: 'JavaScript', file: 'example.js' },
  { key: 'python', label: 'Python', file: 'example.py' },
  { key: 'java', label: 'Java', file: 'Main.java' },
  { key: 'cpp', label: 'C++', file: 'main.cpp' },
];
/** An icon and a colour per kind of slide. */
const TAGS: Record<string, { icon: string; tone: string }> = {
  'Why it matters': { icon: 'bi-lightning-charge', tone: 'why' },
  'Real-world use': { icon: 'bi-globe2', tone: 'real' },
  'Core idea': { icon: 'bi-lightbulb', tone: 'idea' },
  'Syntax': { icon: 'bi-code-slash', tone: 'syntax' },
  'Pattern': { icon: 'bi-diagram-3', tone: 'pattern' },
  'Complexity': { icon: 'bi-speedometer2', tone: 'cost' },
  'Common mistakes': { icon: 'bi-exclamation-triangle', tone: 'warn' },
  'Going further': { icon: 'bi-rocket-takeoff', tone: 'adv' },
};

/**
 * A topic's learning guide as a short lesson: contents on the left, one slide at a time on the right (a carousel; arrow keys work).
 * "Mark as read" on the last slide counts towards the topic's progress.
 */
@Component({
  selector: 'pm-lesson-reader',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    @let g = guide();
    <div class="lr">
      <nav class="cg-panel lr-toc" aria-label="Lessons">
        <div class="cg-kicker">{{ g.title }}</div>
        <h2 class="lr-toc-h">Learning guide</h2>
        <ol>
          @for (s of g.slides; track $index) {
            <li>
              <button type="button" [class.lr-t-on]="$index === index()" [attr.aria-current]="$index === index() ? 'step' : null" (click)="go($index)">
                <span class="lr-t-n" [class.lr-t-seen]="g.read || $index < index()">
                  @if (g.read || $index < index()) { <i class="bi bi-check" aria-hidden="true"></i> } @else { {{ $index + 1 }} }</span>
                <span class="min-w-0"><span class="lr-t-tag">{{ s.tag }}</span>{{ s.title }}</span>
              </button>
            </li>
          }
        </ol>
        <div class="lr-state" [class.lr-state-read]="g.read">
          @if (g.read) { <i class="bi bi-check-circle-fill" aria-hidden="true"></i> Read · counts towards progress }
          @else { <i class="bi bi-book" aria-hidden="true"></i> Read to the end to count it }
        </div>
      </nav>

      <section class="cg-panel lr-main" aria-roledescription="carousel" [attr.aria-label]="g.title + ' learning guide'" tabindex="0" (keydown)="onKey($event)">
        <div class="lr-head">
          <span class="lr-step">Lesson {{ index() + 1 }} of {{ g.slides.length }}</span>
          <div class="lr-bar" aria-hidden="true">@for (s of g.slides; track $index) { <span [class.lr-bar-on]="$index <= index()"></span> }</div>
        </div>
        <div class="lr-body" aria-live="polite" aria-roledescription="slide" [attr.aria-label]="(index() + 1) + ' of ' + g.slides.length">
          @let tag = tagOf(slide().tag);
          <span class="lr-tag" [attr.data-tone]="tag.tone"><i class="bi" [class]="tag.icon" aria-hidden="true"></i> {{ slide().tag }}</span>
          <h3>{{ slide().title }}</h3>
          @if (slide().points.length) { <ul>@for (p of slide().points; track $index) { <li>{{ p }}</li> }</ul> }
          @if (slide().code) {
            <figure class="lr-code">
              <figcaption>
                <div class="lr-langs" role="tablist" aria-label="Code language">
                  @for (t of tabs; track t.key) {
                    <button type="button" role="tab" [attr.aria-selected]="t.key === lang()" [class.lr-lang-on]="t.key === lang()" (click)="language.set(t.key)">{{ t.label }}</button>
                  }
                </div>
                <em>{{ file() }}</em>
              </figcaption>
              <pre class="cg-code" role="tabpanel" [attr.aria-label]="label() + ' example'"><code>@for (l of lines(); track $index) {<span class="lr-ln">{{ $index + 1 }}</span>{{ l }}
}</code></pre>
            </figure>
          }
        </div>
        <div class="lr-foot">
          <button type="button" class="cg-btn" [disabled]="index() === 0" (click)="go(index() - 1)"><i class="bi bi-arrow-left" aria-hidden="true"></i> Previous</button>
          <span class="lr-keys">← → to move</span>
          @if (!last()) {
            <button type="button" class="cg-btn cg-btn-primary" (click)="go(index() + 1)">Next <i class="bi bi-arrow-right" aria-hidden="true"></i></button>
          } @else if (!g.read) {
            <button type="button" class="cg-btn cg-btn-run" [disabled]="saving()" (click)="completed.emit()"><i class="bi bi-check-lg" aria-hidden="true"></i> Mark as read</button>
          } @else {
            <button type="button" class="cg-btn cg-btn-primary" (click)="practise.emit()">Start practising <i class="bi bi-arrow-right" aria-hidden="true"></i></button>
          }
        </div>
      </section>
    </div>`,
  styles: [`
    .lr { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 1rem; align-items: start; }
    .lr-toc { padding: 1.1rem; position: sticky; top: 1rem; }
    .lr-toc-h { font-size: 1.05rem; font-weight: 700; color: var(--pm-heading); margin: .15rem 0 .85rem; }
    .lr-toc ol { list-style: none; margin: 0; padding: 0; display: grid; gap: .15rem; }
    .lr-toc button { all: unset; box-sizing: border-box; cursor: pointer; display: flex; align-items: center; gap: .6rem; width: 100%; padding: .45rem .5rem;
      border-radius: 8px; font-size: .84rem; color: var(--pm-muted); }
    .lr-toc button:hover { background: var(--cg-sub); color: var(--pm-heading); }
    .lr-toc button:focus-visible { outline: 2px solid var(--pm-link); outline-offset: -2px; }
    .lr-toc .lr-t-on { background: var(--cg-sub); color: var(--pm-heading); font-weight: 600; }
    .lr-t-n { flex: 0 0 22px; height: 22px; border-radius: 50%; display: grid; place-items: center; font-size: .7rem; font-weight: 700;
      border: 1px solid var(--cg-line); color: var(--pm-muted); }
    .lr-t-seen { background: var(--cg-ok); border-color: var(--cg-ok); color: #fff; }
    .lr-state { margin-top: 1rem; padding-top: .85rem; border-top: 1px solid var(--cg-line); font-size: .78rem; color: var(--pm-muted); }
    .lr-state-read { color: var(--cg-ok); font-weight: 600; }
    .lr-main { padding: 0; overflow: hidden; }
    .lr-main:focus-visible { outline: 2px solid var(--pm-link); outline-offset: 2px; }
    .lr-head { display: flex; align-items: center; gap: 1rem; padding: .9rem 1.5rem; border-bottom: 1px solid var(--cg-line); }
    .lr-step { font-size: .78rem; font-weight: 600; color: var(--pm-muted); white-space: nowrap; }
    .lr-bar { flex: 1; display: flex; gap: 4px; }
    .lr-bar span { flex: 1; height: 4px; border-radius: 2px; background: var(--cg-sub); transition: background-color .25s ease; }
    .lr-bar .lr-bar-on { background: var(--pm-link); }
    .lr-body { padding: 1.75rem 1.5rem; min-height: 340px; }
    .lr-body h3 { font-size: 1.4rem; font-weight: 700; letter-spacing: -.01em; color: var(--pm-heading); margin: 0 0 1rem; }
    .lr-body ul { margin: 0; padding-left: 1.15rem; display: grid; gap: .6rem; color: var(--pm-label); font-size: .95rem; line-height: 1.6; max-width: 70ch; }
    .lr-code { margin: 1.5rem 0 0; border-radius: 10px; overflow: hidden; background: #1e1e2e; border: 1px solid #2a2a3c; max-width: 760px; }
    .lr-code figcaption { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: 0 .6rem 0 .3rem; background: #181825;
      border-bottom: 1px solid #2a2a3c; }
    .lr-code figcaption em { font-style: normal; font-size: .72rem; color: #7f849c; font-family: var(--cg-mono); white-space: nowrap; }
    .lr-langs { display: flex; overflow-x: auto; scrollbar-width: none; }
    .lr-langs button { all: unset; cursor: pointer; padding: .55rem .75rem; font-size: .76rem; font-weight: 600; color: #a6adc8; border-bottom: 2px solid transparent;
      white-space: nowrap; }
    .lr-langs button:hover { color: #cdd6f4; }
    .lr-langs .lr-lang-on { color: #fff; border-bottom-color: #89b4fa; }
    .lr-langs button:focus-visible { outline: 2px solid #89b4fa; outline-offset: -2px; }
    .lr-tag { display: inline-flex; align-items: center; gap: .35rem; padding: .2rem .6rem; border-radius: 999px; font-size: .72rem; font-weight: 700;
      letter-spacing: .02em; text-transform: uppercase; margin-bottom: .7rem; color: var(--tag); background: color-mix(in srgb, var(--tag) 13%, transparent); }
    .lr-tag[data-tone] { --tag: var(--pm-link); }
    .lr-tag[data-tone='why'] { --tag: #d97706; }
    .lr-tag[data-tone='real'] { --tag: #0891b2; }
    .lr-tag[data-tone='idea'] { --tag: #7c3aed; }
    .lr-tag[data-tone='syntax'], .lr-tag[data-tone='pattern'] { --tag: #2563eb; }
    .lr-tag[data-tone='cost'] { --tag: #0d9488; }
    .lr-tag[data-tone='warn'] { --tag: #dc2626; }
    .lr-tag[data-tone='adv'] { --tag: #16a34a; }
    :host-context([data-bs-theme='dark']) .lr-tag { filter: brightness(1.35); }
    .lr-t-tag { display: block; font-size: .64rem; font-weight: 700; letter-spacing: .03em; text-transform: uppercase; opacity: .75; }
    .lr-code pre { margin: 0; padding: .9rem 1rem; color: #cdd6f4; overflow-x: auto; }
    .lr-ln { display: inline-block; width: 1.8rem; color: #6c7086; user-select: none; }
    .lr-foot { display: flex; justify-content: space-between; align-items: center; gap: .75rem; padding: .85rem 1.5rem; border-top: 1px solid var(--cg-line);
      background: var(--cg-sub); }
    .lr-keys { font-size: .72rem; color: var(--pm-muted); }
    @media (max-width: 991.98px) { .lr { grid-template-columns: minmax(0, 1fr); } .lr-toc { position: static; } .lr-keys { display: none; } }
  `],
})
export class LessonReader {
  guide = input.required<Guide>();
  saving = input(false);
  completed = output<void>();
  practise = output<void>();

  private topicKey = computed(() => this.guide().topicKey);
  /** Back to the first slide when another topic's guide is shown (not when the same guide is marked read). */
  index = linkedSignal<string, number>({ source: this.topicKey, computation: () => 0 });
  slide = computed(() => this.guide().slides[this.index()]);
  last = computed(() => this.index() === this.guide().slides.length - 1);
  protected language = inject(CodeLanguage);
  protected lang = this.language.current;
  protected tabs = TABS;
  file = computed(() => TABS.find(t => t.key === this.lang())!.file);
  label = computed(() => TABS.find(t => t.key === this.lang())!.label);
  lines = computed(() => (this.slide().code?.[this.lang()] ?? '// No example in this language yet').split('\n'));

  tagOf(tag: string): { icon: string; tone: string } {
    return TAGS[tag] ?? { icon: 'bi-bookmark', tone: 'idea' };
  }

  go(i: number): void {
    this.index.set(Math.max(0, Math.min(this.guide().slides.length - 1, i)));
  }

  onKey(e: KeyboardEvent): void {
    if ((e.target as HTMLElement).closest('button')) return;
    if (e.key === 'ArrowRight') { e.preventDefault(); this.go(this.index() + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); this.go(this.index() - 1); }
  }
}
