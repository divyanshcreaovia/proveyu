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
    q: 'What does the AI mock interview cost students?',
    a: 'Each student gets one free AI interview every 15 days. Extra interviews cost ₹20 each, paid by UPI, and never expire.',
  },
];

@Component({
  selector: 'app-home',
  imports: [RouterModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  isHeroCardFlipped = true;
  activeFaq: number | null = 0;
  readonly faqs = FAQS;

  /** Illustrative numbers for the hero card (labelled "Sample data" on the page). */
  readonly funnel = [
    { label: 'Eligible', count: 320, pct: 100, bar: 'bg-primary' },
    { label: 'Applied', count: 248, pct: 78, bar: 'bg-primary' },
    { label: 'Shortlisted', count: 96, pct: 30, bar: 'bg-info' },
    { label: 'Offers', count: 31, pct: 10, bar: 'bg-success' },
  ];

  readonly products = [
    {
      tag: 'For placement cells', title: 'College Placement Portal', icon: 'bi-building', tone: 'tone-blue',
      text: 'The TPO and coordinators run the whole season: students, companies, drives, rounds, interviews, offers and reports.',
      points: ['Bulk student import with duplicate checks', 'Eligibility rules and placement policy built in', 'Interview calendar that blocks clashes', 'NIRF / NAAC-ready reports'],
      cta: 'Explore the college portal', link: '/for-colleges', fragment: undefined as string | undefined,
    },
    {
      tag: 'For students', title: 'Student Portal', icon: 'bi-mortarboard', tone: 'tone-green',
      text: 'Students see the drives open to them, apply in one click and follow every round until the offer.',
      points: ['Eligible drives with the reason when not eligible', 'Round-by-round results and interview slots', 'Offer letters, announcements and chat with the cell', '20 mock tests and AI mock interviews'],
      cta: 'Explore the student portal', link: '/for-students', fragment: undefined,
    },
    {
      tag: 'For recruiters', title: 'Company Drive Room', icon: 'bi-briefcase', tone: 'tone-violet',
      text: 'Invited company HR and panels evaluate inside one drive, on the same data as the placement cell.',
      points: ['Applicants, resumes and filters', 'Shortlist, run rounds and fill panel scorecards', 'Release offers and upload offer letters', 'You decide which student details they see'],
      cta: 'How the Drive Room works', link: '/for-colleges', fragment: 'drive-room',
    },
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
    { title: 'Reports always ready', text: 'Live dashboard and 7 reports, including a NIRF / NAAC-style summary, exportable to Excel, CSV or PDF.' },
  ];

  readonly collegeHighlights: Point[] = [
    { title: 'Student master that stays clean', text: 'Import the batch from Excel or CSV with a dry run and duplicate detection, verify documents, mark debarred or opted-out students.' },
    { title: 'Placement policy enforced automatically', text: 'One-offer, dream upgrade, maximum applications and minimum hike rules are checked on every application and offer.' },
    { title: 'Your team, your rules', text: 'Admin, coordinator and viewer roles with branch scope, plus an audit log of every change.' },
  ];

  readonly driveSteps: Point[] = [
    { title: 'Create and publish the drive', text: 'Company, role, CTC, criteria and rounds. Clone last year\'s drive in seconds.' },
    { title: 'Eligible students are notified and apply', text: 'Eligible students get an e-mail and apply in one click; others see exactly why they are not eligible.' },
    { title: 'Shortlist and run the rounds', text: 'Bulk shortlist, upload results from Excel, and passing students move on automatically.' },
    { title: 'Schedule interviews without clashes', text: 'The calendar blocks student, panel and room clashes and reminds everyone.' },
    { title: 'Record offers, update placement status', text: 'Offer letters as PDF; students accept or decline in their portal, and placed counts update themselves.' },
  ];

  readonly startSteps: Point[] = [
    { title: 'Register your college', text: 'Create the placement cell account and verify your e-mail; the ProveYu team then approves the college.' },
    { title: 'Import your students', text: 'Upload the batch from Excel or CSV and invite your coordinators.' },
    { title: 'Publish a drive', text: 'Add the company and criteria. Eligible students are notified by e-mail at once.' },
    { title: 'Students apply and get placed', text: 'Students activate their portal, prepare, apply and track every round to the offer.' },
  ];

  toggleHeroFlip() {
    this.isHeroCardFlipped = !this.isHeroCardFlipped;
  }

  toggleFaq(index: number) {
    this.activeFaq = this.activeFaq === index ? null : index;
  }
}
