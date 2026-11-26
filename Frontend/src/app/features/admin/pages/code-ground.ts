import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AppError } from '../../../core/api';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Modal } from '../../../shared/ui/modal';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { CodeGroundAdminService, DailyDay, ProblemSummary, Slide, TopicSummary } from '../services/code-ground-admin.service';
import { CodeGroundQuestion } from './code-ground-question';

interface TopicForm { key: string; title: string; summary: string; guide: string; isNew: boolean; }

/** Platform ADMIN: Code ground study plan topics, questions (one at a time or from a JSON file) and the problem of the day. */
@Component({
  selector: 'pm-admin-code-ground',
  imports: [FormsModule, DatePipe, Modal, EmptyState, Skeleton, CodeGroundQuestion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="nav nav-pills small mb-3" role="tablist">
      <li class="nav-item"><button type="button" class="nav-link py-1 px-3" [class.active]="tab() === 'questions'" (click)="tab.set('questions')">Study plan and questions</button></li>
      <li class="nav-item"><button type="button" class="nav-link py-1 px-3" [class.active]="tab() === 'daily'" (click)="showDaily()">Problem of the day</button></li>
    </ul>

    @if (!topics()) { <pm-skeleton [rows]="6" /> }
    @else if (tab() === 'daily') {
      <div class="bg-body border rounded-3 p-3 small">
        <p class="text-body-secondary">Choose a published question for a day. A day you leave on automatic gets one by itself. Solving it that day gives double points.</p>
        @if (days(); as list) {
          <div class="table-responsive"><table class="table table-sm align-middle mb-0">
            <thead><tr><th>Date</th><th>Question</th><th></th></tr></thead>
            <tbody>
              @for (d of list; track d.date; let first = $first) {
                <tr>
                  <td class="text-nowrap">{{ d.date | date: 'EEE, d MMM' }} @if (first) { <span class="badge text-bg-primary">Today</span> }</td>
                  <td>
                    <select class="form-select form-select-sm" [attr.aria-label]="'Question for ' + d.date" [ngModel]="d.problemKey ?? ''" (ngModelChange)="setDay(d, $event)">
                      <option value="">Automatic</option>
                      @for (t of topics(); track t.key) {
                        <optgroup [label]="t.title">@for (p of published(t); track p.key) { <option [value]="p.key">{{ p.title }} ({{ p.difficulty.toLowerCase() }})</option> }</optgroup>
                      }
                    </select>
                    @if (d.unavailable) { <div class="text-danger">The chosen question is no longer published; another is picked automatically.</div> }
                    @if (d.bonusesGiven) { <div class="text-warning-emphasis">{{ d.bonusesGiven }} student(s) already earned today's bonus.</div> }
                  </td>
                  <td class="text-end text-nowrap">@if (d.problemKey) { <span class="badge" [class.text-bg-secondary]="d.auto" [class.text-bg-success]="!d.auto">{{ d.auto ? 'Automatic' : 'Chosen' }}</span> }</td>
                </tr>
              }
            </tbody>
          </table></div>
        } @else { <pm-skeleton [rows]="5" /> }
      </div>
    } @else {
      <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
        <button type="button" class="btn btn-sm btn-primary" (click)="newQuestion('basics')"><i class="bi bi-plus-lg"></i> New question</button>
        <button type="button" class="btn btn-sm btn-outline-primary" (click)="editTopic(null)"><i class="bi bi-folder-plus"></i> New topic</button>
        <label class="btn btn-sm btn-outline-secondary mb-0"><i class="bi bi-upload"></i> Upload JSON
          <input type="file" accept=".json,application/json" class="visually-hidden" (change)="upload($event)" [disabled]="!!progress()" /></label>
        <button type="button" class="btn btn-sm btn-link" (click)="sample()">Download sample file</button>
        @if (drafts().length) {
          <button type="button" class="btn btn-sm btn-success" [disabled]="!!progress()" (click)="publishAll()">Check and publish {{ drafts().length }} draft(s)</button>
        }
        <input type="search" class="form-control form-control-sm ms-auto" style="max-width: 240px" placeholder="Search questions" aria-label="Search questions" [ngModel]="q()" (ngModelChange)="q.set($event)" />
      </div>
      @if (progress()) { <div class="alert alert-info small" role="status"><span class="spinner-border spinner-border-sm" aria-hidden="true"></span> {{ progress() }}</div> }
      @if (problems().length) {
        <div class="alert alert-danger small" role="alert">@for (m of problems(); track $index) { <div>{{ m }}</div> }</div>
      }
      @for (t of shown(); track t.key) {
        <section class="bg-body border rounded-3 p-3 mb-3 small">
          <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
            <h2 class="h6 fw-bold m-0">{{ t.title }}</h2>
            <span class="badge text-bg-light border">{{ t.builtIn ? 'Built-in' : 'Added by you' }}</span>
            <span class="text-body-secondary">{{ t.problems.length }} question(s) · {{ t.guide.length }} guide slide(s)</span>
            <span class="ms-auto d-flex gap-1">
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="newQuestion(t.key)">Add question</button>
              @if (!t.builtIn) {
                <button type="button" class="btn btn-sm btn-outline-secondary" (click)="editTopic(t)">Edit topic</button>
                <button type="button" class="btn btn-sm btn-outline-danger" [disabled]="t.problems.length > 0" (click)="removeTopic(t)">Delete</button>
              }
            </span>
          </div>
          @if (t.problems.length) {
            <div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
              <thead><tr><th>Question</th><th>Difficulty</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
              <tbody>
                @for (p of t.problems; track p.key) {
                  <tr>
                    <td><strong>{{ p.title }}</strong> <span class="text-body-secondary">{{ p.key }}</span></td>
                    <td>{{ p.difficulty.toLowerCase() }}</td>
                    <td><span class="badge" [class.text-bg-success]="p.status === 'PUBLISHED'" [class.text-bg-warning]="p.status === 'DRAFT' || p.status === 'CHANGED'"
                      [class.text-bg-secondary]="p.status === 'HIDDEN'">{{ label[p.status] }}</span></td>
                    <td class="text-end text-nowrap">
                      @if (p.builtIn) {
                        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="toggleHidden(p)">{{ p.status === 'HIDDEN' ? 'Show' : 'Hide' }}</button>
                      } @else {
                        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="editQuestion(p.key)">Edit</button>
                        @if (p.status !== 'PUBLISHED') { <button type="button" class="btn btn-sm btn-outline-success" [disabled]="!!progress()" (click)="publishOne(p)">Publish</button> }
                        @if (p.status !== 'DRAFT') { <button type="button" class="btn btn-sm btn-outline-secondary" (click)="unpublish(p)">Unpublish</button> }
                        <button type="button" class="btn btn-sm btn-outline-danger" (click)="remove(p)" aria-label="Delete question"><i class="bi bi-trash"></i></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table></div>
          }
        </section>
      } @empty { <pm-empty-state icon="bi-search" title="No question matches" /> }
    }

    @if (question(); as form) {
      <pm-code-ground-question [topics]="topics()!" [editKey]="form.key" [topicKey]="form.topic" (changed)="load()" (closed)="question.set(null)" />
    }

    <pm-modal [open]="!!topic()" [title]="topic()?.isNew ? 'New topic' : 'Edit topic'" size="lg" (closed)="topic.set(null)">
      @if (topic(); as f) {
        <div class="small">
          <label class="form-label mb-1" for="t-key">Key (e.g. greedy)</label>
          <input id="t-key" class="form-control form-control-sm mb-2" [(ngModel)]="f.key" [readonly]="!f.isNew" maxlength="60" />
          <label class="form-label mb-1" for="t-title">Title</label>
          <input id="t-title" class="form-control form-control-sm mb-2" [(ngModel)]="f.title" maxlength="120" />
          <label class="form-label mb-1" for="t-summary">Summary</label>
          <input id="t-summary" class="form-control form-control-sm mb-2" [(ngModel)]="f.summary" maxlength="300" />
          <label class="form-label mb-1" for="t-guide">Learning guide (optional): a JSON list of slides, each with tag, title, points and code</label>
          <textarea id="t-guide" class="form-control form-control-sm font-monospace" rows="10" spellcheck="false" [(ngModel)]="f.guide"></textarea>
          <p class="text-body-secondary mt-2 mb-0">A new topic appears to students once it has a published question.</p>
          @if (topicError()) { <div class="alert alert-danger mt-2 mb-0" role="alert">{{ topicError() }}</div> }
        </div>
      }
      <ng-container pmFooter>
        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="topic.set(null)">Cancel</button>
        <button type="button" class="btn btn-sm btn-primary" (click)="saveTopic()">Save topic</button>
      </ng-container>
    </pm-modal>`,
})
export class AdminCodeGroundPage implements OnInit {
  private api = inject(CodeGroundAdminService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  tab = signal<'questions' | 'daily'>('questions');
  topics = signal<TopicSummary[] | null>(null);
  days = signal<DailyDay[] | null>(null);
  q = signal('');
  question = signal<{ key: string | null; topic: string } | null>(null);
  topic = signal<TopicForm | null>(null);
  topicError = signal('');
  progress = signal('');
  problems = signal<string[]>([]);
  label = { PUBLISHED: 'Published', DRAFT: 'Draft', CHANGED: 'Published · has unpublished changes', HIDDEN: 'Hidden' };

  shown = computed(() => {
    const all = this.topics() ?? [], q = this.q().trim().toLowerCase();
    if (!q) return all;
    return all.map(t => ({ ...t, problems: t.problems.filter(p => p.title.toLowerCase().includes(q) || p.key.includes(q)) })).filter(t => t.problems.length);
  });
  drafts = computed(() => (this.topics() ?? []).flatMap(t => t.problems).filter(p => p.status === 'DRAFT' || p.status === 'CHANGED'));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.catalogue().subscribe(t => this.topics.set(t));
  }

  published(t: TopicSummary): ProblemSummary[] {
    return t.problems.filter(p => p.status === 'PUBLISHED' || p.status === 'CHANGED');
  }

  newQuestion(topic: string): void { this.question.set({ key: null, topic }); }
  editQuestion(key: string): void { this.question.set({ key, topic: 'basics' }); }

  editTopic(t: TopicSummary | null): void {
    this.topicError.set('');
    this.topic.set(t ? { key: t.key, title: t.title, summary: t.summary, guide: JSON.stringify(t.guide, null, 2), isNew: false }
      : { key: '', title: '', summary: '', guide: '[]', isNew: true });
  }

  saveTopic(): void {
    const f = this.topic()!;
    if (f.isNew && this.topics()?.some(t => t.key === f.key)) { this.topicError.set('A topic with this key already exists'); return; }
    let guide: Slide[];
    try { guide = JSON.parse(f.guide.trim() || '[]'); if (!Array.isArray(guide)) throw new Error(); }
    catch { this.topicError.set('The learning guide is not a valid JSON list'); return; }
    this.api.saveTopic({ key: f.key, title: f.title, summary: f.summary, guide }).subscribe({
      next: () => { this.topic.set(null); this.toast.success('Topic saved'); this.load(); },
      error: (e: AppError) => this.topicError.set(e.message),
    });
  }

  async removeTopic(t: TopicSummary): Promise<void> {
    if (!(await this.confirm.ask({ title: 'Delete topic?', message: `"${t.title}" will be removed.`, tone: 'danger', confirmText: 'Delete' })).ok) return;
    this.api.deleteTopic(t.key).subscribe(() => this.load());
  }

  async remove(p: ProblemSummary): Promise<void> {
    if (!(await this.confirm.ask({ title: 'Delete question?', message: `"${p.title}" will be removed.`, tone: 'danger', confirmText: 'Delete' })).ok) return;
    this.api.deleteProblem(p.key).subscribe(() => this.load());
  }

  async unpublish(p: ProblemSummary): Promise<void> {
    if (!(await this.confirm.ask({ title: 'Unpublish question?', message: 'Students will no longer see it. Points already earned stay.', confirmText: 'Unpublish' })).ok) return;
    this.api.unpublish(p.key).subscribe(() => this.load());
  }

  async toggleHidden(p: ProblemSummary): Promise<void> {
    const hide = p.status !== 'HIDDEN';
    if (hide && !(await this.confirm.ask({ title: 'Hide question?', message: 'Students will no longer see it. Points already earned stay.', confirmText: 'Hide' })).ok) return;
    this.api.setHidden(p.key, hide).subscribe(() => this.load());
  }

  async publishOne(p: ProblemSummary): Promise<void> {
    await this.publish([p]);
  }

  async publishAll(): Promise<void> {
    await this.publish(this.drafts());
  }

  /** One after another, so the judge is not flooded; each is checked on its own. */
  private async publish(list: ProblemSummary[]): Promise<void> {
    this.problems.set([]);
    const failed: string[] = [];
    let done = 0;
    for (const p of list) {
      this.progress.set(`Checking ${done + 1} of ${list.length}: ${p.title}`);
      await new Promise<void>(next => this.api.publish(p.key).subscribe({
        next: r => {
          if (!r.ok) failed.push(`${p.title}: ${r.errors.join('; ')}${r.failures.length ? ' (' + r.failures.map(f => f.test).join(', ') + ')' : ''}`);
          next();
        },
        error: (e: AppError) => { failed.push(`${p.title}: ${e.message}`); next(); },
      }));
      done++;
    }
    this.progress.set('');
    this.problems.set(failed);
    if (done > failed.length) this.toast.success(`${done - failed.length} question(s) published`);
    this.load();
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.problems.set([]);
    if (file.size > 2_000_000) { this.problems.set(['The file is larger than 2 MB']); return; }
    file.text().then(text => {
      let body: unknown;
      try { body = JSON.parse(text); } catch { this.problems.set(['The file is not valid JSON']); return; }
      this.api.upload(body).subscribe({
        next: r => { this.toast.success(`Uploaded ${r.problems.length} question(s) as drafts. Publish them to show them to students.`); this.load(); },
        error: (e: AppError) => this.problems.set(e.message.split(' · ')),
      });
    });
  }

  sample(): void {
    this.api.sample().subscribe(text => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
      a.download = 'code-ground-sample.json';
      a.click();
      URL.revokeObjectURL(a.href);
    });
  }

  showDaily(): void {
    this.tab.set('daily');
    this.loadDaily();
  }

  private loadDaily(): void {
    this.api.daily(14).subscribe(d => this.days.set(d));
  }

  async setDay(d: DailyDay, key: string): Promise<void> {
    if (key === (d.problemKey ?? '')) return;
    if (d.bonusesGiven && !(await this.confirm.ask({ title: "Change today's question?",
      message: `${d.bonusesGiven} student(s) already earned today's bonus; they keep it and cannot earn another today.`, confirmText: 'Change' })).ok) {
      this.loadDaily();
      return;
    }
    const call = key ? this.api.setDaily(d.date, key) : this.api.clearDaily(d.date);
    call.subscribe({ next: () => { this.toast.success('Saved'); this.loadDaily(); }, error: () => this.loadDaily() });
  }
}
