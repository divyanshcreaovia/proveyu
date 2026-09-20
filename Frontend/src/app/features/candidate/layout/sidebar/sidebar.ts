import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  isCollapsed = false;

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
  }

  navItems = [
    { label: 'Dashboard', icon: 'bi-grid-1x2', route: '/candidate/dashboard' },
    { label: 'Profile', icon: 'bi-person', route: '/candidate/profile' },
    { label: 'Book Exam', icon: 'bi-calendar-plus', route: '/candidate/book-slot' },
    { label: 'My Passport', icon: 'bi-pass', route: '/candidate/my-passport' },
    { label: 'Prep Guide', icon: 'bi-book', route: '/candidate/prep-guide' },
    { label: 'Interview Invites', icon: 'bi-envelope-paper', route: '/candidate/interview-invites' },
    { label: 'Results', icon: 'bi-award', route: '/candidate/results' },
    { label: 'Offers', icon: 'bi-briefcase', route: '/candidate/offers' },
    { label: 'Settings', icon: 'bi-gear', route: '/candidate/settings' }
  ];
}
