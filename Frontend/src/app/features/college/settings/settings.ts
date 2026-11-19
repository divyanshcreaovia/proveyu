import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SettingsService } from '../services/settings.service';
import { CollegeService } from '../services/college.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog';
import { Batch, Branch, CollegeProfile, EmailTemplate, Policy, Tier } from '../../../core/models/college.models';
import { FileUpload } from '../../../shared/ui/file-upload';
import { Skeleton } from '../../../shared/ui/states';

@Component({
  selector: 'pm-college-settings',
  imports: [FormsModule, FileUpload, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="nav nav-tabs small mb-3 flex-nowrap overflow-auto" role="tablist">
      @for (t of tabs; track t) { <li class="nav-item"><button class="nav-link text-nowrap" role="tab" [class.active]="tab() === t" (click)="tab.set(t)">{{ t }}</button></li> }
    </ul>
    <section class="pm-st-card">
      @switch (tab()) {
        @case ('Profile & branding') {
          @if (!profile()) { <pm-skeleton /> } @else {
            <form class="row g-3" (ngSubmit)="saveProfile()">
              <div class="col-md-8"><label class="form-label small fw-semibold" for="st-name">College name</label><input id="st-name" class="form-control" [(ngModel)]="p.collegeName" name="n" /></div>
              <div class="col-md-4"><label class="form-label small fw-semibold" for="st-code">AICTE / UGC code</label><input id="st-code" class="form-control" [value]="profile()!.aicteCode" disabled /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="st-uni">University</label><input id="st-uni" class="form-control" [(ngModel)]="p.university" name="u" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="st-city">City</label><input id="st-city" class="form-control" [(ngModel)]="p.city" name="c" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="st-state">State</label><input id="st-state" class="form-control" [(ngModel)]="p.state" name="s" /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="st-web">Website</label><input id="st-web" class="form-control" [(ngModel)]="p.website" name="w" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="st-ay">Academic year</label><input id="st-ay" class="form-control" [(ngModel)]="p.currentAcademicYear" name="ay" placeholder="2025-26" /></div>
              <div class="col-md-3"><label class="form-label small fw-semibold" for="st-color">Brand colour</label><input id="st-color" type="color" class="form-control form-control-color" [(ngModel)]="p.brandColor" name="bc" /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="st-tpo">TPO name</label><input id="st-tpo" class="form-control" [(ngModel)]="p.tpoName" name="tn" /></div>
              <div class="col-md-6"><label class="form-label small fw-semibold" for="st-tph">TPO phone</label><input id="st-tph" class="form-control" [(ngModel)]="p.tpoPhone" name="tp" /></div>
              <div class="col-12 text-end"><button class="btn btn-primary btn-sm">Save profile</button></div>
            </form>
            <hr><h3 class="h6">Logo</h3>
            <div class="d-flex gap-3 align-items-center flex-wrap">
              @if (logo()) { <img [src]="logo()" alt="College logo" class="pm-st-logo" /> }
              <div class="flex-grow-1"><pm-file-upload accept=".png,.jpg,.jpeg" [maxMb]="2" label="Upload logo (PNG/JPG, max 2 MB)" (fileSelected)="uploadLogo($event)" /></div>
            </div>
          }
        }
        @case ('Placement policy') {
          @if (policy(); as pol) {
            <form (ngSubmit)="savePolicy()" class="row g-3">
              <fieldset class="col-12"><legend class="form-label small fw-semibold">Policy mode</legend>
                @for (o of policyModes; track o.v) {
                  <div class="form-check"><input class="form-check-input" type="radio" [id]="'pm' + o.v" name="mode" [value]="o.v" [(ngModel)]="pol.policyMode" />
                    <label class="form-check-label small" [for]="'pm' + o.v"><strong>{{ o.l }}</strong> - {{ o.d }}</label></div>
                }</fieldset>
              <div class="col-md-4"><label class="form-label small fw-semibold" for="po-max">Max active applications per student</label>
                <input id="po-max" type="number" min="1" class="form-control" [(ngModel)]="pol.maxApplications" name="ma" placeholder="Unlimited" /></div>
              <div class="col-md-4"><label class="form-label small fw-semibold" for="po-off">Max offers per student</label>
                <input id="po-off" type="number" min="1" max="10" class="form-control" [(ngModel)]="pol.maxOffers" name="mo" /></div>
              <div class="col-md-4"><label class="form-label small fw-semibold" for="po-hike">Min CTC hike for upgrade (%)</label>
                <input id="po-hike" type="number" min="0" class="form-control" [(ngModel)]="pol.upgradeMinHikePercent" name="h" /></div>
              <div class="col-12 form-check ms-2"><input id="po-block" type="checkbox" class="form-check-input" [(ngModel)]="pol.blockAfterAccept" name="b" />
                <label for="po-block" class="form-check-label small">Block further applications after a student accepts an offer (one-offer mode)</label></div>
              <div class="col-md-8"><label class="form-label small fw-semibold" for="po-offc">Off-campus jobs (posted by ProveYu)</label>
                <select id="po-offc" class="form-select" [(ngModel)]="pol.offCampusPolicy" name="oc">
                  <option value="ALLOW">Students may apply</option>
                  <option value="BLOCK_PLACED">Placed students may not apply</option>
                  <option value="DISABLED">Turned off for our students</option>
                </select>
                <div class="form-text">You see your students' off-campus applications and offers under Off-campus.</div></div>
              <div class="col-12 text-end"><button class="btn btn-primary btn-sm">Save policy</button></div>
            </form>
          }
        }
        @case ('Branches & batches') {
          <div class="row g-4">
            <div class="col-lg-6"><h3 class="h6">Branches</h3>
              @for (b of branches(); track b.id) {
                <div class="d-flex align-items-center gap-2 border-bottom py-2 small"><span class="badge text-bg-secondary">{{ b.code }}</span><span class="flex-grow-1">{{ b.name }}</span>
                  <span class="text-body-secondary">{{ b.students }} students</span>
                  <button class="btn btn-sm btn-link text-danger" [disabled]="b.students > 0" (click)="deleteBranch(b)" aria-label="Delete branch"><i class="bi bi-trash"></i></button></div>
              }
              <form class="d-flex gap-2 mt-2" (ngSubmit)="addBranch()">
                <input class="form-control form-control-sm" style="max-width:110px" placeholder="Code" aria-label="Branch code" [(ngModel)]="nb.code" name="bc" />
                <input class="form-control form-control-sm" placeholder="Branch name" aria-label="Branch name" [(ngModel)]="nb.name" name="bn" />
                <button class="btn btn-sm btn-outline-primary" [disabled]="!nb.code || !nb.name">Add</button></form>
            </div>
            <div class="col-lg-6"><h3 class="h6">Batches</h3>
              @for (b of batches(); track b.id) {
                <div class="d-flex align-items-center gap-2 border-bottom py-2 small"><strong>{{ b.gradYear }}</strong><span class="flex-grow-1">{{ b.label }}</span>
                  <span class="text-body-secondary">{{ b.students }} students</span>
                  <div class="form-check form-switch m-0"><input class="form-check-input" type="checkbox" [checked]="b.active" (change)="toggleBatch(b)" [attr.aria-label]="'Active ' + b.gradYear" /></div>
                  <button class="btn btn-sm btn-link text-danger" (click)="deleteBatch(b)" aria-label="Delete batch"><i class="bi bi-trash"></i></button></div>
              }
              <form class="d-flex gap-2 mt-2" (ngSubmit)="addBatch()">
                <input type="number" class="form-control form-control-sm" style="max-width:110px" placeholder="Year" aria-label="Graduation year" [(ngModel)]="nbt.gradYear" name="by" />
                <input class="form-control form-control-sm" placeholder="Label" aria-label="Batch label" [(ngModel)]="nbt.label" name="bl" />
                <button class="btn btn-sm btn-outline-primary" [disabled]="!nbt.gradYear">Add</button></form>
            </div>
          </div>
        }
        @case ('Tiers') {
          <p class="small text-body-secondary">Drives are tiered by CTC (higher rank = better). The dream policy lets placed students apply only to higher tiers.</p>
          <div class="table-responsive"><table class="table table-sm align-middle small">
            <thead><tr><th>Name</th><th>Rank</th><th>Min CTC (LPA)</th><th></th></tr></thead>
            <tbody>@for (t of tiers(); track $index) {
              <tr><td><input class="form-control form-control-sm" aria-label="Tier name" [(ngModel)]="t.name" /></td>
                <td><input type="number" min="1" class="form-control form-control-sm" style="width:80px" aria-label="Rank" [(ngModel)]="t.tierRank" /></td>
                <td><input type="number" min="0" step="0.5" class="form-control form-control-sm" style="width:110px" aria-label="Min CTC" [(ngModel)]="t.minCtcLpa" /></td>
                <td><button class="btn btn-sm btn-link text-danger" (click)="tiers.set(removeAt(tiers(), $index))" aria-label="Remove tier"><i class="bi bi-x-lg"></i></button></td></tr>
            }</tbody></table></div>
          <div class="d-flex justify-content-between"><button class="btn btn-sm btn-outline-secondary" (click)="addTier()">Add tier</button>
            <button class="btn btn-sm btn-primary" (click)="saveTiers()">Save tiers</button></div>
        }
        @case ('E-mail templates') {
          <p class="small text-body-secondary">Placeholders: <code>{{ '{{studentName}}' }}</code>, <code>{{ '{{companyName}}' }}</code>, <code>{{ '{{roleTitle}}' }}</code>, <code>{{ '{{ctc}}' }}</code>,
            <code>{{ '{{deadline}}' }}</code>, <code>{{ '{{when}}' }}</code>, <code>{{ '{{where}}' }}</code>, <code>{{ '{{roundName}}' }}</code>, <code>{{ '{{collegeName}}' }}</code>.</p>
          @for (t of templates(); track t.key) {
            <details class="border rounded-3 p-2 mb-2"><summary class="small fw-semibold">{{ t.key }} @if (t.customized) { <span class="badge text-bg-info">custom</span> }</summary>
              <label class="form-label small mt-2" [for]="'tsub' + t.key">Subject</label><input [id]="'tsub' + t.key" class="form-control form-control-sm mb-2" [(ngModel)]="t.subject" />
              <label class="form-label small" [for]="'tbody' + t.key">Body (HTML)</label><textarea [id]="'tbody' + t.key" class="form-control form-control-sm font-monospace mb-2" rows="5" [(ngModel)]="t.body"></textarea>
              <div class="d-flex gap-2 justify-content-end">@if (t.customized) { <button class="btn btn-sm btn-outline-secondary" (click)="resetTemplate(t)">Reset to default</button> }
                <button class="btn btn-sm btn-primary" (click)="saveTemplate(t)">Save</button></div></details>
          }
        }
      }
    </section>`,
  styles: [`.pm-st-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; max-width: 1100px; }
    .pm-st-logo { width: 96px; height: 96px; object-fit: contain; border: 1px solid var(--bs-border-color); border-radius: .6rem; }`],
})
export class CollegeSettings implements OnInit {
  private svc = inject(SettingsService);
  private college = inject(CollegeService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  tabs = ['Profile & branding', 'Placement policy', 'Branches & batches', 'Tiers', 'E-mail templates'];
  tab = signal(this.tabs[0]);
  profile = signal<CollegeProfile | null>(null);
  logo = signal<string | null>(null);
  policy = signal<Policy | null>(null);
  branches = signal<Branch[]>([]);
  batches = signal<Batch[]>([]);
  tiers = signal<Tier[]>([]);
  templates = signal<EmailTemplate[]>([]);
  p: Partial<CollegeProfile> = {};
  nb = { code: '', name: '' };
  nbt = { gradYear: new Date().getFullYear() + 2 as number | null, label: '' };
  policyModes = [
    { v: 'OPEN', l: 'Open', d: 'Placed students can keep applying (only max offers applies).' },
    { v: 'ONE_OFFER', l: 'One student, one offer', d: 'Students with an offer cannot apply further.' },
    { v: 'TIER_UPGRADE', l: 'Dream policy', d: 'Placed students may apply only to higher-tier drives (e.g. Normal → Dream → Super Dream).' },
  ];

  ngOnInit(): void {
    this.svc.profile().subscribe(p => { this.profile.set(p); this.p = { ...p, brandColor: p.brandColor ?? '#2563eb' }; if (p.hasLogo) this.svc.logoUrl().subscribe(u => this.logo.set(u)); });
    this.svc.policy().subscribe(p => this.policy.set({ ...p }));
    this.loadBB();
    this.svc.tiers().subscribe(t => this.tiers.set(t.map(x => ({ ...x }))));
    this.svc.templates().subscribe(t => this.templates.set(t.map(x => ({ ...x }))));
  }
  loadBB(): void { this.svc.branches().subscribe(b => this.branches.set(b)); this.svc.batches().subscribe(b => this.batches.set(b)); this.college.loadReference(); }
  removeAt<T>(arr: T[], i: number): T[] { return arr.filter((_, j) => j !== i); }

  saveProfile(): void {
    this.svc.saveProfile({ collegeName: this.p.collegeName, university: this.p.university, city: this.p.city, state: this.p.state, website: this.p.website || undefined,
      tpoName: this.p.tpoName, tpoPhone: this.p.tpoPhone || undefined, currentAcademicYear: this.p.currentAcademicYear || undefined, brandColor: this.p.brandColor })
      .subscribe(p => { this.profile.set(p); this.toast.success('Profile saved'); this.college.loadMe().subscribe(); });
  }
  uploadLogo(f: File): void { this.svc.uploadLogo(f).subscribe(() => { this.toast.success('Logo updated'); this.svc.logoUrl().subscribe(u => this.logo.set(u)); }); }
  savePolicy(): void {
    const pol = this.policy()!;
    this.svc.savePolicy({ ...pol, maxApplications: pol.maxApplications || null }).subscribe(p => { this.policy.set({ ...p }); this.toast.success('Placement policy saved'); });
  }
  addBranch(): void { this.svc.addBranch({ code: this.nb.code.trim(), name: this.nb.name.trim() }).subscribe(() => { this.nb = { code: '', name: '' }; this.loadBB(); }); }
  async deleteBranch(b: Branch): Promise<void> {
    const r = await this.confirm.ask({ title: `Delete branch ${b.code}?`, message: 'Only empty branches can be deleted.', tone: 'danger' });
    if (r.ok) this.svc.deleteBranch(b.id).subscribe(() => this.loadBB());
  }
  addBatch(): void {
    this.svc.saveBatch({ gradYear: Number(this.nbt.gradYear), label: this.nbt.label || `Batch ${this.nbt.gradYear}`, active: true })
      .subscribe(() => { this.nbt = { gradYear: null, label: '' }; this.loadBB(); });
  }
  toggleBatch(b: Batch): void { this.svc.saveBatch({ gradYear: b.gradYear, label: b.label, active: !b.active }, b.id).subscribe(() => this.loadBB()); }
  deleteBatch(b: Batch): void { this.svc.deleteBatch(b.id).subscribe(() => this.loadBB()); }
  addTier(): void { const max = Math.max(0, ...this.tiers().map(t => t.tierRank)); this.tiers.update(t => [...t, { id: null, name: '', tierRank: max + 1, minCtcLpa: 0 }]); }
  saveTiers(): void {
    this.svc.saveTiers(this.tiers().map(t => ({ ...t, tierRank: Number(t.tierRank), minCtcLpa: Number(t.minCtcLpa) })))
      .subscribe(t => { this.tiers.set(t.map(x => ({ ...x }))); this.toast.success('Tiers saved'); this.college.loadReference(); });
  }
  saveTemplate(t: EmailTemplate): void { this.svc.saveTemplate(t).subscribe(() => { t.customized = true; this.toast.success('Template saved'); }); }
  resetTemplate(t: EmailTemplate): void { this.svc.resetTemplate(t.key).subscribe(() => this.svc.templates().subscribe(x => this.templates.set(x.map(y => ({ ...y }))))); }
}
