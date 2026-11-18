import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { DriveRoomService, ResultValue, RoomApplicant, RoomRound, RoomSummary, Scorecard } from './services/drive-room.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../shared/ui/confirm-dialog';
import { Offer, RoundResultRow } from '../../core/models/college.models';
import { AppError } from '../../core/api';
import { StatusBadge } from '../../shared/ui/status-badge';
import { EmptyState, Skeleton } from '../../shared/ui/states';
import { Modal } from '../../shared/ui/modal';

type Tab = 'Overview' | 'Applicants' | 'Rounds' | 'Offers';
const RESULTS: ResultValue[] = ['PASS', 'FAIL', 'HOLD', 'ABSENT'];

/** Company Drive Room: one campus drive, worked on live with the placement cell. */
@Component({
  selector: 'pm-drive-room',
  imports: [DatePipe, DecimalPipe, FormsModule, StatusBadge, EmptyState, Skeleton, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../student/student-pages.scss',
  template: `
    @if (!s()) { <pm-skeleton [rows]="8" /> } @else {
      @let d = s()!;
      <section class="st-card mb-3 dr-head">
        <div class="min-w-0 flex-grow-1">
          <div class="small st-muted">{{ d.companyName }} · {{ d.collegeName }}</div>
          <h2 class="h4 st-h mb-1">{{ d.title }}</h2>
          <div class="small st-muted">{{ d.roleTitle }}@if (d.ctcLpa) { · {{ d.ctcLpa }} LPA }@if (d.stipendMonthly) { · ₹{{ d.stipendMonthly }}/month }
            · {{ d.mode }}@if (d.venue) { · {{ d.venue }} }@if (d.driveDate) { · {{ d.driveDate | date: 'mediumDate' }} }</div>
        </div>
        <pm-status [value]="d.status" />
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-secondary" (click)="report('xlsx')"><i class="bi bi-file-earmark-spreadsheet"></i> Excel report</button>
          <button class="btn btn-sm btn-outline-secondary" (click)="report('pdf')"><i class="bi bi-file-earmark-pdf"></i> PDF</button>
        </div>
      </section>
      @if (!d.canEdit) { <div class="alert alert-light border small"><i class="bi bi-lock"></i> This drive is {{ d.status.toLowerCase() }}. You can view everything; changes are closed.</div> }

      <div class="btn-group btn-group-sm mb-3 flex-wrap" role="tablist" aria-label="Drive Room sections">
        @for (t of tabs; track t) {
          <button type="button" class="btn" role="tab" [class.btn-primary]="tab() === t" [class.btn-outline-secondary]="tab() !== t" (click)="openTab(t)" [attr.aria-selected]="tab() === t">
            {{ t }}@if (t === 'Applicants') { <span class="ms-1 opacity-75">{{ apps().length }}</span> }</button>
        }
      </div>

      @switch (tab()) {
        @case ('Overview') {
          <div class="dr-funnel mb-3">
            @for (f of funnel(); track f.key) {
              <div class="st-card dr-tile"><span class="dr-tile-n">{{ f.value }}</span><span class="dr-tile-l">{{ f.label }}</span></div>
            }
          </div>
          <h3 class="h6 st-h mb-2">Live round board</h3>
          @for (r of d.rounds; track r.id) {
            <div class="st-row">
              <span class="dr-seq">{{ r.seq }}</span>
              <span class="flex-grow-1 min-w-0"><strong class="st-h">{{ r.name }}</strong><span class="small st-muted ms-2">{{ r.roundType }}</span>
                <span class="d-flex gap-1 flex-wrap mt-1">
                  @for (k of resultKeys(r); track k) { <span class="dr-pill dr-{{ k.toLowerCase() }}">{{ k.toLowerCase() }} {{ r.resultCounts[k] }}</span> }
                  @if (!resultKeys(r).length) { <span class="small st-muted">No participants yet</span> }
                </span></span>
              <pm-status [value]="r.status" />
              <button class="btn btn-sm btn-outline-secondary" (click)="openRound(r)">Open</button>
            </div>
          } @empty { <pm-empty-state icon="bi-diagram-3" title="No rounds yet" text="The placement cell adds the selection rounds for this drive." /> }
          @if (d.jobDescription) { <h3 class="h6 st-h mt-3 mb-2">Job description</h3><p class="small st-pre st-card">{{ d.jobDescription }}</p> }
        }

        @case ('Applicants') {
          <div class="st-filters">
            <input type="search" class="form-control form-control-sm" placeholder="Search name, roll no, skill…" aria-label="Search applicants" [ngModel]="q()" (ngModelChange)="q.set($event)" />
            <select class="form-select form-select-sm" aria-label="Filter by status" [ngModel]="statusF()" (ngModelChange)="statusF.set($event)">
              <option value="">All statuses</option>@for (st of statuses; track st) { <option [value]="st">{{ st.replace('_', ' ').toLowerCase() }}</option> }</select>
            <select class="form-select form-select-sm" aria-label="Filter by branch" [ngModel]="branchF()" (ngModelChange)="branchF.set($event)">
              <option value="">All branches</option>@for (b of branchList(); track b) { <option [value]="b">{{ b }}</option> }</select>
            <span class="small st-muted ms-auto">{{ shownApps().length }} shown</span>
          </div>
          <div class="dr-bulk mb-2">
            <span class="small"><strong>{{ selected().size }}</strong> selected</span>
            @if (d.canEdit) {
              <button class="btn btn-sm btn-primary" [disabled]="!selected().size" (click)="bulk('SHORTLISTED')"><i class="bi bi-check2"></i> Shortlist</button>
              <button class="btn btn-sm btn-outline-secondary" [disabled]="!selected().size" (click)="bulk('WAITLISTED')">Waitlist</button>
              <button class="btn btn-sm btn-outline-danger" [disabled]="!selected().size" (click)="bulk('REJECTED')">Reject</button>
            }
            <button class="btn btn-sm btn-outline-secondary ms-auto" (click)="zip()"><i class="bi bi-file-zip"></i> {{ selected().size ? 'Resumes of selected' : 'All resumes' }}</button>
          </div>
          <div class="table-responsive st-card p-0">
            <table class="table table-sm align-middle small mb-0">
              <thead><tr>
                <th><input type="checkbox" class="form-check-input" aria-label="Select all shown" [checked]="allShownSelected()" (change)="toggleAll()" /></th>
                <th>Student</th><th>Branch</th><th>CGPA</th><th class="d-none d-lg-table-cell">Skills</th><th>Rounds</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (a of shownApps(); track a.applicationId) {
                  <tr>
                    <td><input type="checkbox" class="form-check-input" [attr.aria-label]="'Select ' + a.name" [checked]="selected().has(a.applicationId)" (change)="toggle(a.applicationId)" /></td>
                    <td><strong>{{ a.name }}</strong><br><span class="st-muted">{{ a.rollNumber }}</span></td>
                    <td>{{ a.branchCode ?? '-' }}<br><span class="st-muted">{{ a.batchYear }}</span></td>
                    <td>{{ a.cgpa ?? '-' }}@if (a.activeBacklogs) { <br><span class="text-danger">{{ a.activeBacklogs }} backlog</span> }</td>
                    <td class="d-none d-lg-table-cell">@for (k of a.skills.slice(0, 3); track k) { <span class="st-chip me-1 mb-1">{{ k }}</span> }</td>
                    <td>@for (r of d.rounds; track r.id) { @if (a.roundResults[r.id]; as res) { <span class="dr-pill dr-{{ res.toLowerCase() }}" [title]="r.name">R{{ r.seq }} {{ res.toLowerCase() }}</span> } }</td>
                    <td><pm-status [value]="a.offerStatus ?? a.status" /></td>
                    <td class="text-end text-nowrap">
                      @if (a.hasResume) { <button class="btn btn-sm btn-outline-secondary" (click)="room.openResume(d.driveId, a.applicationId)" title="Open resume" aria-label="Open resume"><i class="bi bi-file-earmark-person"></i></button> }
                      <button class="btn btn-sm btn-outline-secondary ms-1" (click)="profile.set(a)" aria-label="View profile"><i class="bi bi-eye"></i></button>
                    </td>
                  </tr>
                } @empty { <tr><td colspan="8"><pm-empty-state icon="bi-people" title="No applicants match" /></td></tr> }
              </tbody>
            </table>
          </div>
        }

        @case ('Rounds') {
          <div class="d-flex gap-2 flex-wrap mb-3">
            @for (r of d.rounds; track r.id) {
              <button class="btn btn-sm" [class.btn-primary]="round()?.id === r.id" [class.btn-outline-secondary]="round()?.id !== r.id" (click)="openRound(r)">
                R{{ r.seq }} · {{ r.name }} <span class="opacity-75">({{ r.status.toLowerCase() }})</span></button>
            } @empty { <span class="small st-muted">No rounds yet.</span> }
          </div>
          @if (round(); as r) {
            <div class="st-card mb-3 d-flex gap-2 align-items-center flex-wrap">
              <div class="flex-grow-1"><strong class="st-h me-2">{{ r.name }}</strong><pm-status [value]="r.status" />
                <div class="small st-muted">
                  @switch (r.status) {
                    @case ('PENDING') { Start the round when students are ready. Round 1 takes everyone shortlisted; later rounds take those who passed. }
                    @case ('ONGOING') { Give a scorecard per student, or set results in the table. Complete the round to move passed students on. }
                    @default { This round is complete. }
                  }</div></div>
              @if (d.canEdit && r.status === 'PENDING') { <button class="btn btn-sm btn-primary" (click)="start(r)">Start round</button> }
              @if (d.canEdit && r.status === 'ONGOING') {
                <button class="btn btn-sm btn-outline-secondary" (click)="saveTable(r)" [disabled]="!dirty().size">Save results</button>
                <button class="btn btn-sm btn-primary" (click)="complete(r)">Complete round</button>
              }
            </div>
            @for (row of results(); track row.applicationId) {
              <div class="st-row">
                <span class="flex-grow-1 min-w-0 small"><strong class="st-h">{{ row.studentName }}</strong><span class="st-muted ms-2">{{ row.rollNumber }} · {{ row.branchCode }}</span>
                  @if (row.remarks) { <br><span class="st-muted">{{ row.remarks }}</span> }
                  @for (c of cardsFor(row.applicationId); track c.id) {
                    <br><span class="st-muted"><i class="bi bi-person-check"></i> {{ c.interviewerName }}: {{ c.result.toLowerCase() }}
                      (T{{ c.technical ?? '-' }} C{{ c.communication ?? '-' }} A{{ c.attitude ?? '-' }})</span>
                  }</span>
                @if (r.status === 'ONGOING' && d.canEdit) {
                  <select class="form-select form-select-sm w-auto" [attr.aria-label]="'Result for ' + row.studentName" [ngModel]="edits()[row.applicationId] ?? row.result" (ngModelChange)="edit(row.applicationId, $event)">
                    <option value="PENDING">Pending</option>@for (v of resultValues; track v) { <option [value]="v">{{ v.toLowerCase() }}</option> }</select>
                  <button class="btn btn-sm btn-primary" (click)="openScore(row)"><i class="bi bi-clipboard-check"></i> Scorecard</button>
                } @else { <pm-status [value]="row.result" /> }
              </div>
            } @empty { <pm-empty-state icon="bi-hourglass" title="No participants yet" text="Participants appear when the round starts." /> }
          }
        }

        @case ('Offers') {
          @if (d.canEdit && pendingSelected().length) {
            <section class="st-card mb-3">
              <h3 class="h6 st-h">Release offers</h3>
              <p class="small st-muted">Selected students without an offer. Offers go through the college's placement policy; students accept or decline in their portal.</p>
              <div class="d-flex flex-wrap gap-2 mb-2">
                @for (a of pendingSelected(); track a.applicationId) {
                  <label class="dr-check"><input type="checkbox" class="form-check-input me-1" [checked]="offerPick().has(a.applicationId)" (change)="pickOffer(a.applicationId)" />{{ a.name }} <span class="st-muted">{{ a.rollNumber }}</span></label>
                }
              </div>
              <div class="row g-2 align-items-end">
                <div class="col-sm-3"><label class="form-label small fw-semibold" for="of-ctc">CTC (LPA)</label>
                  <input id="of-ctc" type="number" min="0" step="0.1" class="form-control form-control-sm" [(ngModel)]="offerCtc" /></div>
                <div class="col-sm-4"><label class="form-label small fw-semibold" for="of-role">Role</label>
                  <input id="of-role" class="form-control form-control-sm" [(ngModel)]="offerRole" /></div>
                <div class="col-sm-3"><label class="form-label small fw-semibold" for="of-join">Joining date</label>
                  <input id="of-join" type="date" class="form-control form-control-sm" [(ngModel)]="offerJoin" /></div>
                <div class="col-sm-2"><button class="btn btn-sm btn-primary w-100" [disabled]="!offerPick().size || busy()" (click)="releaseOffers()">Release {{ offerPick().size || '' }}</button></div>
              </div>
            </section>
          }
          @for (o of offers(); track o.id) {
            <div class="st-row">
              <span class="flex-grow-1 small"><strong class="st-h">{{ o.studentName }}</strong><span class="st-muted ms-2">{{ o.rollNumber }}</span><br>
                <span class="st-muted">{{ o.roleTitle }} · {{ o.ctcLpa | number: '1.0-2' }} LPA @if (o.joiningDate) { · joining {{ o.joiningDate | date: 'mediumDate' }} }</span></span>
              <pm-status [value]="o.status" />
              @if (o.offerLetterFileId) { <span class="small text-success"><i class="bi bi-file-earmark-check"></i> Letter uploaded</span> }
              @if (d.canEdit) {
                <label class="btn btn-sm btn-outline-secondary mb-0"><i class="bi bi-upload"></i> {{ o.offerLetterFileId ? 'Replace letter' : 'Upload letter' }}
                  <input type="file" accept=".pdf" class="visually-hidden" (change)="letter(o, $event)" /></label>
              }
            </div>
          } @empty { <pm-empty-state icon="bi-award" title="No offers yet" text="Offers can be released once students are selected." /> }
        }
      }
    }

    <pm-modal [open]="!!profile()" [title]="profile()?.name ?? ''" variant="drawer" (closed)="profile.set(null)">
      @if (profile(); as a) {
        <dl class="row small mb-2">
          <dt class="col-5 st-muted fw-normal">Roll number</dt><dd class="col-7">{{ a.rollNumber }}</dd>
          <dt class="col-5 st-muted fw-normal">Programme</dt><dd class="col-7">{{ a.degree ?? '-' }} · {{ a.branch ?? '-' }} · {{ a.batchYear }}</dd>
          <dt class="col-5 st-muted fw-normal">CGPA</dt><dd class="col-7">{{ a.cgpa ?? '-' }} · {{ a.activeBacklogs ?? 0 }} active backlog(s)</dd>
          @if (s()!.shareDetails) {
            <dt class="col-5 st-muted fw-normal">10th / 12th</dt><dd class="col-7">{{ a.tenthPercent ?? '-' }}% / {{ a.twelfthPercent ?? '-' }}%</dd>
            <dt class="col-5 st-muted fw-normal">E-mail</dt><dd class="col-7 text-break">{{ a.email }}</dd>
            <dt class="col-5 st-muted fw-normal">Phone</dt><dd class="col-7">{{ a.phone ?? '-' }}</dd>
          }
          <dt class="col-5 st-muted fw-normal">Status</dt><dd class="col-7"><pm-status [value]="a.status" /></dd>
        </dl>
        @if (!s()!.shareDetails) { <p class="small st-muted"><i class="bi bi-shield-lock"></i> The placement cell has not shared contact details and marks for this drive.</p> }
        <p class="mb-2">@for (k of a.skills; track k) { <span class="st-chip me-1 mb-1">{{ k }}</span> }</p>
        <div class="d-flex gap-2 flex-wrap">
          @if (a.linkedinUrl) { <a class="btn btn-sm btn-outline-secondary" [href]="a.linkedinUrl" target="_blank" rel="noopener"><i class="bi bi-linkedin"></i> LinkedIn</a> }
          @if (a.githubUrl) { <a class="btn btn-sm btn-outline-secondary" [href]="a.githubUrl" target="_blank" rel="noopener"><i class="bi bi-github"></i> GitHub</a> }
          @if (a.hasResume) { <button class="btn btn-sm btn-primary" (click)="room.openResume(s()!.driveId, a.applicationId)"><i class="bi bi-file-earmark-person"></i> Resume</button> }
        </div>
      }
    </pm-modal>

    <pm-modal [open]="!!scoring()" [title]="'Scorecard · ' + (scoring()?.studentName ?? '')" (closed)="scoring.set(null)">
      @if (scoring()) {
        @for (c of criteria; track c.key) {
          <div class="mb-3"><div class="small fw-semibold mb-1">{{ c.label }}</div>
            <div class="dr-rate" role="radiogroup" [attr.aria-label]="c.label">
              @for (n of [1, 2, 3, 4, 5]; track n) {
                <button type="button" role="radio" class="dr-rate-btn" [class.on]="score[c.key] === n" [attr.aria-checked]="score[c.key] === n" (click)="score[c.key] = n">{{ n }}</button>
              }
            </div></div>
        }
        <div class="small fw-semibold mb-1">Result</div>
        <div class="d-flex gap-2 flex-wrap mb-3" role="radiogroup" aria-label="Result">
          @for (v of resultValues; track v) {
            <button type="button" role="radio" class="btn btn-sm" [class.btn-primary]="score.result === v" [class.btn-outline-secondary]="score.result !== v"
              [attr.aria-checked]="score.result === v" (click)="score.result = v">{{ v.toLowerCase() }}</button>
          }
        </div>
        <label class="form-label small fw-semibold" for="sc-rem">Remarks</label>
        <textarea id="sc-rem" class="form-control" rows="3" maxlength="1000" [(ngModel)]="score.remarks" placeholder="Strengths, gaps, recommendation…"></textarea>
        @if (otherCards().length) {
          <div class="small st-muted mt-3">Other panel members</div>
          @for (c of otherCards(); track c.id) { <div class="small">{{ c.interviewerName }}: {{ c.result.toLowerCase() }} @if (c.remarks) { · "{{ c.remarks }}" }</div> }
        }
      }
      <div pmFooter><button class="btn btn-primary" [disabled]="!score.result || busy()" (click)="saveScore()">Save scorecard</button></div>
    </pm-modal>`,
  styles: [`
    .dr-head { display: flex; gap: 1rem; align-items: center; flex-wrap: wrap; }
    .dr-funnel { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: .75rem; }
    .dr-tile { padding: 1rem; display: grid; gap: .1rem; }
    .dr-tile-n { font-size: 1.6rem; font-weight: 800; color: var(--pm-heading); font-variant-numeric: tabular-nums; }
    .dr-tile-l { font-size: .75rem; color: var(--pm-muted); text-transform: capitalize; }
    .dr-seq { width: 2rem; height: 2rem; border-radius: 50%; display: grid; place-items: center; font-weight: 800; background: var(--pm-soft); color: var(--pm-link); flex-shrink: 0; }
    .dr-pill { display: inline-block; font-size: .68rem; font-weight: 700; padding: .1rem .45rem; border-radius: 999px; margin: 0 .2rem .2rem 0; text-transform: capitalize;
      background: var(--pm-soft); color: var(--pm-muted); white-space: nowrap; }
    .dr-pass { background: rgba(22,163,74,.14); color: #15803d; } .dr-fail { background: rgba(225,29,72,.12); color: #be123c; }
    .dr-hold { background: rgba(217,119,6,.15); color: #b45309; } .dr-absent { background: rgba(100,116,139,.18); color: #475569; }
    .dr-bulk { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; }
    .dr-check { font-size: .85rem; padding: .35rem .7rem; border-radius: 999px; border: 1px solid var(--pm-card-border); cursor: pointer; }
    .dr-rate { display: flex; gap: .4rem; }
    .dr-rate-btn { all: unset; box-sizing: border-box; width: 2.4rem; height: 2.4rem; border-radius: 10px; display: grid; place-items: center; cursor: pointer;
      font-weight: 700; background: var(--pm-soft); color: var(--pm-label); }
    .dr-rate-btn.on { background: var(--pm-link); color: #fff; }
    .dr-rate-btn:focus-visible { outline: 2px solid var(--pm-focus); }
    .table th { white-space: nowrap; }
  `],
})
export class DriveRoom implements OnInit {
  readonly id = input.required<string>();
  room = inject(DriveRoomService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  readonly tabs: Tab[] = ['Overview', 'Applicants', 'Rounds', 'Offers'];
  readonly statuses = ['APPLIED', 'SHORTLISTED', 'WAITLISTED', 'IN_PROCESS', 'SELECTED', 'REJECTED'];
  readonly resultValues = RESULTS;
  readonly criteria: { key: 'technical' | 'communication' | 'attitude'; label: string }[] = [
    { key: 'technical', label: 'Technical skill' }, { key: 'communication', label: 'Communication' }, { key: 'attitude', label: 'Attitude & fit' }];
  tab = signal<Tab>('Overview');
  s = signal<RoomSummary | null>(null);
  apps = signal<RoomApplicant[]>([]);
  offers = signal<Offer[]>([]);
  q = signal('');
  statusF = signal('');
  branchF = signal('');
  selected = signal<Set<string>>(new Set());
  profile = signal<RoomApplicant | null>(null);
  round = signal<RoomRound | null>(null);
  results = signal<RoundResultRow[]>([]);
  cards = signal<Scorecard[]>([]);
  edits = signal<Partial<Record<string, ResultValue>>>({});
  dirty = computed(() => new Set(Object.keys(this.edits())));
  scoring = signal<RoundResultRow | null>(null);
  score: { technical?: number; communication?: number; attitude?: number; result?: ResultValue; remarks?: string } = {};
  offerPick = signal<Set<string>>(new Set());
  offerCtc: number | null = null;
  offerRole = '';
  offerJoin: string | null = null;
  busy = signal(false);

  branchList = computed(() => [...new Set(this.apps().map(a => a.branchCode).filter((x): x is string => !!x))].sort());
  shownApps = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.apps().filter(a => (!this.statusF() || a.status === this.statusF()) && (!this.branchF() || a.branchCode === this.branchF())
      && (!q || a.name.toLowerCase().includes(q) || a.rollNumber.toLowerCase().includes(q) || a.skills.some(k => k.toLowerCase().includes(q))));
  });
  allShownSelected = computed(() => this.shownApps().length > 0 && this.shownApps().every(a => this.selected().has(a.applicationId)));
  pendingSelected = computed(() => this.apps().filter(a => a.status === 'SELECTED' && !a.offerStatus));
  funnel = computed(() => {
    const f = this.s()?.funnel ?? {};
    return [['APPLIED', 'Applied'], ['SHORTLISTED', 'Shortlisted'], ['WAITLISTED', 'Waitlisted'], ['IN_PROCESS', 'In process'], ['SELECTED', 'Selected'],
      ['REJECTED', 'Rejected'], ['OFFERED', 'Offers']].map(([key, label]) => ({ key, label, value: f[key] ?? 0 }));
  });
  otherCards = computed(() => this.cards().filter(c => c.applicationId === this.scoring()?.applicationId && !c.mine));

  ngOnInit(): void { this.reload(); }

  reload(): void {
    forkJoin({ s: this.room.summary(this.id()), a: this.room.applicants(this.id()), o: this.room.offers(this.id()) }).subscribe(r => {
      this.s.set(r.s); this.apps.set(r.a); this.offers.set(r.o);
      if (this.offerCtc === null) { this.offerCtc = r.s.ctcLpa ?? null; this.offerRole = r.s.roleTitle; }
      const cur = this.round();
      if (cur) { const fresh = r.s.rounds.find(x => x.id === cur.id); if (fresh) { this.round.set(fresh); this.loadRound(fresh); } }
    });
  }
  openTab(t: Tab): void {
    this.tab.set(t);
    if (t === 'Rounds' && !this.round()) { const rs = this.s()?.rounds ?? []; const r = rs.find(x => x.status === 'ONGOING') ?? rs[0]; if (r) this.openRound(r); }
  }
  resultKeys(r: RoomRound): string[] { return Object.keys(r.resultCounts ?? {}).filter(k => r.resultCounts[k] > 0); }

  // ---------------------------------------------------------------- applicants
  toggle(id: string): void { this.selected.update(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; }); }
  toggleAll(): void {
    const all = this.allShownSelected();
    this.selected.update(s => { const n = new Set(s); this.shownApps().forEach(a => all ? n.delete(a.applicationId) : n.add(a.applicationId)); return n; });
  }
  async bulk(status: 'SHORTLISTED' | 'WAITLISTED' | 'REJECTED'): Promise<void> {
    const ids = [...this.selected()];
    const r = await this.confirm.ask({ title: `${status.toLowerCase().replace(/^\w/, c => c.toUpperCase())} ${ids.length} student(s)?`,
      message: 'Students and the placement cell see the new status immediately.', tone: status === 'REJECTED' ? 'danger' : 'primary' });
    if (!r.ok) return;
    this.room.setStatus(this.id(), ids, status).subscribe(res => {
      this.toast.success(`${res.updated} updated${res.skipped.length ? `, ${res.skipped.length} skipped` : ''}`);
      this.selected.set(new Set()); this.reload();
    });
  }
  zip(): void { this.room.downloadResumes(this.id(), [...this.selected()]).subscribe({ error: (e: AppError) => this.toast.error(e.message) }); }

  // ---------------------------------------------------------------- rounds
  openRound(r: RoomRound): void { this.tab.set('Rounds'); this.round.set(r); this.edits.set({}); this.loadRound(r); }
  private loadRound(r: RoomRound): void {
    this.room.results(this.id(), r.id).subscribe(x => this.results.set(x));
    this.room.scorecards(this.id(), r.id).subscribe(x => this.cards.set(x));
  }
  cardsFor(appId: string): Scorecard[] { return this.cards().filter(c => c.applicationId === appId); }
  async start(r: RoomRound): Promise<void> {
    const ok = await this.confirm.ask({ title: `Start ${r.name}?`, message: r.seq === 1 ? 'All shortlisted students join this round.' : 'Students who passed the previous round join.' });
    if (ok.ok) this.room.startRound(this.id(), r.id).subscribe(() => { this.toast.success('Round started'); this.reload(); });
  }
  async complete(r: RoomRound): Promise<void> {
    const ok = await this.confirm.ask({ title: `Complete ${r.name}?`, message: 'Passed students move to the next round (or are selected after the last round); the others are rejected.' });
    if (ok.ok) this.room.completeRound(this.id(), r.id).subscribe(() => { this.toast.success('Round completed'); this.reload(); });
  }
  edit(appId: string, v: ResultValue): void { this.edits.update(e => ({ ...e, [appId]: v })); }
  saveTable(r: RoomRound): void {
    const results = Object.entries(this.edits()).map(([applicationId, result]) => ({ applicationId, result: result as ResultValue }));
    this.room.saveResults(this.id(), r.id, results).subscribe(x => { this.results.set(x); this.edits.set({}); this.toast.success('Results saved'); this.reload(); });
  }
  openScore(row: RoundResultRow): void {
    const mine = this.cards().find(c => c.applicationId === row.applicationId && c.mine);
    this.score = mine ? { technical: mine.technical, communication: mine.communication, attitude: mine.attitude, result: mine.result, remarks: mine.remarks ?? '' } : {};
    this.scoring.set(row);
  }
  saveScore(): void {
    const row = this.scoring(), r = this.round();
    if (!row || !r || !this.score.result) return;
    this.busy.set(true);
    this.room.saveScorecard(this.id(), r.id, row.applicationId, { ...this.score, result: this.score.result }).subscribe({
      next: () => { this.busy.set(false); this.scoring.set(null); this.toast.success('Scorecard saved'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  // ---------------------------------------------------------------- offers
  pickOffer(id: string): void { this.offerPick.update(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; }); }
  async releaseOffers(): Promise<void> {
    const ids = [...this.offerPick()];
    const ok = await this.confirm.ask({ title: `Release ${ids.length} offer(s)?`, message: `${this.offerRole} · ${this.offerCtc} LPA. Students are notified and can accept or decline.` });
    if (!ok.ok) return;
    this.busy.set(true);
    this.room.makeOffers(this.id(), { applicationIds: ids, ctcLpa: this.offerCtc, roleTitle: this.offerRole, joiningDate: this.offerJoin || null }).subscribe({
      next: r => {
        this.busy.set(false); this.offerPick.set(new Set());
        if (r.created) this.toast.success(`${r.created} offer(s) released`);
        r.errors.forEach(e => this.toast.error(e));
        this.reload();
      },
      error: () => this.busy.set(false),
    });
  }
  letter(o: Offer, ev: Event): void {
    const el = ev.target as HTMLInputElement; const f = el.files?.[0]; el.value = '';
    if (f) this.room.uploadLetter(this.id(), o.id, f).subscribe(() => { this.toast.success('Offer letter uploaded'); this.reload(); });
  }
  report(format: 'xlsx' | 'pdf'): void { this.room.downloadReport(this.id(), format).subscribe(); }
}
