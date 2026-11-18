import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { api, ApiResponse } from '../../../core/api';
import { SILENT_ERRORS } from '../../../core/interceptors/error.interceptor';

interface Banner { enabled: boolean; text: string; link: string; }

/** Public site header. Shown only outside the portals (see App.isPortalRoute). The announcement bar is set in Admin > Settings. */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
})
export class Navbar {
  /** Mobile menu (Bootstrap's JS is not loaded, so the collapse is toggled here). */
  open = signal(false);
  showBanner = signal(true);
  /** Default until the server answers (and if it cannot be reached). */
  banner = signal<Banner | null>({
    enabled: true, text: 'AI mock interviews are live for students: speak your answers, get a scored report in minutes.', link: '/for-students#ai-interview',
  });

  constructor() {
    inject(HttpClient).get<ApiResponse<Banner>>(api('/api/v1/public/banner'), { context: new HttpContext().set(SILENT_ERRORS, true) }).subscribe({
      next: r => this.banner.set(r.data?.enabled ? r.data : null),
      error: () => undefined,
    });
  }

  external(link: string): boolean { return /^https?:\/\//.test(link); }
  path(link: string): string { return link.split('#')[0] || '/'; }
  fragment(link: string): string | undefined { return link.includes('#') ? link.split('#')[1] : undefined; }
}
