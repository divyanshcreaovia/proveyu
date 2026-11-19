import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SUPPORT_EMAIL } from '../../../features/support/support.service';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
})
export class Footer {
  readonly year = new Date().getFullYear();
  readonly supportEmail = SUPPORT_EMAIL;
}
