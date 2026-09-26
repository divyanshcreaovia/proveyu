import { Injectable } from '@angular/core';

export interface ChatMessage {
  id: number;
  text: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
}

export interface CandidateChatThread {
  id: number;
  candidateName: string;
  candidateInitials: string;
  roleTrack: string;
  score: number;
  percentileBadge: string;
  collegeExp: string;
  location: string;
  status: 'Accepted' | 'Pending';
  statusText: string;
  statusClass: string;
  lastMessageTime: string;
  unreadCount?: number;
  passportId: string;
  messages: ChatMessage[];
}

@Injectable({
  providedIn: 'root',
})
export class RecruiterChatService {
  private chatThreads: CandidateChatThread[] = [
    {
      id: 1,
      candidateName: 'Rahul Sharma',
      candidateInitials: 'RS',
      roleTrack: 'Associate QA Automation Engineer',
      score: 92,
      percentileBadge: 'Top 3%',
      collegeExp: 'RV College of Engineering · Fresher (2026)',
      location: 'Bengaluru',
      status: 'Accepted',
      statusText: 'Invite Accepted',
      statusClass: 'confirmed',
      lastMessageTime: '10:02 AM',
      passportId: 'PASSPORT-2026-8921',
      messages: [
        { id: 1, text: 'Hi Rahul! Thanks for sharing your ProveYu Skill Passport. Your QA automation lab scores are impressive!', sender: 'recruiter', time: '10:00 AM' },
        { id: 2, text: 'Hello! Thank you. I completed the live Selenium & Cypress invigilation test last week.', sender: 'candidate', time: '10:01 AM' },
        { id: 3, text: 'Great! We would love to discuss a potential offer for CloudScale Technologies.', sender: 'recruiter', time: '10:02 AM' }
      ]
    },
    {
      id: 2,
      candidateName: 'Priya Nambiar',
      candidateInitials: 'PN',
      roleTrack: 'QA & Automation Testing',
      score: 95,
      percentileBadge: 'Top 1%',
      collegeExp: 'PES University · Fresher (2026)',
      location: 'Hyderabad',
      status: 'Accepted',
      statusText: 'Invite Accepted',
      statusClass: 'confirmed',
      lastMessageTime: 'Yesterday',
      passportId: 'PASSPORT-2026-9501',
      messages: [
        { id: 1, text: 'Hi Priya, your score of 95 in QA Automation is top tier. Are you open for hybrid roles in Hyderabad?', sender: 'recruiter', time: 'Yesterday' },
        { id: 2, text: 'Yes, I am actively looking for opportunities starting this month.', sender: 'candidate', time: 'Yesterday' }
      ]
    },
    {
      id: 3,
      candidateName: 'Candidate #CF-7412',
      candidateInitials: 'CF',
      roleTrack: 'Java Spring Boot Developer',
      score: 89,
      percentileBadge: 'Top 5%',
      collegeExp: 'BMS College of Engineering · 1-3 Yrs Exp',
      location: 'Bengaluru',
      status: 'Pending',
      statusText: 'Invite Pending',
      statusClass: 'expires',
      lastMessageTime: 'Sep 22',
      passportId: 'PASSPORT-2026-7412',
      messages: [
        { id: 1, text: 'Interview invite sent for Junior Java Spring Boot Developer position.', sender: 'system', time: 'Sep 22' },
        { id: 2, text: 'Awaiting candidate response to unlock live chat.', sender: 'system', time: 'Sep 22' }
      ]
    },
    {
      id: 4,
      candidateName: 'Amitav Ghosh',
      candidateInitials: 'AG',
      roleTrack: 'Python Backend & Data',
      score: 86,
      percentileBadge: 'Top 10%',
      collegeExp: 'IIT Kanpur · Fresher (2025)',
      location: 'Delhi NCR',
      status: 'Pending',
      statusText: 'Invite Pending',
      statusClass: 'expires',
      lastMessageTime: 'Sep 20',
      passportId: 'PASSPORT-2026-8640',
      messages: [
        { id: 1, text: 'Interview invite sent for Python Backend Engineer position.', sender: 'system', time: 'Sep 20' },
        { id: 2, text: 'Awaiting candidate response to unlock live chat.', sender: 'system', time: 'Sep 20' }
      ]
    }
  ];

  selectedThreadId: number = 1;

  getThreads(): CandidateChatThread[] {
    return this.chatThreads;
  }

  getSelectedThread(): CandidateChatThread {
    const found = this.chatThreads.find(t => t.id === this.selectedThreadId);
    return found || this.chatThreads[0];
  }

  setSelectedThreadId(id: number) {
    this.selectedThreadId = id;
  }

  addCandidateToChat(candidate: any): CandidateChatThread {
    let existing = this.chatThreads.find(t => t.id === candidate.id || t.candidateName === candidate.name);
    if (!existing) {
      const initials = candidate.name.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase() || 'CD';
      existing = {
        id: candidate.id,
        candidateName: candidate.name,
        candidateInitials: initials,
        roleTrack: candidate.primaryTrack || 'Software Engineer',
        score: candidate.score || 85,
        percentileBadge: candidate.percentileBadge || 'Top 5%',
        collegeExp: candidate.collegeExp || 'Verified Candidate',
        location: candidate.location || 'Remote',
        status: 'Accepted',
        statusText: 'Invite Accepted',
        statusClass: 'confirmed',
        lastMessageTime: 'Just Now',
        passportId: candidate.passportId || 'PASSPORT-2026-NEW',
        messages: [
          { id: 1, text: `Candidate ${candidate.name} shortlisted for recruiter interview chat.`, sender: 'system', time: 'Just Now' }
        ]
      };
      this.chatThreads.unshift(existing);
    }
    this.selectedThreadId = existing.id;
    return existing;
  }
}
