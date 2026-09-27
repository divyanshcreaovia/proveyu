import { Injectable } from '@angular/core';

export interface ChatMessage {
  id: number;
  text: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
}

export interface CandidateChatThread {
  id: number;
  candidateUuid?: string;
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
  private chatThreads: CandidateChatThread[] = [];
  selectedThreadId: number | null = null;

  getThreads(): CandidateChatThread[] {
    return this.chatThreads;
  }

  setThreads(threads: CandidateChatThread[]) {
    this.chatThreads = threads;
  }

  getSelectedThread(): CandidateChatThread | null {
    if (this.selectedThreadId !== null) {
      const found = this.chatThreads.find(t => t.id === this.selectedThreadId);
      if (found) return found;
    }
    return this.chatThreads.length > 0 ? this.chatThreads[0] : null;
  }

  setSelectedThreadId(id: number | null) {
    this.selectedThreadId = id;
  }

  addCandidateToChat(candidate: any): CandidateChatThread {
    let existing = this.chatThreads.find(t => t.candidateUuid === candidate.id || t.id === candidate.id || t.candidateName === candidate.name);
    if (!existing) {
      const initials = candidate.name ? candidate.name.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase() : 'CD';
      existing = {
        id: typeof candidate.id === 'number' ? candidate.id : Date.now(),
        candidateUuid: typeof candidate.id === 'string' ? candidate.id : undefined,
        candidateName: candidate.name || 'Verified Candidate',
        candidateInitials: initials,
        roleTrack: candidate.primaryTrack || candidate.role || 'Software Engineer',
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
