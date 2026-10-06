import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-audit-log',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.scss',
})
export class AuditLog {
  auditLogs = [
    { time: '10:30 AM Oct 20', user: 'Rahul Sharma', action: 'Imported 100 Students', module: 'Students', detail: 'Batch 2024 CSV Roster' },
    { time: '10:15 AM Oct 20', user: 'Divya Kumar', action: 'Changed Tier Assignment', module: 'Results', detail: 'Roll 045: Tier 2 → Tier 1' },
    { time: '10:00 AM Oct 20', user: 'Rahul Sharma', action: 'Downloaded Report', module: 'Reports', detail: 'Test 4 Full Results PDF' },
    { time: '09:45 AM Oct 20', user: 'Sanjay Jadhav', action: 'Sent Feedback Emails', module: 'Communications', detail: '280 Students' },
  ];
}
