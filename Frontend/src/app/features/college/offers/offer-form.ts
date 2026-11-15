import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OfferService } from '../../../services/offer.service';
import { CompanyService } from '../../../services/company.service';
import { StudentService } from '../../../services/student.service';
import { ToastService } from '../../../services/toast.service';
import { AppError } from '../../../core/api';
import { Company, Drive, Offer, Student } from '../../../core/models/college.models';
import { Modal } from '../../../shared/ui/modal';

/** Record (or edit) an offer. Drive offers pre-fill company / role / CTC; off-campus offers pick any company. */
@Component({
  selector: 'pm-offer-form',
  imports: [FormsModule, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pm-modal [open]="open()" [title]="offer() ? 'Edit offer' : 'Record offer'" size="lg" (closed)="closed.emit()">
      <form id="pm-offer-form" class="row g-3" (ngSubmit)="save()">
        @if (!offer()) {
          <div class="col-md-6"><label class="form-label small fw-semibold" for="of-stu">Student *</label>
            @if (presetStudents().length) {
              <select id="of-stu" class="form-select" [(ngModel)]="m.studentId" name="stu" required><option value="">Select</option>
                @for (s of presetStudents(); track s.id) { <option [value]="s.id">{{ s.label }}</option> }</select>
            } @else {
              <input id="of-stu" class="form-control" placeholder="Search roll no / name" [(ngModel)]="studentQ" name="sq" (input)="searchStudents()" />
              @for (s of found(); track s.id) {
                <button type="button" class="list-group-item list-group-item-action small border px-2 py-1" [class.active]="m.studentId === s.id" (click)="m.studentId = s.id; studentQ = s.rollNumber + ' ' + s.fullName; found.set([])">
                  {{ s.rollNumber }} - {{ s.fullName }} ({{ s.branchCode }})</button>
              }
            }
          </div>
          @if (!drive()) {
            <div class="col-md-6"><label class="form-label small fw-semibold" for="of-co">Company *</label>
              <select id="of-co" class="form-select" [(ngModel)]="m.companyId" name="co" required><option value="">Select</option>
                @for (c of companies(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }</select></div>
          }
        }
        <div class="col-md-6"><label class="form-label small fw-semibold" for="of-role">Role *</label><input id="of-role" class="form-control" [(ngModel)]="m.roleTitle" name="role" required /></div>
        <div class="col-md-3"><label class="form-label small fw-semibold" for="of-ctc">CTC (LPA) *</label><input id="of-ctc" type="number" step="0.01" min="0" class="form-control" [(ngModel)]="m.ctcLpa" name="ctc" required /></div>
        <div class="col-md-3"><label class="form-label small fw-semibold" for="of-date">Offer date</label><input id="of-date" type="date" class="form-control" [(ngModel)]="m.offerDate" name="od" /></div>
        <div class="col-md-4"><label class="form-label small fw-semibold" for="of-join">Joining date</label><input id="of-join" type="date" class="form-control" [(ngModel)]="m.joiningDate" name="jd" /></div>
        <div class="col-md-8"><label class="form-label small fw-semibold" for="of-rem">Remarks</label><input id="of-rem" class="form-control" [(ngModel)]="m.remarks" name="rem" /></div>
        <div class="col-12"><label class="form-label small fw-semibold" for="of-break">CTC breakup</label><textarea id="of-break" rows="2" class="form-control" [(ngModel)]="m.ctcBreakup" name="br"></textarea></div>
        @if (policyError()) {
          <div class="col-12"><div class="alert alert-warning small mb-2">{{ policyError() }}</div>
            <div class="form-check"><input id="of-ov" type="checkbox" class="form-check-input" [(ngModel)]="m.overridePolicy" name="ov" />
              <label for="of-ov" class="form-check-label small">Override placement policy (audited)</label></div>
            @if (m.overridePolicy) { <input class="form-control form-control-sm mt-2" placeholder="Reason (min 5 characters)" aria-label="Override reason" [(ngModel)]="m.overrideReason" name="or" /> }
          </div>
        }
      </form>
      <div pmFooter><button class="btn btn-outline-secondary" (click)="closed.emit()">Cancel</button>
        <button type="submit" form="pm-offer-form" class="btn btn-primary" [disabled]="busy()">Save offer</button></div>
    </pm-modal>`,
})
export class OfferForm {
  private svc = inject(OfferService);
  private companiesSvc = inject(CompanyService);
  private studentsSvc = inject(StudentService);
  private toast = inject(ToastService);
  open = input(false);
  drive = input<Drive | null>(null);
  offer = input<Offer | null>(null);
  presetStudents = input<{ id: string; label: string }[]>([]);
  closed = output<void>();
  saved = output<void>();
  companies = signal<Company[]>([]);
  found = signal<Student[]>([]);
  busy = signal(false);
  policyError = signal('');
  studentQ = '';
  m = this.blank();

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const o = this.offer(), d = this.drive();
      this.policyError.set('');
      this.studentQ = '';
      this.m = o ? { ...this.blank(), studentId: o.studentId, companyId: o.companyId, roleTitle: o.roleTitle, ctcLpa: o.ctcLpa, ctcBreakup: o.ctcBreakup ?? '',
          offerDate: o.offerDate ?? '', joiningDate: o.joiningDate ?? '', remarks: o.remarks ?? '' }
        : { ...this.blank(), companyId: d?.companyId ?? '', roleTitle: d?.roleTitle ?? '', ctcLpa: d?.ctcLpa ?? null, ctcBreakup: d?.ctcBreakup ?? '' };
      if (!d && !this.companies().length) this.companiesSvc.all().subscribe(c => this.companies.set(c));
    });
  }

  blank() {
    return { studentId: '', companyId: '', roleTitle: '', ctcLpa: null as number | null, ctcBreakup: '', offerDate: new Date().toISOString().slice(0, 10),
      joiningDate: '', remarks: '', overridePolicy: false, overrideReason: '' };
  }

  searchStudents(): void {
    if (this.studentQ.trim().length < 2) { this.found.set([]); return; }
    this.studentsSvc.list({ q: this.studentQ.trim() }, 0, 8).subscribe(p => this.found.set(p.content));
  }

  save(): void {
    if (!this.m.studentId || !this.m.roleTitle || this.m.ctcLpa === null || (!this.drive() && !this.m.companyId)) { this.toast.warning('Fill the required fields'); return; }
    const body = { studentId: this.m.studentId, driveId: this.offer()?.driveId ?? this.drive()?.id ?? null, companyId: this.m.companyId || this.drive()!.companyId,
      roleTitle: this.m.roleTitle, ctcLpa: Number(this.m.ctcLpa), ctcBreakup: this.m.ctcBreakup || null, offerDate: this.m.offerDate || null,
      joiningDate: this.m.joiningDate || null, remarks: this.m.remarks || null, overridePolicy: this.m.overridePolicy, overrideReason: this.m.overrideReason || null };
    this.busy.set(true);
    (this.offer() ? this.svc.update(this.offer()!.id, body) : this.svc.create(body)).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Offer saved. Student placement status updated.'); this.saved.emit(); },
      error: (e: AppError) => { this.busy.set(false); if (e.code === 'POLICY_VIOLATION') this.policyError.set(e.message); },
    });
  }
}
