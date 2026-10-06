import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-college-ticket-form',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './ticket-form.html',
  styleUrl: './ticket-form.scss',
})
export class TicketForm {
  subject = '';
  category = 'Technical Issue';
  priority = 'Medium';
  description = '';
  isSubmitted = false;

  constructor(private router: Router) {}

  submitTicket() {
    this.isSubmitted = true;
    setTimeout(() => {
      this.router.navigate(['/college/support']);
    }, 1500);
  }
}
