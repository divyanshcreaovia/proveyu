import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class CollegeSidebar {
  isCollapsed = false;

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
  }

  navItems = [
    { label: 'Dashboard', icon: 'bi-grid-fill', route: '/college/dashboard' },
    { label: 'Students', icon: 'bi-people-fill', route: '/college/students' },
    { label: 'Tests', icon: 'bi-file-earmark-check-fill', route: '/college/tests' },
    { label: 'Results & Analytics', icon: 'bi-bar-chart-line-fill', route: '/college/results/analytics' },
    { label: 'Placements', icon: 'bi-briefcase-fill', route: '/college/placements' },
    { label: 'Team Members', icon: 'bi-person-badge-fill', route: '/college/team' },
    { label: 'Reports', icon: 'bi-file-earmark-text-fill', route: '/college/reports' },
  ];

  bottomItems = [
    { label: 'Support & Help', icon: 'bi-headset', route: '/college/support' },
    { label: 'Settings', icon: 'bi-gear-fill', route: '/college/settings' },
    { label: 'Log out', icon: 'bi-box-arrow-right', route: '/select-role' }
  ];
}
