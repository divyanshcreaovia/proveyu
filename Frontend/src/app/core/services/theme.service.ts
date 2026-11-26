import { Injectable, signal } from '@angular/core';

/** Light / dark theme for the portals (Bootstrap 5.3 data-bs-theme). Persisted per browser. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal<boolean>(this.read());

  /** The theme to return to after a page that opens dark; null when nothing is forced. */
  private before: boolean | null = null;

  /** A page that opens in dark mode. Nothing is saved: leaving it brings back the theme from before. */
  forceDark(): void {
    if (this.before !== null) return;
    this.before = this.dark();
    this.dark.set(true);
  }

  release(): void {
    if (this.before === null) return;
    this.dark.set(this.before);
    this.before = null;
  }

  toggle(): void {
    this.before = null; // the user's own choice stays, also after leaving such a page
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
