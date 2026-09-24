import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private router = inject(Router);
  private http = inject(HttpClient);
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
    { label: 'Log out', icon: 'bi-box-arrow-right', action: 'logout' }
  ];

  onBottomItemClick(item: any, event: Event) {
    if (item.action === 'logout') {
      event.preventDefault();
      this.handleLogout();
    }
  }

  private handleLogout() {
    this.http.post('http://localhost:8080/api/v1/auth/logout', {}).subscribe({
      next: () => {
        this.clearSessionAndRedirect();
      },
      error: () => {
        // Even if the backend fails, clear the local session to enforce logout
        this.clearSessionAndRedirect();
      }
    });
  }

  private clearSessionAndRedirect() {
    localStorage.removeItem('token');
    localStorage.removeItem('candidateFullName');
    this.router.navigate(['/login']);
  }
}
