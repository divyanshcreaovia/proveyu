import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

/** Drag-and-drop / click file picker with client-side type & size validation (server validates again). */
@Component({
  selector: 'pm-file-upload',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="pm-upload" [class.pm-upload-over]="over()" [class.pm-upload-disabled]="disabled()"
           (dragover)="$event.preventDefault(); over.set(true)" (dragleave)="over.set(false)" (drop)="drop($event)">
      <input type="file" class="visually-hidden" [accept]="accept()" [disabled]="disabled()" (change)="pick($event)" />
      <i class="bi bi-cloud-arrow-up fs-3 d-block mb-1" aria-hidden="true"></i>
      <span class="fw-semibold">{{ file()?.name ?? label() }}</span>
      <span class="d-block small text-body-secondary">{{ hint() || ('Allowed: ' + accept() + ' · max ' + maxMb() + ' MB') }}</span>
    </label>
    @if (error()) { <div class="text-danger small mt-1" role="alert">{{ error() }}</div> }`,
  styles: [`
    .pm-upload { display: block; text-align: center; padding: 1.5rem; border: 2px dashed var(--pm-input-border, #cbd5e1); border-radius: 14px; cursor: pointer;
      background: var(--pm-input-bg, #f8fafc); color: var(--pm-text, #1e293b);
      transition: border-color .15s, background .15s; }
    .pm-upload:hover, .pm-upload-over { border-color: #4f46e5; background: rgba(79,70,229,.05); }
    .pm-upload:focus-within { outline: 2px solid #2563eb; outline-offset: 2px; }
    .pm-upload-disabled { opacity: .6; cursor: not-allowed; }
  `],
})
export class FileUpload {
  accept = input('.pdf');
  maxMb = input(10);
  label = input('Choose a file or drag it here');
  hint = input('');
  disabled = input(false);
  fileSelected = output<File>();
  file = signal<File | null>(null);
  error = signal('');
  over = signal(false);

  pick(ev: Event): void {
    const el = ev.target as HTMLInputElement;
    if (el.files?.[0]) this.handle(el.files[0]);
    el.value = '';
  }
  drop(ev: DragEvent): void {
    ev.preventDefault();
    this.over.set(false);
    if (!this.disabled() && ev.dataTransfer?.files?.[0]) this.handle(ev.dataTransfer.files[0]);
  }
  reset(): void { this.file.set(null); this.error.set(''); }

  private handle(f: File): void {
    const ext = '.' + (f.name.split('.').pop() ?? '').toLowerCase();
    const allowed = this.accept().split(',').map(a => a.trim().toLowerCase());
    if (!allowed.includes(ext)) { this.error.set(`File type ${ext} is not allowed`); return; }
    if (f.size > this.maxMb() * 1024 * 1024) { this.error.set(`File is larger than ${this.maxMb()} MB`); return; }
    this.error.set('');
    this.file.set(f);
    this.fileSelected.emit(f);
  }
}
