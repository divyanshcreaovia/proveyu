import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Navbar } from './shared/components/navbar/navbar';
import { Footer } from './shared/components/footer/footer';
import { GlobalNotification } from './shared/components/global-notification/global-notification';
import { ToastContainer } from './shared/ui/toast-container';
import { ConfirmDialog } from './shared/ui/confirm-dialog';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Navbar, Footer, GlobalNotification, ToastContainer, ConfirmDialog],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private router = inject(Router);
  /** Portals render their own chrome; public pages get the marketing navbar / footer. */
  readonly isPortalRoute = signal(this.portal(this.router.url));

  constructor() {
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(e => this.isPortalRoute.set(this.portal(e.urlAfterRedirects || e.url)));
  }

  private portal(url: string): boolean {
    return /^\/(candidate|recruiter|college|admin|forbidden)/.test(url);
  }
}
