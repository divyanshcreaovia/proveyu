import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TeamService } from '../../../services/team.service';
import { CollegeService } from '../../../services/college.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Invite, Member, MemberRole, RoleMatrixRow } from '../../../core/models/college.models';
import { Modal } from '../../../shared/ui/modal';
import { StatusBadge } from '../../../shared/ui/status-badge';

@Component({
  selector: 'pm-team',
  imports: [FormsModule, DatePipe, Modal, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row g-3">
      <div class="col-xl-8">
        <section class="pm-tm-card">
          <div class="d-flex justify-content-between align-items-center mb-2"><h2 class="h6 m-0">Members</h2>
            @if (college.isAdmin()) { <button class="btn btn-sm btn-primary" (click)="openInvite()"><i class="bi bi-person-plus"></i> Invite member</button> }</div>
          <div class="table-responsive"><table class="table table-sm align-middle small mb-0">
            <thead><tr><th>Name</th><th>Role</th><th class="d-none d-md-table-cell">Branch scope</th><th class="d-none d-md-table-cell">Last login</th><th>Status</th><th></th></tr></thead>
            <tbody>@for (m of members(); track m.id) {
              <tr><td><strong>{{ m.fullName }}</strong><br><span class="text-body-secondary">{{ m.email }} · {{ m.designation ?? '' }}</span></td>
                <td>{{ roleLabel(m.role) }}</td>
                <td class="d-none d-md-table-cell">{{ m.branchNames.length ? m.branchNames.join(', ') : 'All branches' }}</td>
                <td class="d-none d-md-table-cell">{{ m.lastLoginAt ? (m.lastLoginAt | date: 'short') : 'Never' }}</td>
                <td><pm-status [value]="m.status" /></td>
                <td class="text-end">@if (college.isAdmin() && m.userId !== college.me()?.userId) {
                  <div class="btn-group btn-group-sm">
                    <button class="btn btn-outline-secondary" (click)="openEdit(m)" aria-label="Edit role"><i class="bi bi-pencil"></i></button>
                    @if (m.status === 'ACTIVE') { <button class="btn btn-outline-danger" (click)="revoke(m)">Revoke</button> }
                    @else { <button class="btn btn-outline-success" (click)="restore(m)">Restore</button> }
                  </div> }</td></tr>
            }</tbody></table></div>
        </section>
        @if (college.isAdmin()) {
          <section class="pm-tm-card mt-3"><h2 class="h6">Pending invitations</h2>
            @for (i of invites(); track i.id) {
              <div class="d-flex justify-content-between align-items-center small border-bottom py-2 gap-2 flex-wrap">
                <span><strong>{{ i.fullName }}</strong> · {{ i.email }} · {{ roleLabel(i.role) }}<br><span class="text-body-secondary">expires {{ i.expiresAt | date: 'medium' }}</span></span>
                <div class="btn-group btn-group-sm"><button class="btn btn-outline-secondary" (click)="resend(i)">Resend</button>
                  <button class="btn btn-outline-danger" (click)="revokeInvite(i)">Revoke</button></div>
              </div>
            } @empty { <p class="small text-body-secondary mb-0">No pending invitations.</p> }
          </section>
        }
      </div>
      <div class="col-xl-4"><section class="pm-tm-card"><h2 class="h6">Role matrix</h2>
        <table class="table table-sm small mb-0"><thead><tr><th>Permission</th><th class="text-center">TPO</th><th class="text-center">Coord.</th><th class="text-center">Viewer</th></tr></thead>
          <tbody>@for (r of matrix(); track r.permission) { <tr><td>{{ r.permission }}</td>
            @for (v of [r.admin, r.coordinator, r.viewer]; track $index) { <td class="text-center">@if (v) { <i class="bi bi-check-lg text-success" aria-label="yes"></i> } @else { <i class="bi bi-dash text-body-secondary" aria-label="no"></i> }</td> }</tr> }</tbody></table>
      </section></div>
    </div>

    <pm-modal [open]="dlg() !== null" [title]="dlg() === 'invite' ? 'Invite team member' : 'Edit member'" (closed)="dlg.set(null)">
      <form id="tm-form" class="row g-3" (ngSubmit)="submit()">
        @if (dlg() === 'invite') {
          <div class="col-md-6"><label class="form-label small fw-semibold" for="tm-name">Full name *</label><input id="tm-name" class="form-control" [(ngModel)]="m.fullName" name="n" required /></div>
          <div class="col-md-6"><label class="form-label small fw-semibold" for="tm-email">E-mail *</label><input id="tm-email" type="email" class="form-control" [(ngModel)]="m.email" name="e" required /></div>
        }
        <div class="col-md-6"><label class="form-label small fw-semibold" for="tm-role">Role *</label>
          <select id="tm-role" class="form-select" [(ngModel)]="m.role" name="r">
            <option value="COLLEGE_ADMIN">TPO / Admin</option><option value="COLLEGE_COORDINATOR">Coordinator</option><option value="COLLEGE_VIEWER">Viewer</option></select></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="tm-des">Designation</label><input id="tm-des" class="form-control" [(ngModel)]="m.designation" name="d" /></div>
        @if (m.role !== 'COLLEGE_ADMIN') {
          <fieldset class="col-12"><legend class="form-label small fw-semibold">Branch scope <span class="fw-normal text-body-secondary">(none = all branches)</span></legend>
            @for (b of college.branches(); track b.id) {
              <div class="form-check form-check-inline"><input class="form-check-input" type="checkbox" [id]="'tb' + b.id" [checked]="m.branchIds.includes(b.id)" (change)="toggleBranch(b.id)" />
                <label class="form-check-label small" [for]="'tb' + b.id">{{ b.code }}</label></div>
            }</fieldset>
        }
        @if (devLink()) { <div class="col-12"><div class="alert alert-secondary small mb-0">Dev mode invite link: <a [href]="devLink()">{{ devLink() }}</a></div></div> }
      </form>
      <div pmFooter><button class="btn btn-outline-secondary" (click)="dlg.set(null)">Close</button>
        @if (!devLink()) { <button type="submit" form="tm-form" class="btn btn-primary">{{ dlg() === 'invite' ? 'Send invite' : 'Save' }}</button> }</div>
    </pm-modal>`,
  styles: [`.pm-tm-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }`],
})
export class Team implements OnInit {
  private svc = inject(TeamService);
  college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  members = signal<Member[]>([]);
  invites = signal<Invite[]>([]);
  matrix = signal<RoleMatrixRow[]>([]);
  dlg = signal<'invite' | 'edit' | null>(null);
  devLink = signal('');
  editingId = '';
  m = { fullName: '', email: '', role: 'COLLEGE_COORDINATOR' as MemberRole, designation: '', branchIds: [] as string[] };

  ngOnInit(): void { this.load(); this.svc.matrix().subscribe(m => this.matrix.set(m)); }
  load(): void {
    this.svc.members().subscribe(m => this.members.set(m));
    if (this.college.isAdmin()) this.svc.invites().subscribe(i => this.invites.set(i));
  }
  roleLabel(r: MemberRole): string { return ({ COLLEGE_ADMIN: 'TPO / Admin', COLLEGE_COORDINATOR: 'Coordinator', COLLEGE_VIEWER: 'Viewer' })[r]; }
  openInvite(): void { this.m = { fullName: '', email: '', role: 'COLLEGE_COORDINATOR', designation: '', branchIds: [] }; this.devLink.set(''); this.dlg.set('invite'); }
  openEdit(x: Member): void { this.editingId = x.id; this.m = { fullName: x.fullName, email: x.email, role: x.role, designation: x.designation ?? '', branchIds: [...x.branchScope] }; this.devLink.set(''); this.dlg.set('edit'); }
  toggleBranch(id: string): void { this.m.branchIds = this.m.branchIds.includes(id) ? this.m.branchIds.filter(x => x !== id) : [...this.m.branchIds, id]; }
  submit(): void {
    const body = { role: this.m.role, designation: this.m.designation || undefined, branchIds: this.m.role === 'COLLEGE_ADMIN' ? [] : this.m.branchIds };
    if (this.dlg() === 'invite') {
      if (!this.m.fullName || !this.m.email) { this.toast.warning('Name and e-mail are required'); return; }
      this.svc.invite({ ...body, fullName: this.m.fullName, email: this.m.email }).subscribe(i => {
        this.toast.success('Invitation sent'); this.load();
        if (i.devInviteToken) this.devLink.set(`${location.origin}/college/accept-invite?token=${i.devInviteToken}`); else this.dlg.set(null);
      });
    } else {
      this.svc.update(this.editingId, body).subscribe(() => { this.toast.success('Member updated. They must sign in again.'); this.dlg.set(null); this.load(); });
    }
  }
  async revoke(x: Member): Promise<void> {
    const r = await this.confirm.ask({ title: `Revoke access for ${x.fullName}?`, message: 'They will be signed out on their next request.', tone: 'danger', confirmText: 'Revoke' });
    if (r.ok) this.svc.revoke(x.id).subscribe(() => { this.toast.success('Access revoked'); this.load(); });
  }
  restore(x: Member): void { this.svc.restore(x.id).subscribe(() => { this.toast.success('Access restored'); this.load(); }); }
  resend(i: Invite): void { this.svc.resend(i.id).subscribe(() => this.toast.success('Invitation re-sent')); }
  revokeInvite(i: Invite): void { this.svc.revokeInvite(i.id).subscribe(() => { this.toast.success('Invitation revoked'); this.load(); }); }
}
