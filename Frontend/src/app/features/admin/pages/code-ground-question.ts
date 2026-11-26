import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppError } from '../../../core/api';
import { ToastService } from '../../../core/services/toast.service';
import { Modal } from '../../../shared/ui/modal';
import { CodeGroundAdminService, Language, ProblemSpec, Published, TopicSummary } from '../services/code-ground-admin.service';

const blank = (topicKey: string): ProblemSpec => ({
  key: '', topicKey, title: '', difficulty: 'EASY', statement: '', args: [{ name: 'nums', type: 'ints' }], returnType: 'int', hint: '', note: '',
  tests: [{ input: '', output: '' }, { input: '', output: '' }], hiddenTests: [{ input: '', output: '' }, { input: '', output: '' }],
  solution: { language: 'python', code: '' },
});
const TYPE_HELP: Record<string, string> = {
  int: 'one number on a line', str: 'one line of text', ints: 'numbers separated by spaces', words: 'words separated by spaces',
  rows: 'first the number of rows, then one row per line', tree: 'level order, "null" for a missing child',
};

/** The form for one question: save as a draft, or save and publish (the server checks the reference solution on every test). */
@Component({
  selector: 'pm-code-ground-question',
  imports: [FormsModule, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="true" [title]="editKey() ? 'Edit question' : 'New question'" variant="drawer" size="xl" (closed)="closed.emit()">
      @if (spec(); as s) {
        <div class="row g-2 small">
          <div class="col-sm-6"><label class="form-label mb-1" for="q-topic">Study plan topic</label>
            <select id="q-topic" class="form-select form-select-sm" [(ngModel)]="s.topicKey">
              @for (t of topics(); track t.key) { <option [value]="t.key">{{ t.title }}</option> }</select></div>
          <div class="col-sm-6"><label class="form-label mb-1" for="q-key">Key (unique, e.g. max-two-sum)</label>
            <input id="q-key" class="form-control form-control-sm" [(ngModel)]="s.key" [readonly]="!!editKey()" maxlength="60" /></div>
          <div class="col-sm-8"><label class="form-label mb-1" for="q-title">Title</label>
            <input id="q-title" class="form-control form-control-sm" [(ngModel)]="s.title" maxlength="120" /></div>
          <div class="col-sm-4"><label class="form-label mb-1" for="q-level">Difficulty</label>
            <select id="q-level" class="form-select form-select-sm" [(ngModel)]="s.difficulty">
              <option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></div>
          <div class="col-12"><label class="form-label mb-1" for="q-statement">Question</label>
            <textarea id="q-statement" class="form-control form-control-sm" rows="3" [(ngModel)]="s.statement"></textarea></div>

          <fieldset class="col-12 mt-2"><legend class="fs-6 fw-semibold">solve() parameters and answer</legend>
            @for (a of s.args; track $index) {
              <div class="d-flex gap-2 mb-1 align-items-center">
                <input class="form-control form-control-sm" style="max-width: 160px" [(ngModel)]="a.name" [attr.aria-label]="'Parameter ' + ($index + 1) + ' name'" placeholder="name" />
                <select class="form-select form-select-sm" style="max-width: 120px" [(ngModel)]="a.type" [attr.aria-label]="'Parameter ' + ($index + 1) + ' type'">
                  @for (t of argTypes; track t) { <option [value]="t">{{ t }}</option> }</select>
                <span class="text-body-secondary flex-grow-1">{{ help[a.type] }}</span>
                <button type="button" class="btn btn-sm btn-outline-danger" [disabled]="s.args.length === 1" (click)="s.args.splice($index, 1)" aria-label="Remove parameter"><i class="bi bi-x"></i></button>
              </div>
            }
            <div class="d-flex flex-wrap gap-2 align-items-center">
              <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="s.args.length >= 5" (click)="s.args.push({ name: '', type: 'int' })">Add parameter</button>
              <label class="ms-auto" for="q-return">Returns</label>
              <select id="q-return" class="form-select form-select-sm" style="max-width: 130px" [(ngModel)]="s.returnType">
                @for (t of returnTypes; track t) { <option [value]="t">{{ t }}</option> }</select>
            </div>
          </fieldset>

          <div class="col-sm-6"><label class="form-label mb-1" for="q-hint">Hint</label>
            <input id="q-hint" class="form-control form-control-sm" [(ngModel)]="s.hint" maxlength="500" /></div>
          <div class="col-sm-6"><label class="form-label mb-1" for="q-note">Note (optional)</label>
            <input id="q-note" class="form-control form-control-sm" [(ngModel)]="s.note" maxlength="500" /></div>

          @for (group of groups(s); track group.label) {
            <fieldset class="col-12 mt-2"><legend class="fs-6 fw-semibold mb-0">{{ group.label }}</legend>
              <p class="text-body-secondary mb-1">{{ group.help }} Each parameter reads its own line, in order.</p>
              @for (c of group.list; track $index) {
                <div class="d-flex gap-2 mb-1">
                  <textarea class="form-control form-control-sm font-monospace" rows="2" [(ngModel)]="c.input" [attr.aria-label]="group.label + ' ' + ($index + 1) + ' input'" placeholder="input"></textarea>
                  <textarea class="form-control form-control-sm font-monospace" rows="2" [(ngModel)]="c.output" [attr.aria-label]="group.label + ' ' + ($index + 1) + ' expected output'" placeholder="expected output"></textarea>
                  <button type="button" class="btn btn-sm btn-outline-danger" [disabled]="group.list.length <= 2" (click)="group.list.splice($index, 1)" aria-label="Remove test"><i class="bi bi-x"></i></button>
                </div>
              }
              <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="group.list.length >= 10" (click)="group.list.push({ input: '', output: '' })">Add test</button>
            </fieldset>
          }

          <fieldset class="col-12 mt-2"><legend class="fs-6 fw-semibold mb-1">Reference solution (never shown to students)</legend>
            <div class="d-flex gap-2 mb-1">
              <select class="form-select form-select-sm" style="max-width: 150px" [(ngModel)]="s.solution!.language" aria-label="Solution language">
                <option value="javascript">JavaScript</option><option value="python">Python</option><option value="java">Java</option><option value="cpp">C++</option></select>
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="outline()">Start with an empty solve()</button>
            </div>
            <textarea class="form-control form-control-sm font-monospace" rows="9" spellcheck="false" [(ngModel)]="s.solution!.code" aria-label="Solution code" placeholder="Write solve() here; helper functions are fine"></textarea>
          </fieldset>
        </div>

        @if (error()) { <div class="alert alert-danger small mt-3 mb-0" role="alert">{{ error() }}</div> }
        @if (result(); as r) {
          <div class="alert small mt-3 mb-0" [class.alert-success]="r.ok" [class.alert-danger]="!r.ok" role="alert">
            @if (r.ok) { Published. Students can solve it now. }
            @else {
              <strong>Not published.</strong> @for (e of r.errors; track $index) { <div>{{ e }}</div> }
              @if (r.compileError) { <pre class="mt-2 mb-0">{{ r.compileError }}</pre> }
              @for (f of r.failures; track f.test) {
                <div class="mt-2"><strong>{{ f.test }}</strong> — input <code>{{ f.input }}</code>, expected <code>{{ f.expected }}</code>,
                  got <code>{{ f.got || 'nothing' }}</code>@if (f.error) { <pre class="mb-0">{{ f.error }}</pre> }</div>
              }
            }
          </div>
        }
      } @else { <p class="small">Loading…</p> }
      <ng-container pmFooter>
        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="closed.emit()">Close</button>
        <button type="button" class="btn btn-sm btn-outline-primary" [disabled]="busy() || !spec()" (click)="save(false)">Save draft</button>
        <button type="button" class="btn btn-sm btn-primary" [disabled]="busy() || !spec()" (click)="save(true)">
          @if (busy()) { <span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Checking… } @else { Save and publish }</button>
      </ng-container>
    </pm-modal>`,
})
export class CodeGroundQuestion implements OnInit {
  topics = input.required<TopicSummary[]>();
  /** The problem being edited; null for a new one. */
  editKey = input<string | null>(null);
  topicKey = input('basics');
  closed = output<void>();
  changed = output<void>();

  private api = inject(CodeGroundAdminService);
  private toast = inject(ToastService);
  spec = signal<ProblemSpec | null>(null);
  busy = signal(false);
  error = signal('');
  result = signal<Published | null>(null);
  help = TYPE_HELP;
  argTypes = Object.keys(TYPE_HELP);
  returnTypes = ['int', 'long', 'double', 'bool', 'string', 'int[]', 'string[]', 'int[][]', 'string[][]'];

  ngOnInit(): void {
    const key = this.editKey();
    if (!key) { this.spec.set(blank(this.topicKey())); return; }
    this.api.problem(key).subscribe({
      next: a => this.spec.set({ ...a.spec, solution: a.spec.solution ?? { language: 'python', code: '' } }),
      error: (e: AppError) => this.error.set(e.message),
    });
  }

  groups(s: ProblemSpec) {
    return [
      { label: 'Visible tests', list: s.tests, help: 'The first one is the example; students see these.' },
      { label: 'Hidden tests', list: s.hiddenTests, help: 'Students only see pass or fail.' },
    ];
  }

  outline(): void {
    const s = this.spec()!;
    this.error.set('');
    this.api.templates(s.args, s.returnType).subscribe({
      next: list => {
        const t = list.find(x => x.language === s.solution!.language);
        if (t) this.spec.set({ ...s, solution: { language: t.language, code: t.solve } });
      },
      error: (e: AppError) => this.error.set(e.message),
    });
  }

  save(publish: boolean): void {
    const s = this.spec()!;
    this.busy.set(true);
    this.error.set('');
    this.result.set(null);
    const failed = (e: AppError) => { this.busy.set(false); this.error.set(e.message); };
    this.api.saveProblem(s).subscribe({
      next: () => {
        this.changed.emit();
        if (!publish) { this.busy.set(false); this.toast.success('Saved as draft'); return; }
        this.api.publish(s.key).subscribe({
          next: r => { this.busy.set(false); this.result.set(r); this.changed.emit(); },
          error: failed,
        });
      },
      error: failed,
    });
  }
}
