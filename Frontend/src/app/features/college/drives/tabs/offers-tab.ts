import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { OfferService } from '../../services/offer.service';
import { ApplicationService } from '../../services/application.service';
import { CollegeService } from '../../services/college.service';
import { Application, Drive, Offer } from '../../../../core/models/college.models';
import { StatusBadge } from '../../../../shared/ui/status-badge';
import { EmptyState } from '../../../../shared/ui/states';
import { OfferForm } from '../../offers/offer-form';
import { OfferActions } from '../../offers/offer-actions';

@Component({
  selector: 'pm-drive-offers-tab',
  imports: [DatePipe, StatusBadge, EmptyState, OfferForm, OfferActions],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (college.canWrite()) {
      <div class="d-flex gap-2 align-items-center mb-3 flex-wrap">
        <button class="btn btn-sm btn-primary" (click)="formOpen.set(true)"><i class="bi bi-award"></i> Record offer</button>
        <span class="small text-body-secondary">{{ pendingSelected().length }} selected student(s) without an offer</span>
      </div>
    }
    <div class="table-responsive bg-body border rounded-3">
      <table class="table table-sm align-middle small mb-0">
        <thead><tr><th>Student</th><th>Role</th><th>CTC</th><th class="d-none d-md-table-cell">Dates</th><th>Status</th><th></th></tr></thead>
        <tbody>
          @for (o of offers(); track o.id) {
            <tr><td><strong>{{ o.studentName }}</strong><br><span class="text-body-secondary">{{ o.rollNumber }}</span></td>
              <td>{{ o.roleTitle }}</td><td>{{ o.ctcLpa }} LPA @if (o.tierName) { <span class="badge text-bg-light border">{{ o.tierName }}</span> }</td>
              <td class="d-none d-md-table-cell">{{ o.offerDate | date: 'mediumDate' }} @if (o.joiningDate) { → {{ o.joiningDate | date: 'mediumDate' }} }</td>
              <td><pm-status [value]="o.status" /></td>
              <td class="text-end"><pm-offer-actions [offer]="o" (changed)="load(); changed.emit()" /></td></tr>
          } @empty { <tr><td colspan="6"><pm-empty-state icon="bi-award" title="No offers recorded" text="Record offers for students selected in the final round." /></td></tr> }
        </tbody>
      </table>
    </div>
    <pm-offer-form [open]="formOpen()" [drive]="drive()" [presetStudents]="presets()" (closed)="formOpen.set(false)" (saved)="formOpen.set(false); load(); changed.emit()" />`,
})
export class DriveOffersTab implements OnInit {
  private svc = inject(OfferService);
  private apps = inject(ApplicationService);
  college = inject(CollegeService);
  drive = input.required<Drive>();
  changed = output<void>();
  offers = signal<Offer[]>([]);
  applicants = signal<Application[]>([]);
  formOpen = signal(false);
  pendingSelected = computed(() => this.applicants().filter(a => a.status === 'SELECTED' && !this.offers().some(o => o.studentId === a.studentId && o.status !== 'REVOKED')));
  presets = computed(() => {
    const pool = this.pendingSelected().length ? this.pendingSelected() : this.applicants().filter(a => a.status !== 'WITHDRAWN' && a.status !== 'REJECTED');
    return pool.map(a => ({ id: a.studentId, label: `${a.rollNumber} - ${a.studentName} (${a.status})` }));
  });

  ngOnInit(): void {
    this.load();
    this.apps.list(this.drive().id, null, '', 0, 500).subscribe(p => this.applicants.set(p.content));
  }
  load(): void { this.svc.list({ driveId: this.drive().id }, 0, 200).subscribe(p => this.offers.set(p.content)); }
}
