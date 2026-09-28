import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

export interface ChatMessage {
  id: number | string;
  text: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
}

export interface CandidateChatThread {
  id: string; // Changed to string for UUID
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
  selectedThreadId: string = '';

  constructor(private http: HttpClient) {}

  fetchCandidateThreads(): Observable<CandidateChatThread[]> {
    return this.http.get<any>('http://localhost:8080/api/v1/recruiters/candidates', {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    }).pipe(
      map(res => {
        if (res.success && res.data && res.data.content) {
          const threads = res.data.content.map((c: any) => {
            const initials = c.fullName ? c.fullName.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase() : 'CD';
            return {
              id: c.candidateId,
              candidateName: c.fullName || 'Unknown Candidate',
              candidateInitials: initials,
              roleTrack: c.primaryTrack || c.experienceTrack || '', 
              score: c.scorePercent || 0,
              percentileBadge: c.verificationStatus === 'VERIFIED' ? 'Verified' : 'Pending',
              collegeExp: c.experienceTrack ? c.experienceTrack : '',
              location: c.city || c.location || '',
              status: 'Accepted',
              statusText: 'Active',
              statusClass: 'confirmed',
              lastMessageTime: '',
              passportId: `PASSPORT-${c.candidateId.substring(0,8).toUpperCase()}`,
              messages: []
            };
          });
          this.chatThreads = threads;
          if (threads.length > 0 && !this.selectedThreadId) {
            this.selectedThreadId = threads[0].id;
          }
          return threads;
        }
        return [];
      }),
      catchError(err => {
        console.error('Failed to fetch candidate threads', err);
        return of([]);
      })
    );
  }

  getThreads(): CandidateChatThread[] {
    return this.chatThreads;
  }

  getSelectedThread(): CandidateChatThread {
    const found = this.chatThreads.find(t => t.id === this.selectedThreadId);
    return found || this.chatThreads[0];
  }

  setSelectedThreadId(id: string) {
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
        roleTrack: candidate.primaryTrack || candidate.experienceTrack || '',
        score: candidate.score || 0,
        percentileBadge: candidate.percentileBadge || 'Verified',
        collegeExp: candidate.collegeExp || candidate.experienceTrack || '',
        location: candidate.location || candidate.city || '',
        status: 'Accepted',
        statusText: 'Active',
        statusClass: 'confirmed',
        lastMessageTime: 'Just Now',
        passportId: candidate.passportId || `PASSPORT-${candidate.id.substring(0,8).toUpperCase()}`,
        messages: []
      };
      this.chatThreads.unshift(existing);
    }
    this.selectedThreadId = existing.id;
    return existing;
  }

  // --- API INTEGRATION ---

  fetchMessagesForThread(candidateId: string): Observable<ChatMessage[]> {
    return this.http.get<any>(`http://localhost:8080/api/v1/chat/messages/${candidateId}`, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    }).pipe(
      map(res => {
        if (res.success && res.data && res.data.content) {
          // Backend returns Page<ChatMessageResponse>
          return res.data.content.map((msg: any) => {
            // Determine sender type based on who sent it
            const isRecruiter = msg.senderId !== candidateId;
            return {
              id: msg.id,
              text: msg.message,
              sender: isRecruiter ? 'recruiter' : 'candidate',
              time: new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true })
            };
          }).reverse(); // API returns newest first (sort=createdAt,DESC usually, or we reverse if needed)
        }
        return [];
      }),
      catchError(err => {
        console.error('Failed to fetch messages', err);
        return of([]);
      })
    );
  }

  sendMessage(receiverId: string, text: string): Observable<any> {
    const payload = {
      receiverId: receiverId,
      message: text
    };
    return this.http.post<any>('http://localhost:8080/api/v1/chat/messages', payload, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    });
  }
}

