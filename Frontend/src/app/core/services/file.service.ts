import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { api, params } from '../api';

/** Authenticated binary downloads / previews (the JWT never goes in the URL). */
@Injectable({ providedIn: 'root' })
export class FileService {
  private http = inject(HttpClient);

  /** Downloads a file from an API path and saves it with the server-provided name. */
  download(path: string, query: Record<string, unknown> = {}, fallbackName = 'download'): Observable<void> {
    return this.http.get(api(path), { params: params(query), responseType: 'blob', observe: 'response' }).pipe(
      tap((res: HttpResponse<Blob>) => this.save(res.body!, this.fileName(res, fallbackName))),
      map(() => void 0),
    );
  }

  /** Returns an object URL for inline preview (caller must revoke it). */
  objectUrl(path: string): Observable<string> {
    return this.http.get(api(path), { responseType: 'blob' }).pipe(map(b => URL.createObjectURL(b)));
  }

  open(path: string): void {
    const win = window.open('', '_blank');
    this.objectUrl(path).subscribe({
      next: url => { if (win) win.location.href = url; else window.location.href = url; },
      error: () => win?.close(),
    });
  }

  save(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private fileName(res: HttpResponse<Blob>, fallback: string): string {
    const cd = res.headers.get('Content-Disposition') ?? '';
    const m = /filename="?([^";]+)"?/i.exec(cd);
    return m ? m[1] : fallback;
  }

  /** Builds multipart form data with a single file. */
  static form(file: File, fields: Record<string, string> = {}): FormData {
    const fd = new FormData();
    fd.append('file', file, file.name);
    Object.entries(fields).forEach(([k, v]) => fd.append(k, v));
    return fd;
  }
}
