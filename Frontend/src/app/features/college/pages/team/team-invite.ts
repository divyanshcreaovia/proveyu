import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-college-team-invite',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './team-invite.html',
  styleUrl: './team-invite.scss',
})
export class TeamInvite {
  email = '';
  role = 'Faculty Coordinator';
  department = 'Computer Science';
  isSent = false;

  constructor(private router: Router) {}

  sendInvite() {
    this.isSent = true;
    setTimeout(() => {
      this.router.navigate(['/college/team']);
    }, 1500);
  }
}
