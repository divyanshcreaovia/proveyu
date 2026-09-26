import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-recruiter-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class RecruiterSidebar {
  private router = inject(Router);
  private http = inject(HttpClient);
  isCollapsed = false;

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
  }

  navItems = [
    { label: 'Dashboard', icon: 'bi-grid-fill', route: '/recruiter/dashboard' },
    { label: 'Manage Hiring', icon: 'bi-kanban-fill', route: '/recruiter/manage-hiring' },
    { label: 'Post Requirement', icon: 'bi-file-earmark-plus-fill', route: '/recruiter/post-requirement' },
    { label: 'Company Profile', icon: 'bi-building-fill', route: '/recruiter/profile' },
    { label: 'Candidate Profiles', icon: 'bi-people-fill', route: '/recruiter/candidate-detail' },
    { label: 'Candidate Chats', icon: 'bi-chat-dots-fill', route: '/recruiter/chat' },
  ];

  bottomItems = [
    { label: 'Support', icon: 'bi-headset', route: '/recruiter/support' },
    { label: 'Settings', icon: 'bi-gear-fill', route: '/recruiter/settings' },
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
        this.clearSessionAndRedirect();
      }
    });
  }

  private clearSessionAndRedirect() {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('candidateFullName');
    this.router.navigate(['/login']);
  }
}
