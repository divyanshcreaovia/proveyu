import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-companies',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './companies.html',
  styleUrl: './companies.scss',
})
export class Companies {
  activePartnerships = [
    { company: 'TCS', status: 'Active', placements: 35, upcoming: 'Batch Interview Nov 10', email: 'hr@tcs.com' },
    { company: 'Wipro', status: 'Active', placements: 28, upcoming: 'Round 2 Interviews Nov 5', email: 'placement@wipro.com' },
    { company: 'Amdocs', status: 'Active', placements: 22, upcoming: 'Batch Interview Nov 3', email: 'careers@amdocs.com' },
  ];

  realtimeSearches = [
    { company: 'TCS', time: 'Today 2:45 PM', matched: '5 Tier 1 Students Matched', query: 'Java, Spring Boot, PostgreSQL', status: 'Interested' },
    { company: 'Infosys', time: 'Yesterday 3:00 PM', matched: '12 Profiles Viewed', query: 'QA & Test Automation', status: 'Interviews Scheduled' },
    { company: 'Accenture', time: 'Oct 28', matched: '20+ Candidates Reviewed', query: 'Full Stack Web Engineering', status: 'Pending Review' }
  ];
}
