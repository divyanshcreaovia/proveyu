import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/** Public page: the placement season from setup to reports, across the placement cell, students and companies. */
@Component({
  selector: 'app-how-it-works',
  imports: [RouterModule],
  templateUrl: './how-it-works.html',
  styleUrl: './how-it-works.scss',
})
export class HowItWorks {
  readonly steps = [
    {
      tag: 'SET UP', who: 'Placement cell', whoIcon: 'bi-building',
      title: 'Register the Placement Cell and Import the Batch',
      text: 'Create the college account and verify your e-mail; the ProveYu team approves the college. Then bring in your students and your team.',
      points: ['Import students from Excel or CSV with a dry run', 'Duplicate rows flagged before anything is saved', 'Invite coordinators and viewers with branch scope', 'Set your placement policy once (one-offer, dream upgrade)', 'Add companies with contacts and MoU', 'Upload your own practice tests (optional)'],
    },
    {
      tag: 'ACTIVATE & PREPARE', who: 'Students', whoIcon: 'bi-mortarboard',
      title: 'Students Activate Their Portal and Start Preparing',
      text: 'Each student activates the account with their college e-mail and roll number. Academic details come from the college, so the profile is ready in minutes.',
      points: ['6-digit e-mail code, then a password', 'Upload resume and documents once', 'Placement-readiness checklist on the dashboard', '20-test mock path from easy to hard', 'AI mock interview on any role or job description', 'Off-campus jobs for the fields they choose', 'Chat with the placement cell'],
    },
    {
      tag: 'PUBLISH A DRIVE', who: 'Placement cell → students', whoIcon: 'bi-briefcase',
      title: 'Publish a Drive; Eligible Students Apply',
      text: 'Add the role, CTC, criteria and rounds. The portal checks every student against the criteria and your placement policy before anyone applies.',
      points: ['Eligibility preview with the reason per student', 'Audited include / exclude overrides', 'Eligible students get an e-mail at once', 'One-click apply from the student portal', 'Ineligible students see exactly why', 'Clone last year\'s drive to save time'],
    },
    {
      tag: 'ROUNDS & INTERVIEWS', who: 'Placement cell + company', whoIcon: 'bi-people',
      title: 'Shortlist, Run Rounds and Schedule Interviews',
      text: 'Your team, or the company itself in its Drive Room, shortlists applicants and records each round. Passing students move to the next round automatically.',
      points: ['Bulk shortlist, waitlist or reject', 'Round results typed in or uploaded from Excel', 'Interview calendar with clash checks', 'Reminders to students 24 h before', 'Panel scorecards from the company', 'Students follow every round live'],
    },
    {
      tag: 'OFFERS & REPORTS', who: 'Everyone', whoIcon: 'bi-award',
      title: 'Record Offers and Keep the Numbers Ready',
      text: 'Offers are checked against your placement policy. Students accept or decline in their portal, and placement status updates itself.',
      points: ['Offer letters as PDF', 'Accept or decline online', 'Placed status and highest CTC worked out automatically', 'Live dashboard and drive funnel', 'NIRF / NAAC-style placement summary', 'Reports in Excel, CSV or PDF, also by scheduled e-mail'],
    },
  ];
}
