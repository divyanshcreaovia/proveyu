import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/** "Get started": choose the college or the student portal. Companies join a drive by invitation. */
@Component({
  selector: 'app-select-role',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './select-role.html',
  styleUrl: './select-role.scss',
})
export class SelectRole {}
