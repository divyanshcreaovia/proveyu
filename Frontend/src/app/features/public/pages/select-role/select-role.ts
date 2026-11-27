import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { PUBLIC_IMAGES } from '../../public-images';

/** "Get started": choose the college or the student portal. Companies join a drive by invitation. */
@Component({
  selector: 'app-select-role',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './select-role.html',
  styleUrl: '../portal-choice.scss',
})
export class SelectRole {
  readonly images = PUBLIC_IMAGES;
}
