import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StudentImportService } from '../services/student-import.service';
import { ToastService } from '../../../core/services/toast.service';
import { ImportHistory, ImportResult } from '../../../core/models/college.models';
import { Stepper } from '../../../shared/ui/stepper';
import { FileUpload } from '../../../shared/ui/file-upload';
import { EmptyState } from '../../../shared/ui/states';

@Component({
  selector: 'pm-student-import',
  imports: [Stepper, FileUpload, EmptyState, DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/college/students" class="small"><i class="bi bi-arrow-left"></i> Back to students</a>
    <div class="row g-3 mt-1">
      <div class="col-xl-8">
        <section class="pm-imp-card">
          <pm-stepper [steps]="['Upload', 'Validate', 'Import']" [current]="step()" [clickable]="false" />
          @if (step() === 0) {
            <p class="small text-body-secondary">Download the template, fill one row per student and upload it. Existing roll numbers are updated;
              duplicate roll / e-mail / phone rows are skipped. Nothing is saved until you confirm.</p>
            <button class="btn btn-sm btn-outline-primary mb-3" (click)="svc.template().subscribe()"><i class="bi bi-download"></i> Download Excel template</button>
            <pm-file-upload accept=".xlsx,.xls,.csv" [maxMb]="10" label="Choose the filled sheet (.xlsx or .csv)" (fileSelected)="file.set($event)" />
            <div class="text-end mt-3">
              <button class="btn btn-primary" [disabled]="!file() || busy()" (click)="validate()">
                @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Validate (dry run)</button>
            </div>
          }
          @if (step() >= 1 && result(); as r) {
            <div class="pm-imp-summary">
              <div><strong>{{ r.totalRows }}</strong><span>rows</span></div>
              <div class="text-success"><strong>{{ r.dryRun ? r.validRows : r.created + r.updated }}</strong><span>{{ r.dryRun ? 'valid' : 'imported' }}</span></div>
              <div class="text-danger"><strong>{{ r.errorRows }}</strong><span>with errors</span></div>
              <div class="text-warning"><strong>{{ r.duplicateRows }}</strong><span>duplicates</span></div>
              @if (!r.dryRun) { <div><strong>{{ r.created }} / {{ r.updated }}</strong><span>created / updated</span></div> }
            </div>
            @if (r.errors.length) {
              <h3 class="h6 mt-3">Row errors</h3>
              <div class="table-responsive pm-imp-scroll"><table class="table table-sm small"><thead><tr><th>Row</th><th>Field</th><th>Problem</th></tr></thead>
                <tbody>@for (e of r.errors; track $index) { <tr><td>{{ e.row }}</td><td><code>{{ e.field }}</code></td><td>{{ e.message }}</td></tr> }</tbody></table></div>
            }
            @if (r.duplicates.length) {
              <h3 class="h6 mt-3">Duplicates (skipped)</h3>
              <div class="table-responsive pm-imp-scroll"><table class="table table-sm small"><thead><tr><th>Row</th><th>Reason</th></tr></thead>
                <tbody>@for (e of r.duplicates; track $index) { <tr><td>{{ e.row }}</td><td>{{ e.message }}</td></tr> }</tbody></table></div>
            }
            @if (r.dryRun && r.preview.length) {
              <h3 class="h6 mt-3">Preview (first {{ r.preview.length }} valid rows)</h3>
              <div class="table-responsive pm-imp-scroll"><table class="table table-sm small"><thead><tr><th>Row</th><th>Action</th><th>Roll</th><th>Name</th><th>E-mail</th><th>Branch</th><th>Batch</th><th>CGPA</th></tr></thead>
                <tbody>@for (p of r.preview; track p.row) { <tr><td>{{ p.row }}</td><td><span class="badge" [class.text-bg-success]="p.action === 'CREATE'" [class.text-bg-info]="p.action === 'UPDATE'">{{ p.action }}</span></td>
                  <td>{{ p.rollNumber }}</td><td>{{ p.name }}</td><td>{{ p.email }}</td><td>{{ p.branchCode }}</td><td>{{ p.batchYear }}</td><td>{{ p.cgpa ?? '-' }}</td></tr> }</tbody></table></div>
            }
            <div class="d-flex justify-content-between mt-3">
              <button class="btn btn-outline-secondary" (click)="restart()">{{ r.dryRun ? 'Choose another file' : 'Import another file' }}</button>
              @if (r.dryRun) {
                <button class="btn btn-primary" [disabled]="!r.validRows || busy()" (click)="commit()">
                  @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span> } Import {{ r.validRows }} valid row(s)</button>
              } @else { <a routerLink="/college/students" class="btn btn-primary">View students</a> }
            </div>
          }
        </section>
      </div>
      <div class="col-xl-4">
        <section class="pm-imp-card">
          <h2 class="h6 fw-bold">Import history</h2>
          @for (h of history(); track h.id) {
            <div class="border-bottom py-2 small">
              <div class="d-flex justify-content-between"><span class="text-truncate fw-semibold">{{ h.fileName }}</span>
                <span class="badge" [class.text-bg-secondary]="h.dryRun" [class.text-bg-success]="!h.dryRun">{{ h.dryRun ? 'Dry run' : 'Imported' }}</span></div>
              <div class="text-body-secondary">{{ h.createdAt | date: 'medium' }} · {{ h.totalRows }} rows · +{{ h.createdRows }} / ~{{ h.updatedRows }} · {{ h.errorRows }} err · {{ h.duplicateRows }} dup</div>
            </div>
          } @empty { <pm-empty-state icon="bi-clock-history" title="No imports yet" /> }
        </section>
      </div>
    </div>`,
  styles: [`
    .pm-imp-card { background: var(--pm-card-bg, #fff); border: 1px solid var(--pm-card-border, #e2e8f0); border-radius: 16px; box-shadow: var(--pm-card-shadow, 0 4px 16px rgba(0,0,0,.02)); padding: 1.5rem; }
    .pm-imp-summary { display: flex; flex-wrap: wrap; gap: .75rem; }
    .pm-imp-summary > div { flex: 1 1 100px; border: 1px solid var(--bs-border-color); border-radius: .6rem; padding: .6rem; text-align: center; }
    .pm-imp-summary strong { display: block; font-size: 1.3rem; } .pm-imp-summary span { font-size: .75rem; color: var(--bs-secondary-color); }
    .pm-imp-scroll { max-height: 260px; overflow-y: auto; }
  `],
})
export class StudentImport implements OnInit {
  svc = inject(StudentImportService);
  private toast = inject(ToastService);
  step = signal(0);
  file = signal<File | null>(null);
  result = signal<ImportResult | null>(null);
  busy = signal(false);
  history = signal<ImportHistory[]>([]);

  ngOnInit(): void { this.loadHistory(); }
  loadHistory(): void { this.svc.history().subscribe(p => this.history.set(p.content)); }

  validate(): void {
    this.busy.set(true);
    this.svc.upload(this.file()!, true).subscribe({
      next: r => { this.busy.set(false); this.result.set(r); this.step.set(1); this.loadHistory(); },
      error: () => this.busy.set(false),
    });
  }
  commit(): void {
    this.busy.set(true);
    this.svc.upload(this.file()!, false).subscribe({
      next: r => { this.busy.set(false); this.result.set(r); this.step.set(2); this.loadHistory();
        this.toast.success(`Imported: ${r.created} created, ${r.updated} updated`); },
      error: () => this.busy.set(false),
    });
  }
  restart(): void { this.file.set(null); this.result.set(null); this.step.set(0); }
}
