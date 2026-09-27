import { Component, OnInit, OnDestroy, AfterViewChecked, inject, ChangeDetectorRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { RecruiterJobService } from '../../../recruiter/services/recruiter-job.service';
import { ChatWebSocketService } from '../../../../shared/services/chat-websocket.service';

export interface ChatMessage {
  id: number | string;
  text: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
}

export interface InterviewInvite {
  id: number | string;
  invitationUuid?: string;
  recruiterId?: string;
  candidateId?: string;
  jobId?: string;
  company: string;
  logo: string;
  role: string;
  level: string;
  ctc: string;
  location: string;
  type: string;
  status: 'Pending' | 'Accepted' | 'Declined';
  statusText: string;
  statusClass: string;
  scheduledDate: string;
  scheduledTime: string;
  recruiterInitials: string;
  recruiterName: string;
  recruiterBadge: string;
  recruiterRole: string;
  jobDesc: string;
  requirements: string[];
  topics: string[];
  preScreeningSkipped?: boolean;
  preScreeningText?: string;
  messages: ChatMessage[];
}

@Component({
  selector: 'app-interview-invites',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-invites.html',
  styleUrl: './interview-invites.scss',
})
export class InterviewInvites implements OnInit, OnDestroy, AfterViewChecked {
  private http = inject(HttpClient);
  private recruiterJobService = inject(RecruiterJobService);
  private chatWebSocketService = inject(ChatWebSocketService);
  private cdr = inject(ChangeDetectorRef);
  private wsSubscription?: Subscription;

  filter: 'all' | 'pending' | 'accepted' | 'declined' = 'all';
  searchQuery = '';

  // Toast Notification
  toastMessage = '';
  showToast = false;

  // Job Description Modal state
  showJobDescModal = false;

  interviews: InterviewInvite[] = [];
  selectedInterview: InterviewInvite | null = null;
  newMessage = '';

  @ViewChild('chatScrollContainer') chatScrollContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('scrollBottomAnchor') scrollBottomAnchor?: ElementRef<HTMLDivElement>;
  private shouldScrollBottom = false;

  ngAfterViewChecked() {
    if (this.shouldScrollBottom) {
      this.shouldScrollBottom = false;
      this.scrollToBottom();
    }
  }

  ngOnInit() {
    // 1. Connect WebSocket
    this.chatWebSocketService.connect();

    // 2. Listen to incoming real-time messages via WebSocket
    this.wsSubscription = this.chatWebSocketService.messages$.subscribe(evt => {
      if (evt.event === 'message.new' && evt.data) {
        this.handleRealtimeIncomingMessage(evt.data);
      }
    });

    // 3. Load backend interview invitations
    this.loadBackendInvitations();
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
  }

  getCurrentUserId(): string {
    const stored = sessionStorage.getItem('userId') || localStorage.getItem('userId');
    if (stored) return stored;
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload.sub) {
          sessionStorage.setItem('userId', payload.sub);
          localStorage.setItem('userId', payload.sub);
          return payload.sub;
        }
      } catch {}
    }
    return '';
  }

  handleRealtimeIncomingMessage(msgData: any) {
    if (!this.selectedInterview || !this.selectedInterview.recruiterId) return;

    const currentUserId = this.getCurrentUserId();
    const isMe = currentUserId && msgData.senderId === currentUserId;
    const sender: 'candidate' | 'recruiter' = isMe ? 'candidate' : 'recruiter';

    // Check if message belongs to currently open interview recruiter
    if (msgData.senderId === this.selectedInterview.recruiterId || msgData.receiverId === this.selectedInterview.recruiterId) {
      const alreadyExists = this.selectedInterview.messages.some(m => 
        m.id === msgData.id || (m.text === msgData.message && m.sender === sender)
      );

      if (!alreadyExists) {
        this.selectedInterview.messages.push({
          id: msgData.id || Date.now(),
          text: msgData.message,
          sender,
          time: msgData.createdAt ? new Date(msgData.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
        this.shouldScrollBottom = true;
        this.cdr.detectChanges();
        this.scrollToBottom(true);
      }
    }
  }

  loadBackendInvitations() {
    const candidateId = this.getCurrentUserId();
    this.recruiterJobService.getCandidateInvitations(candidateId || undefined).subscribe({
      next: (res) => {
        if (res.data && res.data.length > 0) {
          const backendList: InterviewInvite[] = res.data.map((inv: any, idx: number) => {
            let status: 'Pending' | 'Accepted' | 'Declined' = 'Pending';
            let statusText = 'Decision Pending';
            let statusClass = 'expires';

            if (inv.status === 'ACCEPT') {
              status = 'Accepted';
              statusText = 'Accepted';
              statusClass = 'confirmed';
            } else if (inv.status === 'DECLINE') {
              status = 'Declined';
              statusText = 'Declined';
              statusClass = 'declined';
            }

            const initials = inv.recruiterName
              ? inv.recruiterName.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase()
              : 'HR';

            const createdDate = inv.createdAt ? new Date(inv.createdAt) : new Date();
            const dateStr = createdDate.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
            const timeStr = createdDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

            const msgs: ChatMessage[] = [
              {
                id: 1,
                text: inv.message || `You have been shortlisted for ${inv.jobTitle || 'Verified Engineering Role'}.`,
                sender: 'recruiter',
                time: timeStr
              }
            ];

            if (status === 'Accepted') {
              msgs.push({
                id: 2,
                text: 'Accepted. Now you can chat',
                sender: 'system',
                time: 'Just now'
              });
            } else if (status === 'Declined') {
              msgs.push({
                id: 2,
                text: 'Request Declined',
                sender: 'system',
                time: 'Just now'
              });
            }

            return {
              id: `INV-${idx + 1}`,
              invitationUuid: inv.id,
              recruiterId: inv.recruiterId,
              candidateId: inv.candidateId,
              jobId: inv.jobId,
              company: inv.companyName || 'Verified Partner',
              logo: 'https://upload.wikimedia.org/wikipedia/commons/4/44/Microsoft_logo.svg',
              role: inv.jobTitle || 'Verified Role',
              level: inv.experienceLevel || 'Mid Level',
              ctc: inv.salaryPackage || 'Pre-vetted Salary',
              location: inv.location || 'Remote',
              type: 'Technical Interview & Evaluation',
              status,
              statusText,
              statusClass,
              scheduledDate: dateStr,
              scheduledTime: timeStr,
              recruiterInitials: initials,
              recruiterName: inv.recruiterName || 'Verified Hiring Lead',
              recruiterBadge: 'Verified Recruiter',
              recruiterRole: inv.recruiterRole || 'Technical Recruiter',
              jobDesc: inv.jobDescription || 'Participate in verified assessment and interview.',
              requirements: inv.requiredSkills ? inv.requiredSkills.split(',').map((s: string) => s.trim()) : ['Problem Solving', 'Engineering Skills'],
              topics: ['Technical Assessment', 'Architecture', 'Problem Solving'],
              messages: msgs
            };
          });

          this.interviews = backendList;
          this.selectedInterview = this.interviews.length > 0 ? this.interviews[0] : null;

          if (this.selectedInterview && this.selectedInterview.status === 'Accepted' && this.selectedInterview.recruiterId) {
            this.loadChatMessages(this.selectedInterview.recruiterId);
          }
        } else {
          this.interviews = [];
          this.selectedInterview = null;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.warn('Could not load candidate backend invitations:', err);
        this.interviews = [];
        this.selectedInterview = null;
        this.cdr.detectChanges();
      }
    });
  }

  get filteredInterviews(): InterviewInvite[] {
    return this.interviews.filter(item => {
      const matchesFilter =
        this.filter === 'all' ||
        (this.filter === 'pending' && item.status === 'Pending') ||
        (this.filter === 'accepted' && item.status === 'Accepted') ||
        (this.filter === 'declined' && item.status === 'Declined');

      const matchesSearch =
        !this.searchQuery ||
        item.company.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        item.role.toLowerCase().includes(this.searchQuery.toLowerCase());

      return matchesFilter && matchesSearch;
    });
  }

  selectInterview(invite: InterviewInvite) {
    this.selectedInterview = invite;
    if (invite.status === 'Accepted' && invite.recruiterId) {
      this.loadChatMessages(invite.recruiterId);
    }
  }

  sendMessage() {
    if (!this.selectedInterview || !this.selectedInterview.recruiterId || !this.newMessage.trim()) return;

    const textToSend = this.newMessage.trim();
    const msg: ChatMessage = {
      id: Date.now(),
      text: textToSend,
      sender: 'candidate',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    this.selectedInterview.messages.push(msg);
    this.newMessage = '';
    this.shouldScrollBottom = true;
    this.scrollToBottom(true);

    // 1. Send via WebSocket for instant push
    const sentViaWs = this.chatWebSocketService.sendMessage(this.selectedInterview.recruiterId, textToSend);

    // 2. Fallback to REST persistence only if WebSocket is not connected
    if (!sentViaWs) {
      const token = sessionStorage.getItem('token') || localStorage.getItem('token');
      const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
      this.http.post('http://localhost:8080/api/v1/chat/messages', {
        receiverId: this.selectedInterview.recruiterId,
        message: textToSend
      }, { headers }).subscribe({
        next: () => this.scrollToBottom(),
        error: (err) => {
          console.warn('Backend chat send warning:', err);
          this.scrollToBottom();
        }
      });
    }
  }

  acceptRequest() {
    if (!this.selectedInterview) return;

    if (this.selectedInterview.invitationUuid) {
      this.recruiterJobService.acceptInvitation(this.selectedInterview.invitationUuid).subscribe({
        next: () => {
          if (!this.selectedInterview) return;
          this.selectedInterview.status = 'Accepted';
          this.selectedInterview.statusText = 'Accepted';
          this.selectedInterview.statusClass = 'confirmed';

          const systemMsg: ChatMessage = {
            id: Date.now(),
            text: 'Accepted. Now you can chat',
            sender: 'system',
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          };
          this.selectedInterview.messages.push(systemMsg);
          this.triggerToast('Interview invite accepted! You can now chat directly with the recruiter.');

          if (this.selectedInterview.recruiterId) {
            this.loadChatMessages(this.selectedInterview.recruiterId);
          }
        },
        error: (err) => {
          console.error('Accept API error:', err);
          if (!this.selectedInterview) return;
          this.selectedInterview.status = 'Accepted';
          this.selectedInterview.statusText = 'Accepted';
          this.selectedInterview.statusClass = 'confirmed';
          this.triggerToast('Interview invite accepted! Direct chat unlocked.');
        }
      });
    } else {
      this.selectedInterview.status = 'Accepted';
      this.selectedInterview.statusText = 'Accepted';
      this.selectedInterview.statusClass = 'confirmed';

      const systemMsg: ChatMessage = {
        id: Date.now(),
        text: 'Accepted. Now you can chat',
        sender: 'system',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      this.selectedInterview.messages.push(systemMsg);
      this.triggerToast('Interview invite accepted! Direct chat unlocked.');
    }
  }

  declineRequest() {
    if (!this.selectedInterview) return;

    if (this.selectedInterview.invitationUuid) {
      this.recruiterJobService.declineInvitation(this.selectedInterview.invitationUuid).subscribe({
        next: () => {
          if (!this.selectedInterview) return;
          this.selectedInterview.status = 'Declined';
          this.selectedInterview.statusText = 'Declined';
          this.selectedInterview.statusClass = 'declined';

          const systemMsg: ChatMessage = {
            id: Date.now(),
            text: 'Request Declined',
            sender: 'system',
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          };
          this.selectedInterview.messages.push(systemMsg);
          this.triggerToast('Interview invite declined.');
        },
        error: (err) => {
          console.error('Decline API error:', err);
          if (!this.selectedInterview) return;
          this.selectedInterview.status = 'Declined';
          this.selectedInterview.statusText = 'Declined';
          this.selectedInterview.statusClass = 'declined';
        }
      });
    } else {
      this.selectedInterview.status = 'Declined';
      this.selectedInterview.statusText = 'Declined';
      this.selectedInterview.statusClass = 'declined';

      const systemMsg: ChatMessage = {
        id: Date.now(),
        text: 'Request Declined',
        sender: 'system',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      this.selectedInterview.messages.push(systemMsg);
      this.triggerToast('Interview invite declined.');
    }
  }

  loadChatMessages(recruiterId: string) {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
    this.http.get<any>(`http://localhost:8080/api/v1/chat/messages/${recruiterId}`, { headers }).subscribe({
      next: (res) => {
        if (this.selectedInterview && res.data && res.data.content && res.data.content.length > 0) {
          const currentUserId = this.getCurrentUserId();
          const loadedMsgs: ChatMessage[] = res.data.content.map((m: any) => {
            const isMe = currentUserId && (m.senderId === currentUserId);
            return {
              id: m.id,
              text: m.message,
              sender: (isMe ? 'candidate' : 'recruiter') as ('candidate' | 'recruiter'),
              time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now'
            };
          });

          // System badge at the beginning
          loadedMsgs.unshift({
            id: 9999,
            text: 'Accepted. Now you can chat',
            sender: 'system',
            time: 'Just now'
          });

          this.selectedInterview.messages = loadedMsgs;
          this.shouldScrollBottom = true;
          this.cdr.detectChanges();
          this.scrollToBottom(false);
        }
      },
      error: (err) => console.warn('Could not load chat messages:', err)
    });
  }

  scrollToBottom(smooth: boolean = false) {
    const doScroll = (behavior: ScrollBehavior) => {
      if (this.scrollBottomAnchor?.nativeElement) {
        this.scrollBottomAnchor.nativeElement.scrollIntoView({ behavior, block: 'end' });
      }
      if (this.chatScrollContainer?.nativeElement) {
        const el = this.chatScrollContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      } else {
        const el = document.querySelector('.chat-scroll-area');
        if (el) el.scrollTop = el.scrollHeight;
      }
    };

    setTimeout(() => doScroll(smooth ? 'smooth' : 'auto'), 30);
    setTimeout(() => doScroll('auto'), 150);
    setTimeout(() => doScroll('auto'), 300);
  }

  openJobDescModal() {
    this.showJobDescModal = true;
  }

  closeJobDescModal() {
    this.showJobDescModal = false;
  }

  triggerToast(msg: string) {
    this.toastMessage = msg;
    this.showToast = true;
    setTimeout(() => {
      this.showToast = false;
    }, 3500);
  }
}
