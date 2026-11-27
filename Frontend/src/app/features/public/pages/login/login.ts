import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { PUBLIC_IMAGES } from '../../public-images';

/** "Sign in": choose the college, student or company portal; each has its own login page. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './login.html',
  styleUrl: '../portal-choice.scss',
})
export class Login {
  readonly images = PUBLIC_IMAGES;
}
