import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CollegeService } from '../services/college.service';
import { SearchHit, SearchResult } from '../../../core/models/college.models';

/** Ctrl/Cmd + K command palette across students, companies and drives. */
@Component({
  selector: 'pm-global-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'hotkey($event)' },
  template: `
    <button type="button" class="pm-gs-trigger" (click)="openPalette()" aria-label="Search (Ctrl+K)">
      <i class="bi bi-search" aria-hidden="true"></i><span class="d-none d-md-inline">Search…</span><kbd class="d-none d-md-inline">Ctrl K</kbd>
    </button>
    @if (open()) {
      <div class="pm-gs-backdrop" (click)="close()"></div>
      <div class="pm-gs" role="dialog" aria-modal="true" aria-label="Global search">
        <div class="pm-gs-input">
          <i class="bi bi-search" aria-hidden="true"></i>
          <input #box type="search" placeholder="Search students, companies, drives…" aria-label="Search" [value]="q()"
                 (input)="type($any($event.target).value)" (keydown.arrowDown)="move(1, $event)" (keydown.arrowUp)="move(-1, $event)"
                 (keydown.enter)="go(flat()[active()])" (keydown.escape)="close()" />
        </div>
        <div class="pm-gs-results" role="listbox">
          @if (q().length < 2) { <div class="p-3 small text-body-secondary">Type at least 2 characters.</div> }
          @else if (!flat().length) { <div class="p-3 small text-body-secondary">No matches.</div> }
          @for (h of flat(); track h.type + h.id; let i = $index) {
            <button type="button" role="option" class="pm-gs-item" [class.pm-gs-active]="i === active()" [attr.aria-selected]="i === active()"
                    (mouseenter)="active.set(i)" (click)="go(h)">
              <i class="bi" [class.bi-person]="h.type === 'student'" [class.bi-building]="h.type === 'company'" [class.bi-briefcase]="h.type === 'drive'"></i>
              <span class="flex-grow-1 text-truncate"><strong>{{ h.title }}</strong><small class="text-body-secondary ms-2">{{ h.subtitle }}</small></span>
              <small class="text-body-secondary text-capitalize">{{ h.type }}</small>
            </button>
          }
        </div>
      </div>
    }`,
  styles: [`
    .pm-gs-trigger { display: flex; align-items: center; gap: .6rem; border: 0; background: var(--pm-card-bg, #fff); height: 42px;
      color: var(--pm-muted, #64748b); border-radius: 50px; padding: 0 1rem; font-size: .875rem; min-width: 0; box-shadow: 0 2px 8px rgba(15, 23, 42, .06); }
    .pm-gs-trigger:focus-visible { outline: 2px solid #4f46e5; outline-offset: 2px; }
    .pm-gs-trigger kbd { font-size: .65rem; background: var(--pm-soft, #f1f5f9); color: var(--pm-muted, #64748b); border-radius: 6px; }
    .pm-gs-backdrop { position: fixed; inset: 0; background: rgba(15,23,42,.45); z-index: 1060; }
    .pm-gs { position: fixed; z-index: 1065; top: 10vh; left: 50%; transform: translateX(-50%); width: min(620px, calc(100vw - 1.5rem));
      background: var(--pm-card-bg, #fff); border-radius: 18px; box-shadow: 0 25px 60px rgba(15,23,42,.25); overflow: hidden; }
    .pm-gs-input { display: flex; align-items: center; gap: .6rem; padding: .8rem 1rem; border-bottom: 1px solid var(--bs-border-color); }
    .pm-gs-input input { border: 0; outline: 0; flex: 1; background: transparent; color: var(--bs-body-color); font-size: 1rem; }
    .pm-gs-results { max-height: 50vh; overflow-y: auto; }
    .pm-gs-item { all: unset; box-sizing: border-box; display: flex; gap: .7rem; align-items: center; width: 100%; padding: .6rem 1rem; cursor: pointer; font-size: .875rem; }
    .pm-gs-active { background: rgba(37,99,235,.1); }
  `],
})
export class GlobalSearch {
  private college = inject(CollegeService);
  private router = inject(Router);
  private box = viewChild<ElementRef<HTMLInputElement>>('box');
  private input$ = new Subject<string>();
  open = signal(false);
  q = signal('');
  result = signal<SearchResult>({ students: [], companies: [], drives: [] });
  flat = signal<SearchHit[]>([]);
  active = signal(0);

  constructor() {
    this.input$.pipe(debounceTime(220), distinctUntilChanged(),
      switchMap(q => q.trim().length < 2 ? of({ students: [], companies: [], drives: [] }) : this.college.search(q.trim())), takeUntilDestroyed())
      .subscribe(r => { this.result.set(r); this.flat.set([...r.students, ...r.companies, ...r.drives]); this.active.set(0); });
  }

  hotkey(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); this.openPalette(); }
  }
  openPalette(): void { this.open.set(true); setTimeout(() => this.box()?.nativeElement.focus()); }
  close(): void { this.open.set(false); this.q.set(''); this.flat.set([]); }
  type(v: string): void { this.q.set(v); this.input$.next(v); }
  move(d: number, e: Event): void {
    e.preventDefault();
    const n = this.flat().length;
    if (n) this.active.set((this.active() + d + n) % n);
  }
  go(h?: SearchHit): void {
    if (!h) return;
    const path = h.type === 'student' ? ['/college/students'] : h.type === 'company' ? ['/college/companies'] : ['/college/drives', h.id];
    this.router.navigate(path, h.type === 'drive' ? {} : { queryParams: { open: h.id } });
    this.close();
  }
}
