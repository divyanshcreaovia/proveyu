import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Scene } from '../../scene';
import { ScrollAnimate } from '../../scroll-animate';
import { Faq } from '../home/home';

/** Public page: the leadership (CXO) portal for principals, directors and management. */
@Component({
  selector: 'app-for-leadership',
  imports: [RouterModule, ScrollAnimate, Scene],
  templateUrl: './for-leadership.html',
})
export class ForLeadership {
  /** Leadership (CXO) portal features. */
  readonly leadership = [
    { title: 'AI Studio', text: 'Type a question in plain words and get a dashboard planned from your college’s own data, with an AI summary.' },
    { title: 'Saved dashboards', text: 'Save, edit and pin the dashboards you use. The numbers stay live.' },
    { title: 'Drill-down', text: 'Click a bar or a number to see the students behind it, and download the list as CSV.' },
    { title: 'Board report', text: 'Take a dashboard to the meeting as a PDF or PowerPoint, without student names.' },
    { title: 'What changed', text: 'The last 7, 30 or 90 days against the period before: placements, offers, applications and practice.' },
    { title: 'Forecast', text: 'Where a batch is likely to finish, from the recent pace, compared with last year.' },
    { title: 'Share link', text: 'A read-only link that expires and can be switched off. No sign-in and no student names.' },
    { title: 'NIRF / NAAC figures', text: 'Placement outcomes for three batches, by branch, as PDF or PowerPoint.' },
    { title: 'How it is calculated', text: 'Every widget shows the definition of its number and the filters in force.' },
  ];

  readonly stories = [
    { scene: 'ask' as const, pill: 'AI Studio', title: 'Ask in plain', accent: 'words', text: 'Type what you want to know. The dashboard is planned for you from a fixed list of placement measures.',
      points: ['Ask about one year, several years or all batches', 'Numbers come from your college’s records', 'A short AI summary of what they show'] },
    { scene: 'boardroom' as const, pill: 'Board meetings', title: 'Walk in with the', accent: 'report', text: 'Take the dashboard on screen to management or share it as a link.',
      points: ['PDF or PowerPoint, with fresh numbers', 'Read-only share link that expires and can be switched off', 'Student names are left out of exports and links'] },
    { scene: 'forecast' as const, pill: 'Looking ahead', title: 'See where the season is', accent: 'heading', text: 'Compare the last period with the one before and project where a batch is likely to finish.',
      points: ['What changed in 7, 30 or 90 days', 'Likely, slower and faster projections', 'Marked low confidence when there is too little recent data'] },
  ];

  readonly access = [
    { title: 'Your college joins ProveYu', text: 'The placement cell registers and runs the season on the platform.' },
    { title: 'An account is created', text: 'A ProveYu admin creates the leadership account for your college.' },
    { title: 'Sign in and ask', text: 'Use the leadership sign-in and ask your first question.' },
  ];

  readonly faqs: Faq[] = [
    { q: 'Who can get a leadership account?', a: 'Principals, directors and management of a college on ProveYu. Accounts are created by a ProveYu admin; there is no self sign-up.' },
    { q: 'Whose data does a dashboard use?', a: 'Only your own college’s. The college comes from the signed-in account, not from the question.' },
    { q: 'Can leadership see student names?', a: 'Signed in, yes: clicking a number shows the students behind it. Shared links and exported reports leave the names out.' },
    { q: 'Does the AI invent numbers?', a: 'No. The AI only picks which measures to show from a fixed list; the figures are calculated from your records, and every widget explains how.' },
  ];
}
