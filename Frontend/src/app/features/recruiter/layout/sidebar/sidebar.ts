import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-recruiter-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class RecruiterSidebar {
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
    { label: 'Log out', icon: 'bi-box-arrow-right', route: '/select-role' }
  ];
}
