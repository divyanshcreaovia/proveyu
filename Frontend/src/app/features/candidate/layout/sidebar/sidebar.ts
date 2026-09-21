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
    { label: 'Overview', icon: 'bi-grid-fill', route: '/candidate/dashboard' },
    { label: 'Profile', icon: 'bi-person-fill', route: '/candidate/profile' },
    { label: 'Book Exam', icon: 'bi-calendar-event-fill', route: '/candidate/book-slot' },
    { label: 'My Passport', icon: 'bi-pass-fill', route: '/candidate/my-passport' },
    // { label: 'Prep Guide', icon: 'bi-journal-bookmark-fill', route: '/candidate/prep-guide' },
    { label: 'Interview Invites', icon: 'bi-envelope-paper-fill', route: '/candidate/interview-invites' },
    // { label: 'Results & Offers', icon: 'bi-award-fill', route: '/candidate/results' },
  ];

  bottomItems = [
    { label: 'Support', icon: 'bi-headset', route: '/candidate/support' },
    { label: 'Settings', icon: 'bi-gear-fill', route: '/candidate/settings' },
    { label: 'Log out', icon: 'bi-box-arrow-right', route: '/login' }
  ];
}
