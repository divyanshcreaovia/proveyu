import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CollegeService } from '../../../../services/college.service';
import { DriveRoomService, RecruiterAccess, Scorecard } from '../../../../services/drive-room.service';
import { RoundService } from '../../../../services/round.service';
import { ToastService } from '../../../../services/toast.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog';
import { Drive, Round } from '../../../../core/models/college.models';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { EmptyState } from '../../../../shared/ui/states';
import { AppError } from '../../../../core/api';

/**
 * TPO: give company HR / panel members access to this drive's Drive Room, choose whether student contact details are
 * shared, and read the panel scorecards they leave.
 */
@Component({
  selector: 'pm-company-access-tab',
  imports: [DatePipe, ReactiveFormsModule, StatusBadge, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-lg-8">
        <div class="table-responsive bg-body border rounded-3">
          <table class="table table-sm align-middle small mb-0">
            <thead><tr><th>Company person</th><th>Access</th><th class="d-none d-md-table-cell">Last active</th><th>Contact details</th><th></th></tr></thead>
            <tbody>
              @for (a of rows(); track a.id) {
                <tr>
                  <td><strong>{{ a.fullName }}</strong>@if (a.designation) { <span class="text-body-secondary"> · {{ a.designation }}</span> }<br>
                    <span class="text-body-secondary">{{ a.email }}</span>
                    @if (devLinks()[a.id]; as link) { <div class="mt-1"><span class="badge text-bg-warning">Dev</span> <a [href]="link" target="_blank" rel="noopener">invitation link</a></div> }</td>
                  <td><pm-status [value]="a.status === 'INVITED' ? 'PENDING' : a.status" />
                    <div class="text-body-secondary">{{ a.status === 'INVITED' ? 'Invitation sent' : a.status === 'ACTIVE' ? 'Joined' : 'Revoked' }}</div></td>
                  <td class="d-none d-md-table-cell">{{ a.lastAccessAt ? (a.lastAccessAt | date: 'short') : '-' }}</td>
                  <td>
                    <div class="form-check form-switch m-0">
                      <input class="form-check-input" type="checkbox" role="switch" [id]="'share-' + a.id" [checked]="a.shareDetails"
                        [disabled]="!college.canWrite() || a.status === 'REVOKED'" (change)="toggleShare(a)" />
                      <label class="form-check-label" [for]="'share-' + a.id">{{ a.shareDetails ? 'Shared' : 'Hidden' }}</label>
                    </div>
                  </td>
                  <td class="text-end text-nowrap">
                    @if (college.canWrite() && a.status === 'INVITED') { <button class="btn btn-sm btn-outline-secondary me-1" (click)="resend(a)">Resend</button> }
                    @if (college.canWrite() && a.status !== 'REVOKED') { <button class="btn btn-sm btn-outline-danger" (click)="revoke(a)">Revoke</button> }
                  </td>
                </tr>
              } @empty { <tr><td colspan="5"><pm-empty-state icon="bi-person-workspace" title="No company access yet"
                text="Invite the company's HR so they can shortlist, record round results and offers themselves." /></td></tr> }
            </tbody>
          </table>
        </div>

        <h3 class="h6 fw-bold mt-4 mb-2">Panel scorecards</h3>
        <div class="d-flex gap-2 mb-2 flex-wrap">
          @for (r of rounds(); track r.id) {
            <button class="btn btn-sm" [class.btn-primary]="round()?.id === r.id" [class.btn-outline-secondary]="round()?.id !== r.id" (click)="pickRound(r)">
              R{{ r.seq }} · {{ r.name }}</button>
          } @empty { <span class="small text-body-secondary">This drive has no rounds yet.</span> }
        </div>
        @if (round()) {
          @for (s of cards(); track s.id) {
            <div class="bg-body border rounded-3 p-2 px-3 mb-2 small d-flex gap-3 align-items-center flex-wrap">
              <span class="fw-semibold">{{ names()[s.applicationId] ?? 'Student' }}</span>
              <pm-status [value]="s.result" />
              <span class="text-body-secondary">Tech {{ s.technical ?? '-' }}/5 · Comm {{ s.communication ?? '-' }}/5 · Attitude {{ s.attitude ?? '-' }}/5</span>
              @if (s.remarks) { <span class="flex-grow-1">"{{ s.remarks }}"</span> }
              <span class="text-body-secondary ms-auto">{{ s.interviewerName }} · {{ s.updatedAt | date: 'short' }}</span>
            </div>
          } @empty { <p class="small text-body-secondary">No scorecards for this round yet.</p> }
        }
      </div>

      <div class="col-lg-4">
        @if (college.canWrite()) {
          <form class="bg-body border rounded-3 p-3" [formGroup]="form" (ngSubmit)="invite()" novalidate>
            <h3 class="h6 fw-bold">Invite company HR / panel</h3>
            <p class="small text-body-secondary">They get access to this drive only: applicants, resumes, shortlisting, round results, scorecards and offers. Every change shows in your portal and in the audit log.</p>
            <label class="form-label small fw-semibold" for="ca-name">Full name</label>
            <input id="ca-name" class="form-control form-control-sm mb-2" formControlName="fullName" />
            <label class="form-label small fw-semibold" for="ca-email">Work e-mail</label>
            <input id="ca-email" type="email" class="form-control form-control-sm mb-2" formControlName="email" [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
            <label class="form-label small fw-semibold" for="ca-desig">Designation (optional)</label>
            <input id="ca-desig" class="form-control form-control-sm mb-2" formControlName="designation" placeholder="HR Manager, Technical panel…" />
            <div class="form-check form-switch mb-3">
              <input class="form-check-input" type="checkbox" role="switch" id="ca-share" formControlName="shareDetails" />
              <label class="form-check-label small" for="ca-share">Share student phone, e-mail and 10th/12th marks</label>
            </div>
            <button class="btn btn-sm btn-primary w-100" [disabled]="busy()">
              @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Send invitation</button>
          </form>
        }
      </div>
    </div>`,
})
export class CompanyAccessTab implements OnInit {
  readonly drive = input.required<Drive>();
  college = inject(CollegeService);
  private room = inject(DriveRoomService);
  private roundsApi = inject(RoundService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  rows = signal<RecruiterAccess[]>([]);
  devLinks = signal<Record<string, string>>({});
  rounds = signal<Round[]>([]);
  round = signal<Round | null>(null);
  cards = signal<Scorecard[]>([]);
  names = signal<Partial<Record<string, string>>>({});
  busy = signal(false);
  form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    designation: [''],
    shareDetails: [false],
  });

  ngOnInit(): void {
    this.load();
    this.roundsApi.list(this.drive().id).subscribe(r => { this.rounds.set(r); if (r.length) this.pickRound(r.find(x => x.status !== 'PENDING') ?? r[0]); });
  }
  load(): void { this.room.access(this.drive().id).subscribe(r => this.rows.set(r)); }

  invite(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    const v = this.form.getRawValue();
    this.room.invite(this.drive().id, { ...v, email: v.email.trim(), fullName: v.fullName.trim() }).subscribe({
      next: a => { this.busy.set(false); this.toast.success(`Invitation sent to ${a.email}`); this.remember(a); this.form.reset({ shareDetails: false }); this.load(); },
      error: (e: AppError) => { this.busy.set(false); this.toast.error(e.message); },
    });
  }
  resend(a: RecruiterAccess): void { this.room.resend(this.drive().id, a.id).subscribe(x => { this.toast.success('Invitation sent again'); this.remember(x); this.load(); }); }
  toggleShare(a: RecruiterAccess): void {
    this.room.setShare(this.drive().id, a.id, !a.shareDetails).subscribe(() => { this.toast.success(a.shareDetails ? 'Student details hidden' : 'Student details shared'); this.load(); });
  }
  async revoke(a: RecruiterAccess): Promise<void> {
    const r = await this.confirm.ask({ title: `Revoke access for ${a.fullName}?`, message: 'They lose access to this drive immediately. Their earlier changes stay.', tone: 'danger', confirmText: 'Revoke' });
    if (r.ok) this.room.revoke(this.drive().id, a.id).subscribe(() => { this.toast.success('Access revoked'); this.load(); });
  }
  pickRound(r: Round): void {
    this.round.set(r);
    this.room.collegeScorecards(r.id).subscribe(c => this.cards.set(c));
    this.roundsApi.results(r.id).subscribe(rows => this.names.set(Object.fromEntries(rows.map(x => [x.applicationId, `${x.studentName} (${x.rollNumber})`]))));
  }
  /** Dev mode only: the API returns the raw token so the link can be opened without e-mail. */
  private remember(a: RecruiterAccess): void {
    if (a.devInviteToken) this.devLinks.update(m => ({ ...m, [a.id]: `${location.origin}/company/accept?token=${a.devInviteToken}` }));
  }
}
