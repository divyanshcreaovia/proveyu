import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Board, BoardRow, Period, Scope } from './code-ground.models';
import { CodeGroundService } from './code-ground.service';

/**
 * The student's college ranking: this week or all time, whole college / my branch / my batch. Top three on a podium, the rest of
 * the top ten below, and the student's own rank and streak even when they are further down.
 */
@Component({
  selector: 'pm-leaderboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cg-shared.scss',
  template: `
    <div class="lb-filters">
      <div class="lb-seg" role="group" aria-label="Period">
        <button type="button" [attr.aria-pressed]="period() === 'week'" (click)="pick('week', scope())">This week</button>
        <button type="button" [attr.aria-pressed]="period() === 'all'" (click)="pick('all', scope())">All time</button>
      </div>
      <div class="lb-seg" role="group" aria-label="Who to compare with">
        <button type="button" [attr.aria-pressed]="scope() === 'college'" (click)="pick(period(), 'college')">College</button>
        <button type="button" [attr.aria-pressed]="scope() === 'branch'" (click)="pick(period(), 'branch')">{{ board()?.myBranch || 'My branch' }}</button>
        <button type="button" [attr.aria-pressed]="scope() === 'batch'" (click)="pick(period(), 'batch')">Batch {{ board()?.myBatchYear || '' }}</button>
      </div>
    </div>

    @if (failed()) {
      <div class="cg-panel lb-msg">The leaderboard could not be loaded. <button type="button" class="cg-btn cg-btn-primary ms-2" (click)="load()">Try again</button></div>
    } @else if (!board()) {
      <div class="cg-panel lb-msg">Loading the leaderboard…</div>
    } @else {
      @let b = board()!;
      <section class="cg-panel lb-me" aria-label="Your standing" aria-live="polite">
        <div><div class="cg-kicker">Your rank</div><strong>{{ b.me.rank ? '#' + b.me.rank : '—' }}</strong><span>of {{ b.participants }}</span></div>
        <div><div class="cg-kicker">Points</div><strong>{{ b.me.points }}</strong><span>{{ b.me.solved }} solved</span></div>
        <div><div class="cg-kicker">Streak</div><strong [class.lb-hot]="b.me.streak > 0"><i class="bi bi-fire" aria-hidden="true"></i> {{ b.me.streak }}</strong>
          <span>{{ b.me.streak === 1 ? 'day' : 'days' }}</span></div>
        <p class="lb-nudge">
          @if (!b.me.rank) { Solve a problem or read a guide to get on the board. }
          @else if (b.me.pointsToNext) { <strong>{{ b.me.pointsToNext }}</strong> more points to move up to #{{ b.me.rank - 1 }}. }
          @else { You are leading. Keep it that way! }
        </p>
      </section>

      @if (!b.top.length) {
        <div class="cg-panel lb-msg">Nobody is on this board yet. Be the first.</div>
      } @else {
        <ol class="lb-podium" aria-label="Top three">
          @for (r of podium(); track r.rank) {
            <li class="cg-panel" [attr.data-rank]="r.rank" [class.lb-you]="r.you">
              <span class="lb-medal" aria-hidden="true"><i class="bi bi-trophy-fill"></i></span>
              <span class="lb-place">#{{ r.rank }}</span>
              <span class="lb-name">{{ r.name }}@if (r.you) { <em>You</em> }</span>
              <span class="lb-sub">{{ r.branch || 'No branch' }} · {{ r.batchYear }}</span>
              <span class="lb-pts">{{ r.points }} <small>pts</small></span>
              <span class="lb-sub">{{ r.solved }} solved · <i class="bi bi-fire" aria-hidden="true"></i> {{ r.streak }}</span>
            </li>
          }
        </ol>
        @if (rest().length) {
          <section class="cg-panel lb-table" aria-label="Rank four to ten">
            <table>
              <thead><tr><th>Rank</th><th>Student</th><th class="lb-hide">Solved</th><th class="lb-hide">Streak</th><th>Points</th></tr></thead>
              <tbody>
                @for (r of rest(); track r.rank) {
                  <tr [class.lb-you]="r.you">
                    <td>#{{ r.rank }}</td>
                    <td><span class="lb-name">{{ r.name }}@if (r.you) { <em>You</em> }</span><span class="lb-sub">{{ r.branch || 'No branch' }} · {{ r.batchYear }}</span></td>
                    <td class="lb-hide">{{ r.solved }}</td>
                    <td class="lb-hide"><i class="bi bi-fire" aria-hidden="true"></i> {{ r.streak }}</td>
                    <td class="lb-num">{{ r.points }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      }
      <p class="lb-rules">Easy 10 · Medium 20 · Hard 40 · learning guide 5, each counted once; the problem of the day gives its points again. The weekly board starts again every Monday.
        With equal points, whoever got there first is ahead.</p>
    }`,
  styles: [`
    :host { display: block; }
    .lb-filters { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .75rem; margin-bottom: 1rem; }
    .lb-seg { display: inline-flex; flex-wrap: wrap; padding: 3px; border-radius: 9px; background: var(--cg-sub); border: 1px solid var(--cg-line); }
    .lb-seg button { all: unset; cursor: pointer; padding: .35rem .85rem; border-radius: 7px; font-size: .8rem; font-weight: 600; color: var(--pm-muted); }
    .lb-seg [aria-pressed='true'] { background: var(--cg-panel); color: var(--pm-heading); box-shadow: 0 1px 2px rgba(0,0,0,.08); }
    .lb-seg button:focus-visible { outline: 2px solid var(--pm-link); }
    .lb-msg { padding: 1.5rem; text-align: center; color: var(--pm-muted); margin-bottom: 1rem; }
    .lb-me { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem 2.5rem; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .lb-me > div strong { font-size: 1.6rem; font-weight: 700; color: var(--pm-heading); margin-right: .35rem; font-variant-numeric: tabular-nums; }
    .lb-me > div span { font-size: .78rem; color: var(--pm-muted); }
    .lb-me .lb-hot { color: #ea580c; }
    .lb-nudge { margin: 0 0 0 auto; font-size: .86rem; color: var(--pm-label); }
    .lb-podium { list-style: none; margin: 0 0 1rem; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; align-items: end; }
    .lb-podium li { display: flex; flex-direction: column; align-items: center; text-align: center; gap: .2rem; padding: 1.1rem .75rem; border-top: 3px solid var(--medal); }
    .lb-podium li[data-rank='1'] { --medal: #eab308; order: 2; padding-top: 1.9rem; padding-bottom: 1.6rem; }
    .lb-podium li[data-rank='2'] { --medal: #94a3b8; order: 1; }
    .lb-podium li[data-rank='3'] { --medal: #c2763a; order: 3; }
    .lb-medal { font-size: 1.5rem; color: var(--medal); }
    .lb-place { font-size: .72rem; font-weight: 700; color: var(--pm-muted); }
    .lb-name { font-weight: 700; color: var(--pm-heading); overflow-wrap: anywhere; }
    .lb-name em { font-style: normal; font-size: .66rem; font-weight: 700; margin-left: .4rem; padding: .05rem .4rem; border-radius: 999px; color: var(--pm-link);
      background: color-mix(in srgb, var(--pm-link) 14%, transparent); }
    .lb-sub { display: block; font-size: .76rem; color: var(--pm-muted); }
    .lb-pts { font-size: 1.5rem; font-weight: 700; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .lb-pts small { font-size: .72rem; font-weight: 600; color: var(--pm-muted); }
    .lb-you { background: color-mix(in srgb, var(--pm-link) 8%, var(--cg-panel)); }
    .lb-table { overflow: hidden; }
    table { width: 100%; border-collapse: collapse; font-size: .86rem; color: var(--pm-label); }
    th { text-align: left; font-size: .7rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--pm-muted); background: var(--cg-sub); }
    th, td { padding: .6rem .9rem; border-bottom: 1px solid var(--cg-line); }
    tbody tr:last-child td { border-bottom: 0; }
    .lb-num { font-weight: 700; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .lb-rules { margin: .85rem 0 0; font-size: .76rem; color: var(--pm-muted); }
    @media (max-width: 575.98px) {
      .lb-podium { grid-template-columns: minmax(0, 1fr); }
      .lb-podium li { order: 0 !important; padding: .9rem !important; }
      .lb-hide { display: none; }
      .lb-nudge { margin-left: 0; }
    }
  `],
})
export class Leaderboard implements OnInit {
  private api = inject(CodeGroundService);
  period = signal<Period>('week');
  scope = signal<Scope>('college');
  board = signal<Board | null>(null);
  failed = signal(false);
  podium = computed<BoardRow[]>(() => this.board()?.top.slice(0, 3) ?? []);
  rest = computed<BoardRow[]>(() => this.board()?.top.slice(3) ?? []);

  ngOnInit(): void {
    this.load();
  }

  pick(period: Period, scope: Scope): void {
    this.period.set(period);
    this.scope.set(scope);
    this.load();
  }

  load(): void {
    const period = this.period(), scope = this.scope();
    this.failed.set(false);
    this.api.leaderboard(period, scope).subscribe({
      // ignore an answer for filters the student has already left
      next: b => { if (this.period() === period && this.scope() === scope) this.board.set(b); },
      error: () => this.failed.set(true),
    });
  }
}
