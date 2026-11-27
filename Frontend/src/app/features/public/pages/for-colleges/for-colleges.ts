import { Scene } from '../../scene';
import { PublicCta } from '../../cta';
import { ScrollAnimate } from '../../scroll-animate';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FAQS } from '../home/home';

interface FeatureGroup { title: string; summary: string; }

/** Public page: everything the College Placement Portal and the company Drive Room do. */
@Component({
  selector: 'app-for-colleges',
  imports: [RouterModule, ScrollAnimate, Scene, PublicCta],
  templateUrl: './for-colleges.html',
})
export class ForColleges {
  activeFaq: number | null = 0;
  readonly faqs = FAQS.slice(0, 4);

  readonly groups: FeatureGroup[] = [
    { title: 'Students', summary: 'Import the batch from Excel or CSV with duplicate checks, verify documents and keep one clean record for every student.' },
    { title: 'Companies', summary: 'Contacts, MoU, visit history and feedback in one place. Invite company HR and panels to a drive.' },
    { title: 'Drives & eligibility', summary: 'Set the criteria once and see who is eligible, with the reason for every student. Overrides are audited.' },
    { title: 'Rounds & interviews', summary: 'Shortlist in bulk, record round results and schedule interviews with clash checks and reminders.' },
    { title: 'Offers & placement policy', summary: 'Record offers against your placement policy. Students accept online and placement status updates itself.' },
    { title: 'Communication', summary: 'Announcements to filtered groups in-app and by e-mail, with read receipts and live chat.' },
    { title: 'Reports & analytics', summary: 'A live dashboard and reports, including a NIRF / NAAC-style summary, in Excel, CSV or PDF.' },
    { title: 'Team, roles & audit', summary: 'Admin, coordinator and viewer roles with branch scope, and a log of who changed what.' },
    { title: 'Preparation for your students', summary: 'A 20-test mock path, your own practice tests, AI mock interviews and a readiness checklist.' },
    { title: 'College tests & exams', summary: 'Upload tests and exams, set the window, monitor live, review proctoring logs and screenshots, and export the marks register.' },
    { title: 'Code ground leaderboard', summary: 'See your students’ coding points, problems solved and streaks, filtered by branch and batch.' },
    { title: 'Off-campus & NOC', summary: 'Follow off-campus applications and approve or reject NOC requests, issued as PDF on your college letterhead.' },
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
}
