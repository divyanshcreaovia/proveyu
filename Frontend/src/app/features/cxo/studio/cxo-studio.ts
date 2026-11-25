import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CxoService } from '../cxo.service';
import { CxoBoard } from '../widgets/cxo-board';
import { ToastService } from '../../../core/services/toast.service';
import { BuildEvent, BuildStatus, Catalog, DashboardSpec, Insight, WidgetData } from '../cxo.models';
import { coverage } from '../cxo-coverage';

type StepKey = BuildStatus['step'];
const STEP_ORDER: StepKey[] = ['understand', 'plan', 'query', 'insight'];
const LIVE_EVERY_MS = 30_000;
const DEFAULT_PROMPT = 'Give me a complete placement overview for this season: placement rate, offers, packages, branches and top recruiters';

/**
 * Leadership overview: one compact dark dashboard. Opens the pinned (or latest) saved dashboard straight away, or builds an overview
 * live on the first visit. Every question to "Ask AI" builds a fresh dashboard (never a change to the one on screen); New clears the canvas.
 */
@Component({
  selector: 'pm-cxo-studio',
  imports: [FormsModule, CxoBoard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cxo-studio.html',
  styleUrl: './cxo-studio.scss',
})
export class CxoStudio implements OnInit {
  readonly cxo = inject(CxoService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  catalog = signal<Catalog | null>(null);
  question = '';
  focused = signal(false);

  building = signal(false);
  step = signal<StepKey | null>(null);
  statusMessage = signal('');
  error = signal('');
  spec = signal<DashboardSpec | null>(null);
  data = signal<Record<string, WidgetData>>({});
  insight = signal<Insight | null>(null);
  source = signal<'AI' | 'RULES' | null>(null);
  /** The last build came from the AI answer cache (no model call). */
  cached = signal(false);
  /** Notes from the server about the data (a year not on record, nothing recorded yet). */
  notices = signal<string[]>([]);
  /** Which data the board shows: batch, branch, period, what the tiles are compared with. */
  coverageLine = computed(() => coverage(this.spec(), this.data(), this.catalog()?.batches ?? []));
  savedId = signal<string | null>(null);
  saving = signal(false);
  live = signal(false);
  refreshing = signal(false);
  loadingFirst = signal(true);
  lastUpdated = signal<number | null>(null);
  private now = signal(Date.now());
  private lastPrompt = '';
  /** The question the dashboard started from (saved with it; follow-ups do not replace it). */
  private basePrompt = '';
  private lastCurrent: DashboardSpec | null = null;
  private abort?: AbortController;
  private liveTimer?: ReturnType<typeof setInterval>;

  /** 0..100 for the slim progress line while building. */
  progress = computed(() => {
    if (!this.building()) return 0;
    const i = STEP_ORDER.indexOf(this.step() ?? 'understand');
    const widgets = this.spec()?.widgets.length ?? 0;
    const got = Object.keys(this.data()).length;
    return Math.min(96, 8 + i * 22 + (i === 2 && widgets ? Math.round((got / widgets) * 22) : 0));
  });
  updatedAgo = computed(() => {
    const t = this.lastUpdated();
    if (!t) return '';
    const s = Math.max(0, Math.round((this.now() - t) / 1000));
    return s < 5 ? 'Updated just now' : s < 60 ? `Updated ${s}s ago` : `Updated ${Math.floor(s / 60)}m ago`;
  });
  placeholder = computed(() => 'Ask AI about placements, packages, drives, tests, comparisons…');

  ngOnInit(): void {
    this.cxo.catalog().subscribe(c => this.catalog.set(c));
    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => { clearInterval(clock); this.stopLive(); this.abort?.abort(); });
    const id = this.route.snapshot.queryParamMap.get('d');
    if (id) { this.open(id); return; }
    // instant first paint: the last dashboard from this browser, then live numbers
    const snap = this.cxo.loadSnapshot();
    if (snap) {
      this.loadingFirst.set(false);
      this.spec.set(snap.spec); this.data.set(snap.data); this.insight.set(snap.insight); this.savedId.set(snap.savedId);
      this.source.set(snap.source); this.lastPrompt = this.basePrompt = snap.prompt; this.lastUpdated.set(snap.at);
      this.toggleLive(true);
      if (!snap.insight) this.summarize();
      return;
    }
    // first visit: the pinned or latest saved dashboard, else build an overview live
    this.cxo.dashboards().subscribe({
      next: list => list.length ? this.open(list[0].id) : this.firstBuild(),
      error: () => this.firstBuild(),
    });
  }

  private firstBuild(): void {
    this.loadingFirst.set(false);
    this.basePrompt = DEFAULT_PROMPT;
    this.run(DEFAULT_PROMPT, null);
  }

  /** Opens a saved dashboard with live numbers. */
  private open(id: string): void {
    this.cxo.dashboard(id).subscribe({
      next: d => {
        this.loadingFirst.set(false);
        this.savedId.set(d.id);
        this.spec.set({ ...d.spec, title: d.title });
        this.lastPrompt = this.basePrompt = d.prompt ?? '';
        this.insight.set(null);
        this.toggleLive(true);
        this.summarize();
      },
      error: () => this.firstBuild(),
    });
  }

  enter(e: Event): void {
    const k = e as KeyboardEvent;
    if (k.shiftKey) return;
    k.preventDefault();
    this.ask();
  }

  /** Builds a new dashboard for the question: the one on screen is not carried over, so every answer stands on its own. */
  ask(text?: string): void {
    const q = (text ?? this.question).trim();
    if (!q || this.building()) return;
    this.question = '';
    this.focused.set(false);
    this.savedId.set(null); // a new dashboard: "Save" creates a new one, the opened saved dashboard stays as it was
    this.basePrompt = q;
    if (this.route.snapshot.queryParamMap.has('d')) this.router.navigate([], { queryParams: {} });
    this.run(q, null);
  }

  retry(): void {
    if (this.lastPrompt) this.run(this.lastPrompt, this.lastCurrent);
  }

  reset(): void {
    this.stopLive();
    this.abort?.abort();
    this.spec.set(null); this.data.set({}); this.insight.set(null); this.source.set(null); this.error.set(''); this.notices.set([]);
    this.step.set(null); this.savedId.set(null); this.question = ''; this.lastUpdated.set(null);
    if (this.route.snapshot.queryParamMap.has('d')) this.router.navigate([], { queryParams: {} });
  }

  private async run(prompt: string, current: DashboardSpec | null): Promise<void> {
    this.lastPrompt = prompt;
    this.lastCurrent = current;
    this.stopLive();
    this.abort?.abort();
    this.abort = new AbortController();
    this.building.set(true); this.error.set(''); this.insight.set(null); this.notices.set([]);
    this.step.set('understand'); this.statusMessage.set('Reading your question');
    if (!current) { this.spec.set(null); this.data.set({}); }
    try {
      await this.cxo.build(prompt, current, e => this.onEvent(e), this.abort.signal);
    } catch (e) {
      if ((e as Error).name !== 'AbortError') this.error.set((e as Error).message || 'Could not build the dashboard.');
    } finally {
      this.building.set(false);
      if (this.spec() && !this.error()) this.persist();
    }
  }

  private onEvent(e: BuildEvent): void {
    switch (e.type) {
      case 'status': this.step.set(e.data.step); this.statusMessage.set(e.data.message); break;
      case 'plan': this.spec.set(e.data); this.data.set({}); break;
      case 'widget': this.data.update(d => ({ ...d, [e.data.widgetId]: e.data })); break;
      case 'insight': this.insight.set(e.data); break;
      case 'done':
        this.spec.set(e.data.spec); this.source.set(e.data.source); this.cached.set(e.data.cached); this.step.set(null);
        this.statusMessage.set(''); this.lastUpdated.set(Date.now()); this.notices.set(e.data.notices ?? []);
        break;
      case 'error': this.error.set(e.data.message); break;
    }
  }

  // ------------------------------------------------------------------ live numbers

  toggleLive(on = !this.live()): void {
    this.stopLive();
    if (!on) return;
    this.live.set(true);
    this.refresh();
    this.liveTimer = setInterval(() => this.refresh(), LIVE_EVERY_MS);
  }

  private stopLive(): void {
    clearInterval(this.liveTimer);
    this.live.set(false);
  }

  refresh(): void {
    const s = this.spec();
    if (!s || this.refreshing()) return;
    this.refreshing.set(true);
    this.cxo.run(s).subscribe({
      next: rows => {
        const map: Record<string, WidgetData> = {};
        rows.forEach(r => (map[r.widgetId] = r));
        this.data.set(map);
        this.lastUpdated.set(Date.now());
        this.refreshing.set(false);
        this.persist();
      },
      error: () => this.refreshing.set(false),
    });
  }

  summarizing = signal(false);

  /** Writes (or fetches from the cache) the AI summary for the dashboard on screen. */
  private summarize(): void {
    const s = this.spec();
    if (!s) return;
    this.summarizing.set(true);
    this.cxo.summary(this.basePrompt || null, s).subscribe({
      next: i => { this.insight.set(i); this.summarizing.set(false); this.persist(); },
      error: () => this.summarizing.set(false),
    });
  }

  /** Remembers what is on screen in this browser, for an instant first paint next time. */
  private persist(): void {
    const s = this.spec();
    if (!s || this.building()) return;
    this.cxo.saveSnapshot({ spec: s, data: this.data(), insight: this.insight(), savedId: this.savedId(), prompt: this.basePrompt,
      source: this.source(), at: this.lastUpdated() ?? Date.now() });
  }

  // ------------------------------------------------------------------ save

  save(): void {
    const s = this.spec();
    if (!s) return;
    this.saving.set(true);
    const body = { title: s.title, prompt: this.basePrompt || this.lastPrompt || null, spec: s };
    const id = this.savedId();
    (id ? this.cxo.update(id, body) : this.cxo.save(body)).subscribe({
      next: d => { this.saving.set(false); this.savedId.set(d.id); this.persist(); this.toast.success(id ? 'Dashboard updated' : 'Dashboard saved'); },
      error: () => this.saving.set(false), // the error interceptor shows the message
    });
  }
}
