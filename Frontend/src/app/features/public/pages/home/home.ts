import { CodeArena } from '../for-students/code-arena';
import { PUBLIC_IMAGES } from '../../public-images';
import { Scene } from '../../scene';
import { PublicCta } from '../../cta';
import { HomeHero } from './home-hero';
import { ScrollAnimate } from '../../scroll-animate';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

export interface Point { title: string; text: string; }
export interface Faq { q: string; a: string; }

/** Shared by the public pages so the same facts read the same everywhere. */
export const FAQS: Faq[] = [
  {
    q: 'How does our college get started?',
    a: 'Register your placement cell and verify your e-mail. Once the ProveYu team approves the college, import your students from Excel or CSV and publish your first drive. Your coordinators can be invited from the Team page.',
  },
  {
    q: 'How do students get access?',
    a: 'Students activate their account themselves with their college e-mail and roll number, which must match a student your placement cell added. A 6-digit code is sent to their e-mail to confirm it.',
  },
  {
    q: 'How do companies take part in a drive?',
    a: 'The placement cell invites the company HR or panel to one drive by work e-mail and chooses which student details they can see. The company then shortlists, runs rounds, fills scorecards and releases offers in its own Drive Room. It never sees other drives.',
  },
  {
    q: 'Who can see our data?',
    a: 'Every college sees only its own data. Inside the college, access follows the role (admin, coordinator or viewer) and branch scope, and every change is recorded in the audit log. Students see only their own records, and companies only what you share with them.',
  },
  {
    q: 'How often can students take the AI mock interview?',
    a: 'Each student gets one free AI interview every 15 days, on the role or job description they choose.',
  },
];

@Component({
  selector: 'app-home',
  imports: [RouterModule, ScrollAnimate, HomeHero, Scene, PublicCta, CodeArena],
  templateUrl: './home.html',
})
export class Home {
  readonly faqs = FAQS;

  readonly images = PUBLIC_IMAGES;

  readonly workspaces = [
    { scene: 'drive' as const, category: 'Placement cell · Drives & reports', title: 'College Placement Portal', text: 'Your team runs the whole season from one screen.', chips: ['Drives', 'Eligibility', 'Reports'], cta: 'Explore the college portal', link: '/for-colleges', fragment: undefined as string | undefined },
    { scene: 'student' as const, category: 'Students · Prepare & apply', title: 'Student Portal', text: 'Apply in one click and follow every round until the offer.', chips: ['Apply', 'Mock tests', 'AI interview'], cta: 'Explore the student portal', link: '/for-students', fragment: undefined },
    { scene: 'company' as const, category: 'Recruiters · Shortlist & offer', title: 'Company Drive Room', text: 'Recruiters evaluate on the same drive as the placement cell.', chips: ['Shortlist', 'Scorecards', 'Offers'], cta: 'How the Drive Room works', link: '/for-colleges', fragment: 'drive-room' },
    { scene: 'leadership' as const, category: 'Leadership · Live dashboards', title: 'Leadership (CXO) Portal', text: 'Principals and management ask a question and get a live dashboard.', chips: ['AI Studio', 'Forecast', 'Board report'], cta: 'Explore the leadership portal', link: '/for-leadership', fragment: undefined },
  ];

  readonly stories = [
    { scene: 'eligibility' as const, pill: 'Eligibility', title: 'Rules do the', accent: 'filtering', text: 'Set the criteria once. Every student is checked and shown the reason when not eligible.', points: ['CGPA, backlogs, branch and batch', 'Your placement policy applied automatically', 'Audited include and exclude overrides'] },
    { scene: 'calendar' as const, pill: 'Interviews', title: 'Schedules without', accent: 'clashes', text: 'Shortlist in bulk, schedule rounds and let the calendar catch conflicts.', points: ['Student, panel and room clash checks', 'Passing students advance automatically', 'Reminders 24 hours ahead'] },
    { scene: 'reports' as const, pill: 'Reports', title: 'Numbers ready', accent: 'any day', text: 'Offers update placement status, and the reports keep up on their own.', points: ['Reports, including a NIRF / NAAC-style summary', 'Excel, CSV or PDF', 'Scheduled e-mail exports'] },
    { scene: 'interview' as const, pill: 'AI mock interview', title: 'Practice like a real', accent: 'panel', text: 'Students speak their answers, get a follow-up question and a scored report.', points: ['Technical, HR or mixed', 'Content, relevance, communication and confidence', 'One free interview every 15 days'] },
    { scene: 'test' as const, pill: 'Mock tests and coding', title: 'From easy to', accent: 'hard', text: '20 timed tests on real campus hiring patterns, with coding that actually runs.', points: ['Python, Java, C++ and JavaScript', 'Next test unlocks above 50%', 'Colleges can upload their own tests'] },
  ];

  readonly before: Point[] = [
    { title: 'Eligibility checked by hand', text: 'Someone filters marks, backlogs and branches in Excel for every drive, and mistakes slip through.' },
    { title: 'Updates lost in group chats', text: 'Students miss deadlines and venue changes buried under hundreds of messages.' },
    { title: 'Interview clashes and follow-ups', text: 'Slots are planned on paper, the same student lands in two rooms, and results are typed in later.' },
    { title: 'Reports at the last minute', text: 'Placement numbers for NIRF, NAAC and management are collected again from scattered sheets.' },
  ];
  readonly after: Point[] = [
    { title: 'Eligibility in one click', text: 'Set criteria once; every student is checked and shown the reason when not eligible. Overrides are logged.' },
    { title: 'Every update reaches the right students', text: 'Announcements go to filtered groups in-app and by e-mail, with read receipts and live chat.' },
    { title: 'Rounds and interviews that run themselves', text: 'Bulk scheduling with clash detection, reminders 24 h ahead, and results that advance students to the next round.' },
    { title: 'Reports always ready', text: 'Live dashboard and reports, including a NIRF / NAAC-style summary, exportable to Excel, CSV or PDF.' },
  ];
  readonly startSteps: Point[] = [
    { title: 'Register your college', text: 'Create the placement cell account and verify your e-mail; the ProveYu team then approves the college.' },
    { title: 'Import your students', text: 'Upload the batch from Excel or CSV and invite your coordinators.' },
    { title: 'Publish a drive', text: 'Add the company and criteria. Eligible students are notified by e-mail at once.' },
    { title: 'Students apply and get placed', text: 'Students activate their portal, prepare, apply and track every round to the offer.' },
  ];
}
