import { Component, signal } from '@angular/core';
import { RouterModule } from '@angular/router';

/** Public site header. Shown only outside the portals (see App.isPortalRoute). */
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
}
