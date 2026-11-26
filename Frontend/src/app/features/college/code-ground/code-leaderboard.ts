import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { api, ApiResponse } from '../../../core/api';
import { EmptyState, Skeleton } from '../../../shared/ui/states';
import { CollegeService } from '../services/college.service';

interface StaffRow {
  rank: number; name: string; rollNumber: string; branch: string | null; batchYear: number; points: number; solved: number; streak: number;
  lastActive: string;
}
interface StaffBoard { period: 'week' | 'all'; weekStart: string; participants: number; rows: StaffRow[]; }

/** Code ground ranking of the college's students (a coordinator limited to some branches sees only those). */
@Component({
  selector: 'pm-code-leaderboard',
  imports: [FormsModule, DatePipe, EmptyState, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cl-card">
      <div class="d-flex flex-wrap gap-2 align-items-end mb-3">
        <div><label class="form-label small mb-1" for="cl-period">Period</label>
          <select id="cl-period" class="form-select form-select-sm" [(ngModel)]="period" (change)="load()">
            <option value="week">This week</option><option value="all">All time</option></select></div>
        <div><label class="form-label small mb-1" for="cl-branch">Branch</label>
          <select id="cl-branch" class="form-select form-select-sm" [(ngModel)]="branchId" (change)="load()">
            <option value="">All branches</option>@for (b of college.branches(); track b.id) { <option [value]="b.id">{{ b.name }}</option> }</select></div>
        <div><label class="form-label small mb-1" for="cl-batch">Batch</label>
          <select id="cl-batch" class="form-select form-select-sm" [(ngModel)]="batch" (change)="load()">
            <option value="">All batches</option>@for (b of college.batches(); track b.id) { <option [value]="b.gradYear">{{ b.gradYear }}</option> }</select></div>
        @if (board(); as d) {
          <div class="ms-auto small text-body-secondary">{{ d.participants }} student(s) on the board
            @if (d.period === 'week') { · week from {{ d.weekStart | date: 'd MMM' }} }</div>
        }
      </div>

      @if (failed()) {
        <pm-empty-state icon="bi-wifi-off" title="Could not load the leaderboard" />
      } @else if (!board()) {
        <pm-skeleton [rows]="6" />
      } @else if (!board()!.rows.length) {
        <pm-empty-state icon="bi-trophy" title="No activity yet" text="Students appear here once they solve a problem or read a learning guide in Code ground." />
      } @else {
        <div class="table-responsive"><table class="table table-sm table-hover small align-middle mb-0">
          <thead><tr><th>Rank</th><th>Student</th><th>Roll number</th><th>Branch</th><th>Batch</th><th class="text-end">Points</th><th class="text-end">Solved</th>
            <th class="text-end">Streak (days)</th><th>Last active</th></tr></thead>
          <tbody>
            @for (r of board()!.rows; track r.rank) {
              <tr><td class="fw-semibold">@if (r.rank <= 3) { <i class="bi bi-trophy-fill cl-medal" [attr.data-rank]="r.rank" aria-hidden="true"></i> }{{ r.rank }}</td>
                <td>{{ r.name }}</td><td>{{ r.rollNumber }}</td><td>{{ r.branch || '—' }}</td><td>{{ r.batchYear }}</td>
                <td class="text-end fw-semibold">{{ r.points }}</td><td class="text-end">{{ r.solved }}</td><td class="text-end">{{ r.streak }}</td>
                <td>{{ r.lastActive | date: 'd MMM y' }}</td></tr>
            }
          </tbody></table></div>
        @if (board()!.participants > board()!.rows.length) { <p class="small text-body-secondary mt-2 mb-0">Showing the top {{ board()!.rows.length }}.</p> }
      }
      <p class="small text-body-secondary mt-3 mb-0">Points: Easy 10, Medium 20, Hard 40, learning guide 5, and the problem of the day gives its points again. This is practice activity: it does not check whether code was copied.</p>
    </section>`,
  styles: [`.cl-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; padding: 1.5rem; }
    .cl-medal { margin-right: .35rem; } .cl-medal[data-rank='1'] { color: #eab308; } .cl-medal[data-rank='2'] { color: #94a3b8; } .cl-medal[data-rank='3'] { color: #c2763a; }`],
})
export class CodeLeaderboard implements OnInit {
  private http = inject(HttpClient);
  college = inject(CollegeService);
  period: 'week' | 'all' = 'week';
  branchId = '';
  batch = '';
  board = signal<StaffBoard | null>(null);
  failed = signal(false);
  private request = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const n = ++this.request;
    let params = new HttpParams().set('period', this.period);
    if (this.branchId) params = params.set('branchId', this.branchId);
    if (this.batch) params = params.set('batchYear', this.batch);
    this.failed.set(false);
    this.http.get<ApiResponse<StaffBoard>>(api('/api/v1/college/code-ground/leaderboard'), { params }).subscribe({
      next: r => { if (n === this.request) this.board.set(r.data); },
      error: () => { if (n === this.request) this.failed.set(true); },
    });
  }
}
