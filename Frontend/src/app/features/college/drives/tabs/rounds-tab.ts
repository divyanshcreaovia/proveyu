import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoundService } from '../../services/round.service';
import { CollegeService } from '../../services/college.service';
import { ReportService } from '../../services/report.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog';
import { Drive, Round, RoundResultRow, RoundResultValue, RoundType } from '../../../../core/models/college.models';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { EmptyState } from '../../../../shared/ui/states';
import { FileUpload } from '../../../../shared/ui/file-upload';

/** Configure rounds, start them, record results (inline or upload) and complete with auto-advance. */
@Component({
  selector: 'pm-rounds-tab',
  imports: [FormsModule, DatePipe, StatusBadge, EmptyState, FileUpload],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-lg-4">
        <section class="pm-rt-card">
          <div class="d-flex justify-content-between align-items-center mb-2"><h3 class="h6 m-0">Rounds</h3>
            <button class="btn btn-sm btn-link" (click)="reports.download('drive', 'xlsx', null, drive().id).subscribe()"><i class="bi bi-download"></i> Round-wise report</button></div>
          @for (r of rounds(); track r.id; let i = $index) {
            <div class="pm-rt-round" [class.pm-rt-sel]="sel()?.id === r.id">
              <button type="button" class="pm-rt-pick" (click)="select(r)">
                <span class="pm-rt-seq">{{ r.seq }}</span>
                <span class="flex-grow-1 text-start min-w-0"><span class="d-block fw-semibold text-truncate">{{ r.name }}</span>
                  <small class="text-body-secondary">{{ r.roundType }} @if (r.scheduledAt) { · {{ r.scheduledAt | date: 'short' }} }</small>
                  <small class="d-block">@for (c of entries(r); track c[0]) { <span class="me-2">{{ c[0] }} {{ c[1] }}</span> }</small></span>
                <pm-status [value]="r.status" />
              </button>
              @if (college.canWrite() && r.status === 'PENDING') {
                <div class="btn-group btn-group-sm mt-1">
                  <button class="btn btn-outline-secondary" [disabled]="i === 0 || !allPending()" (click)="move(i, -1)" aria-label="Move up"><i class="bi bi-arrow-up"></i></button>
                  <button class="btn btn-outline-secondary" [disabled]="i === rounds().length - 1 || !allPending()" (click)="move(i, 1)" aria-label="Move down"><i class="bi bi-arrow-down"></i></button>
                  <button class="btn btn-outline-danger" (click)="remove(r)" aria-label="Delete round"><i class="bi bi-trash"></i></button>
                  <button class="btn btn-primary" (click)="start(r)">Start</button>
                </div>
              }
            </div>
          } @empty { <pm-empty-state icon="bi-diagram-3" title="No rounds configured" /> }
          @if (college.canWrite() && drive().status !== 'COMPLETED' && drive().status !== 'CANCELLED') {
            <form class="border-top pt-3 mt-2" (ngSubmit)="add()">
              <label class="form-label small fw-semibold" for="rt-name">Add round</label>
              <input id="rt-name" class="form-control form-control-sm mb-2" placeholder="Round name" [(ngModel)]="newName" name="n" required />
              <label class="form-label small" for="rt-type">Type</label>
              <select id="rt-type" class="form-select form-select-sm mb-2" [(ngModel)]="newType" name="t">@for (t of types; track t) { <option [value]="t">{{ t }}</option> }</select>
              <label class="form-label small" for="rt-at">Scheduled at (optional)</label>
              <input id="rt-at" type="datetime-local" class="form-control form-control-sm" [(ngModel)]="newAt" name="a" />
              <button class="btn btn-sm btn-outline-primary mt-2 w-100" [disabled]="!newName.trim()">Add round</button>
            </form>
          }
        </section>
      </div>
      <div class="col-lg-8">
        <section class="pm-rt-card">
          @if (!sel()) { <pm-empty-state icon="bi-hand-index" title="Select a round" text="Pick a round to record per-student results." /> }
          @else {
            @let r = sel()!;
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <h3 class="h6 m-0">R{{ r.seq }} · {{ r.name }} <pm-status [value]="r.status" /></h3>
              @if (college.canWrite() && r.status === 'ONGOING') {
                <div class="d-flex gap-2 flex-wrap">
                  <button class="btn btn-sm btn-outline-success" (click)="setAll('PASS')">All pass</button>
                  <button class="btn btn-sm btn-outline-primary" [disabled]="!dirty()" (click)="save()">Save results</button>
                  <button class="btn btn-sm btn-success" (click)="complete(r)">Complete & advance</button>
                </div>
              }
            </div>
            @if (r.status === 'PENDING') { <div class="alert alert-light border small">Start this round to load participants (round 1 takes all SHORTLISTED applicants; later rounds take those who passed the previous round).</div> }
            @if (results().length) {
              <div class="table-responsive"><table class="table table-sm align-middle small">
                <thead><tr><th>Student</th><th>Result</th><th class="d-none d-md-table-cell">Score</th><th class="d-none d-md-table-cell">Remarks</th></tr></thead>
                <tbody>@for (x of results(); track x.applicationId) {
                  <tr><td><strong>{{ x.studentName }}</strong><br><span class="text-body-secondary">{{ x.rollNumber }} · {{ x.branchCode }}</span></td>
                    <td>@if (editable()) {
                        <select class="form-select form-select-sm" [attr.aria-label]="'Result for ' + x.studentName" [(ngModel)]="x.result" (change)="dirty.set(true)">
                          @for (v of values; track v) { <option [value]="v">{{ v }}</option> }</select>
                      } @else { <pm-status [value]="x.result" /> }</td>
                    <td class="d-none d-md-table-cell">@if (editable()) { <input type="number" class="form-control form-control-sm" style="width:90px" aria-label="Score" [(ngModel)]="x.score" (change)="dirty.set(true)" /> } @else { {{ x.score ?? '-' }} }</td>
                    <td class="d-none d-md-table-cell">@if (editable()) { <input class="form-control form-control-sm" aria-label="Remarks" [(ngModel)]="x.remarks" (change)="dirty.set(true)" /> } @else { {{ x.remarks ?? '' }} }</td>
                  </tr> }</tbody></table></div>
              @if (editable()) {
                <details class="mt-2"><summary class="small">Bulk upload results (.csv / .xlsx: roll_number, result, score, remarks)</summary>
                  <div class="mt-2"><pm-file-upload accept=".csv,.xlsx,.xls" (fileSelected)="upload($event)" /></div></details>
              }
            } @else if (r.status !== 'PENDING') { <pm-empty-state icon="bi-people" title="No participants in this round" /> }
          }
        </section>
      </div>
    </div>`,
  styles: [`
    .pm-rt-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }
    .pm-rt-round { border: 1px solid var(--bs-border-color); border-radius: .6rem; padding: .5rem; margin-bottom: .5rem; }
    .pm-rt-sel { border-color: #2563eb; box-shadow: 0 0 0 1px #2563eb; }
    .pm-rt-pick { all: unset; display: flex; gap: .6rem; align-items: center; width: 100%; cursor: pointer; font-size: .85rem; box-sizing: border-box; }
    .pm-rt-pick:focus-visible { outline: 2px solid #2563eb; border-radius: .4rem; }
    .pm-rt-seq { width: 1.7rem; height: 1.7rem; border-radius: 50%; background: var(--bs-secondary-bg); display: grid; place-items: center; font-weight: 700; flex-shrink: 0; }
  `],
})
export class RoundsTab implements OnInit {
  private svc = inject(RoundService);
  college = inject(CollegeService);
  reports = inject(ReportService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  drive = input.required<Drive>();
  changed = output<void>();
  types: RoundType[] = ['APTITUDE', 'CODING', 'GD', 'TECHNICAL', 'HR', 'CUSTOM'];
  values: RoundResultValue[] = ['PENDING', 'PASS', 'FAIL', 'ABSENT', 'HOLD'];
  rounds = signal<Round[]>([]);
  sel = signal<Round | null>(null);
  results = signal<RoundResultRow[]>([]);
  dirty = signal(false);
  newName = ''; newType: RoundType = 'TECHNICAL'; newAt = '';

  ngOnInit(): void { this.loadRounds(); }
  entries(r: Round): [string, number][] { return Object.entries(r.resultCounts); }
  allPending(): boolean { return this.rounds().every(r => r.status === 'PENDING'); }
  editable(): boolean { return this.college.canWrite() && this.sel()?.status === 'ONGOING'; }

  loadRounds(keep = this.sel()?.id): void {
    this.svc.list(this.drive().id).subscribe(r => {
      this.rounds.set(r);
      const s = r.find(x => x.id === keep) ?? r.find(x => x.status === 'ONGOING') ?? null;
      if (s) this.select(s);
    });
  }
  select(r: Round): void {
    this.sel.set(r);
    this.dirty.set(false);
    this.svc.results(r.id).subscribe(x => this.results.set(x));
  }
  add(): void {
    this.svc.add(this.drive().id, { name: this.newName.trim(), roundType: this.newType, scheduledAt: this.newAt ? new Date(this.newAt).toISOString() : null })
      .subscribe(() => { this.newName = ''; this.newAt = ''; this.loadRounds(); this.changed.emit(); });
  }
  move(i: number, d: number): void {
    const ids = this.rounds().map(r => r.id);
    [ids[i], ids[i + d]] = [ids[i + d], ids[i]];
    this.svc.reorder(this.drive().id, ids).subscribe(r => this.rounds.set(r));
  }
  async remove(r: Round): Promise<void> {
    const ok = await this.confirm.ask({ title: `Delete ${r.name}?`, message: 'Only rounds that have not started can be deleted.', tone: 'danger' });
    if (ok.ok) this.svc.remove(r.id).subscribe(() => { this.sel.set(null); this.loadRounds(); this.changed.emit(); });
  }
  async start(r: Round): Promise<void> {
    const ok = await this.confirm.ask({ title: `Start ${r.name}?`, message: 'Participants are notified that they are shortlisted for this round.' });
    if (ok.ok) this.svc.start(r.id).subscribe(x => { this.toast.success('Round started'); this.loadRounds(x.id); this.changed.emit(); });
  }
  setAll(v: RoundResultValue): void { this.results.update(rows => rows.map(r => ({ ...r, result: v }))); this.dirty.set(true); }
  save(): void {
    const body = this.results().map(r => ({ applicationId: r.applicationId, result: r.result, score: r.score === undefined || (r.score as unknown) === '' ? null : r.score, remarks: r.remarks || null }));
    this.svc.saveResults(this.sel()!.id, body).subscribe(x => { this.results.set(x); this.dirty.set(false); this.toast.success('Results saved'); this.loadRounds(); });
  }
  upload(f: File): void {
    this.svc.upload(this.sel()!.id, f).subscribe(r => {
      this.toast.success(`${r.updated} result(s) updated`);
      if (r.errors.length) this.toast.warning(r.errors.slice(0, 3).join(' | '));
      this.select(this.sel()!); this.loadRounds();
    });
  }
  async complete(r: Round): Promise<void> {
    if (this.dirty()) { this.toast.warning('Save results first'); return; }
    const ok = await this.confirm.ask({ title: `Complete ${r.name}?`, message: 'PASS → next round (or SELECTED after the last round). FAIL / ABSENT → rejected. Everyone must have a final result.' });
    if (ok.ok) this.svc.complete(r.id).subscribe(x => { this.toast.success('Round completed'); this.loadRounds(x.id); this.changed.emit(); });
  }
}
