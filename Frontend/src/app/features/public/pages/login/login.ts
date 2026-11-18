import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/** "Sign in": choose the college, student or company portal; each has its own login page. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {}
