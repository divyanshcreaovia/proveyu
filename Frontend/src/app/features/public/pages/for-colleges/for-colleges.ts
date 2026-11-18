import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FAQS } from '../home/home';

interface FeatureGroup { icon: string; tone: string; title: string; text: string; points: string[]; }

/** Public page: everything the College Placement Portal and the company Drive Room do. */
@Component({
  selector: 'app-for-colleges',
  imports: [RouterModule],
  templateUrl: './for-colleges.html',
  styleUrls: ['../home/home.scss'],
})
export class ForColleges {
  activeFaq: number | null = 0;
  readonly faqs = FAQS.slice(0, 4);

  readonly groups: FeatureGroup[] = [
    {
      icon: 'bi-people', tone: 'tone-blue', title: 'Students',
      text: 'One clean student master for the whole batch.',
      points: ['Import from Excel or CSV with a dry run and duplicate detection', 'Search, filters and sorting across the whole batch','Document vault with verification', 'Debarred and opted-out status, soft delete and restore'],
    },
    {
      icon: 'bi-building', tone: 'tone-violet', title: 'Companies',
      text: 'Your recruiter relationships, kept in one place.',
      points: ['Contacts, MoU and visit history', 'Feedback and ratings after every drive', 'Invite company HR and panels to a drive', 'Export the full company list'],
    },
    {
      icon: 'bi-briefcase', tone: 'tone-green', title: 'Drives & eligibility',
      text: 'Publish a drive and let the rules do the filtering.',
      points: ['Criteria on CGPA, 10th / 12th %, backlogs, gap years, branch and batch','Eligibility preview with the reason for every student', 'Audited include / exclude overrides', 'Clone a drive; draft, published, ongoing, completed'],
    },
    {
      icon: 'bi-diagram-3', tone: 'tone-blue', title: 'Rounds & interviews',
      text: 'Run every round without chasing anyone.',
      points: ['Bulk shortlist, waitlist or reject', 'Round results typed in or uploaded from Excel / CSV', 'Passing students advance to the next round automatically', 'Interview calendar with student, panel and room clash checks, reminders 24 h ahead'],
    },
    {
      icon: 'bi-award', tone: 'tone-amber', title: 'Offers & placement policy',
      text: 'Offers recorded once, numbers always correct.',
      points: ['One-offer, dream upgrade, maximum offers and minimum hike rules', 'Offer letters as PDF; students accept or decline online', 'Placed status and highest CTC worked out automatically', 'Every policy override is logged'],
    },
    {
      icon: 'bi-megaphone', tone: 'tone-violet', title: 'Communication',
      text: 'Reach exactly the students who need to know.',
      points: ['Announcements to filtered groups, in-app and by e-mail', 'Read receipts for every announcement', 'Live chat between students and their coordinators', 'Global search across students, drives and companies'],
    },
    {
      icon: 'bi-bar-chart-line', tone: 'tone-green', title: 'Reports & analytics',
      text: 'The numbers management asks for, ready any day.',
      points: ['Live dashboard with KPIs, charts and drive funnel', '7 reports incl. a NIRF / NAAC-style placement summary', 'Branch, company, CTC, drive, alumni and skill-gap reports', 'Excel, CSV or PDF, and scheduled e-mail reports'],
    },
    {
      icon: 'bi-person-badge', tone: 'tone-blue', title: 'Team, roles & audit',
      text: 'Give every coordinator the right access.',
      points: ['Admin, coordinator and viewer roles', 'Branch scope, so a coordinator sees only their branches', 'Invite, change or revoke team members', 'Audit log of every change, by whom and when'],
    },
    {
      icon: 'bi-journal-code', tone: 'tone-amber', title: 'Preparation for your students',
      text: 'Students practise inside the same portal.',
      points: ['20-test mock path from easy to hard', 'Upload your own drive-specific practice tests', 'AI mock interviews on the role or job description', 'A placement-readiness checklist on every student dashboard'],
    },
  ];

  readonly roomPoints = [
    'Overview with the drive funnel and a live round board',
    'Applicants with filters, profiles, resumes and a ZIP of all resumes',
    'Shortlist, waitlist or reject; start and complete rounds',
    'Panel scorecard: technical, communication and attitude, with remarks',
    'Release offers to selected students and upload offer letters',
    'Excel and PDF report of the drive',
  ];

  readonly controlPoints = [
    'Invite only the people you want, per drive, by work e-mail',
    'Choose whether phone, e-mail and 10th / 12th marks are shared',
    'Read every panel scorecard round by round',
    'Revoke access at any time',
  ];

  toggleFaq(index: number) {
    this.activeFaq = this.activeFaq === index ? null : index;
  }
}
