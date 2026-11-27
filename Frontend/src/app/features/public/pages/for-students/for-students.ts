import { ScrollAnimate } from '../../scroll-animate';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FAQS, Point } from '../home/home';

/** Public page: the student portal, the mock test path and the AI mock interview. */
@Component({
  selector: 'app-for-students',
  imports: [RouterModule, ScrollAnimate],
  templateUrl: './for-students.html',
  styleUrls: ['../home/home.scss'],
})
export class ForStudents {
  activeFaq: number | null = 0;
  /** Student-facing questions: access and AI interview cost. */
  readonly faqs = [FAQS[1], FAQS[4]];

  readonly portal = [
    { icon: 'bi-speedometer2', title: 'Dashboard', text: 'Your next interview, drives closing soon, latest announcements and a placement-readiness checklist.' },
    { icon: 'bi-briefcase', title: 'Campus drives', text: 'See every drive you are eligible for and apply in one click. If you are not eligible, you see exactly why.' },
    { icon: 'bi-globe2', title: 'Off-campus jobs', text: 'Jobs and internships beyond campus drives, picked for the fields you choose. Track each one, and ask your placement cell for a NOC when you get an offer.' },
    { icon: 'bi-list-check', title: 'My applications', text: 'Follow each application round by round, with pass or fail results as soon as they are out.' },
    { icon: 'bi-calendar3', title: 'Interviews', text: 'Your slots with the room or join link, and one click to add them to your calendar.' },
    { icon: 'bi-award', title: 'Offers', text: 'Accept or decline offers online and download the offer letter as PDF.' },
    { icon: 'bi-megaphone', title: 'Announcements & chat', text: 'Updates from the placement cell in one inbox, and live chat with your coordinator.' },
    { icon: 'bi-folder2-open', title: 'Document locker', text: 'Upload your resume, mark sheets and certificates once; the placement cell verifies them.' },
    { icon: 'bi-person-circle', title: 'Profile', text: 'Your academic record from the college, plus your skills, LinkedIn and GitHub.' },
  ];

  readonly interviewSteps: Point[] = [
    { title: 'Choose the interview', text: 'Technical, HR or both; easy, medium or hard. Pick a campus drive, or type a role and paste its job description.' },
    { title: 'Answer out loud', text: 'The interviewer reads each question aloud. You speak your answer (or type it), and get one follow-up on what you said, like a real panel.' },
    { title: 'Get your report', text: 'A few seconds after the last answer: an overall score, a verdict and a coach summary with what to improve.' },
  ];

  readonly reportPoints = [
    'Every answer scored on content, relevance, communication and confidence',
    'What a strong answer would include, for every question',
    'Your transcript and recording to play back',
    'Your speaking speed shown for every spoken answer',
    'Questions stay on the technologies in the job description',
    'Skipped or unanswered questions count as zero, like a real interview',
  ];

  readonly testPoints = [
    '20 timed tests, easy to hard, each modelled on a real campus hiring test pattern',
    'Quantitative aptitude, logical reasoning, verbal ability, technical and coding sections',
    'Write and run code in Python, Java, C++ or JavaScript',
    'The next test unlocks when you score above 50% in the previous one',
    'Section-wise score and an explanation for every question',
    'Extra practice tests your placement cell uploads for a specific drive',
  ];

  readonly activation: Point[] = [
    { title: 'Your college joins ProveYu', text: 'The placement cell adds the batch to its portal.' },
    { title: 'Activate your account', text: 'Enter your college e-mail and roll number, then the 6-digit code we e-mail you.' },
    { title: 'Set a password and start', text: 'Complete your profile, upload documents and take your first mock test.' },
  ];

  toggleFaq(index: number) {
    this.activeFaq = this.activeFaq === index ? null : index;
  }
}
