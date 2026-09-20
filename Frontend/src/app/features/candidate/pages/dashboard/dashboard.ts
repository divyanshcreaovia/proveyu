import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  stats = [
    { label: 'Tests Taken', value: '8', icon: 'bi-journal-check', color: 'text-primary' },
    { label: 'Tests Passed', value: '6', icon: 'bi-check-circle', color: 'text-success' },
    { label: 'Interview Invites', value: '3', icon: 'bi-envelope-open', color: 'text-warning' },
    { label: 'Offers Received', value: '1', icon: 'bi-briefcase', color: 'text-info' },
    { label: 'Profile Views', value: '246', icon: 'bi-eye', color: 'text-primary' },
    { label: 'Tier Level', value: 'Silver', icon: 'bi-award', color: 'text-secondary' },
  ];

  upcomingEvents = [
    { title: 'DSA Assessment', date: 'Sep 23, 2024', time: '2:00 PM - 3:30 PM', status: 'Upcoming', statusColor: 'bg-warning text-dark' },
    { title: 'Tech Interview - TCS', date: 'Sep 24, 2024', time: '11:00 AM', status: 'Confirmed', statusColor: 'bg-success text-white' },
    { title: 'System Design Interview', date: 'Sep 26, 2024', time: '4:00 PM', status: 'Pending', statusColor: 'bg-warning text-dark' }
  ];

  skillsProgress = [
    { name: 'DSA', progress: 78, color: 'bg-primary' },
    { name: 'System Design', progress: 45, color: 'bg-info' },
    { name: 'Java', progress: 82, color: 'bg-success' },
    { name: 'SQL', progress: 60, color: 'bg-warning' },
    { name: 'React', progress: 90, color: 'bg-primary' }
  ];
}
