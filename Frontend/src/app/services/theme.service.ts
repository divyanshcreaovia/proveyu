import { Injectable, signal } from '@angular/core';

/** Light / dark theme for the portals (Bootstrap 5.3 data-bs-theme). Persisted per browser. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal<boolean>(this.read());

  toggle(): void {
    this.dark.update(d => !d);
    try { localStorage.setItem('proveyu_theme', this.dark() ? 'dark' : 'light'); } catch { /* storage unavailable */ }
  }

  private read(): boolean {
    try {
      const saved = localStorage.getItem('proveyu_theme');
      if (saved) return saved === 'dark';
      return typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches;
    } catch {
      return false;
    }
  }
}
