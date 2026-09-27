import { Component, OnInit, OnDestroy, AfterViewChecked, inject, ChangeDetectorRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { RecruiterChatService, CandidateChatThread, ChatMessage } from '../../services/recruiter-chat.service';
import { NotificationService } from '../../../../shared/services/notification.service';
import { ChatWebSocketService } from '../../../../shared/services/chat-websocket.service';

@Component({
  selector: 'app-recruiter-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class Chat implements OnInit, OnDestroy, AfterViewChecked {
  filter: 'all' | 'accepted' | 'pending' = 'all';
  searchQuery = '';
  newMessage = '';

  showPassportModal = false;
  chatThreads: CandidateChatThread[] = [];
  selectedThread: CandidateChatThread | null = null;
  isWsConnected = false;

  @ViewChild('chatScrollContainer') chatScrollContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('scrollBottomAnchor') scrollBottomAnchor?: ElementRef<HTMLDivElement>;
  private shouldScrollBottom = false;

  ngAfterViewChecked() {
    if (this.shouldScrollBottom) {
      this.shouldScrollBottom = false;
      this.scrollToBottom();
    }
  }

  private http = inject(HttpClient);
  private chatWebSocketService = inject(ChatWebSocketService);
  private cdr = inject(ChangeDetectorRef);
  private wsSubscription?: Subscription;
  private wsConnSubscription?: Subscription;

  constructor(
    private recruiterChatService: RecruiterChatService,
    private router: Router,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    // 1. Connect WebSocket
    this.chatWebSocketService.connect();
    this.wsConnSubscription = this.chatWebSocketService.connected$.subscribe(conn => {
      this.isWsConnected = conn;
      this.cdr.detectChanges();
    });

    // 2. Listen to real-time incoming messages via WebSocket
    this.wsSubscription = this.chatWebSocketService.messages$.subscribe(evt => {
      if (evt.event === 'message.new' && evt.data) {
        this.handleRealtimeIncomingMessage(evt.data);
      }
    });

    // 3. Load real backend invitations & candidates
    this.loadBackendInvitations();
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
    this.wsConnSubscription?.unsubscribe();
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

  loadBackendInvitations() {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
    this.http.get<any>('http://localhost:8080/api/v1/recruiter/jobs/invitations', { headers }).subscribe({
      next: (res) => {
        if (res.data && res.data.length > 0) {
          const backendThreads: CandidateChatThread[] = res.data.map((inv: any, idx: number) => {
            const isAccepted = inv.status === 'ACCEPT';
            const initials = inv.candidateName
              ? inv.candidateName.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase()
              : 'CD';

            const createdTime = inv.updatedAt || inv.createdAt;
            const timeStr = createdTime
              ? new Date(createdTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              : 'Recently';

            return {
              id: 100 + idx,
              candidateUuid: inv.candidateId,
              candidateName: inv.candidateName || 'Verified Candidate',
              candidateInitials: initials,
              roleTrack: inv.jobTitle || 'Verified Candidate',
              score: Math.round(inv.scoreSnapshot || 90),
              percentileBadge: (inv.scoreSnapshot && inv.scoreSnapshot >= 90) ? 'Top 3%' : 'Top 5%',
              collegeExp: inv.location ? `${inv.location} · Verified` : 'Verified Candidate',
              location: inv.location || 'Remote',
              status: isAccepted ? 'Accepted' : 'Pending',
              statusText: isAccepted ? 'Invite Accepted' : 'Invite Pending',
              statusClass: isAccepted ? 'confirmed' : 'expires',
              lastMessageTime: timeStr,
              unreadCount: 0,
              passportId: `PASSPORT-2026-${(inv.candidateId || '').substring(0, 4).toUpperCase()}`,
              messages: []
            };
          });

          this.chatThreads = backendThreads;

          // Auto-select the first backend thread
          if (this.chatThreads.length > 0) {
            this.selectedThread = this.chatThreads[0];
            this.recruiterChatService.setSelectedThreadId(this.selectedThread.id);
            if (this.selectedThread.candidateUuid) {
              this.loadBackendThreadMessages(this.selectedThread.candidateUuid);
            }
          } else {
            this.selectedThread = null;
          }
        } else {
          this.chatThreads = [];
          this.selectedThread = null;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.warn('Could not load recruiter invitations for chat:', err);
        this.chatThreads = [];
        this.selectedThread = null;
        this.cdr.detectChanges();
      }
    });
  }

  get filteredThreads(): CandidateChatThread[] {
    return this.chatThreads.filter(thread => {
      const matchesFilter = 
        this.filter === 'all' ||
        (this.filter === 'accepted' && thread.status === 'Accepted') ||
        (this.filter === 'pending' && thread.status === 'Pending');

      const matchesSearch =
        !this.searchQuery ||
        thread.candidateName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        thread.roleTrack.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        thread.location.toLowerCase().includes(this.searchQuery.toLowerCase());

      return matchesFilter && matchesSearch;
    });
  }

  selectThread(thread: CandidateChatThread) {
    this.selectedThread = thread;
    this.recruiterChatService.setSelectedThreadId(thread.id);
    this.shouldScrollBottom = true;
    if (thread.candidateUuid) {
      this.loadBackendThreadMessages(thread.candidateUuid);
    }
  }

  loadBackendThreadMessages(candidateUuid: string) {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
    this.http.get<any>(`http://localhost:8080/api/v1/chat/messages/${candidateUuid}`, { headers }).subscribe({
      next: (res) => {
        if (this.selectedThread && res.data && res.data.content && res.data.content.length > 0) {
          const currentUserId = this.getCurrentUserId();
          const loaded: ChatMessage[] = res.data.content.map((m: any) => ({
            id: m.id,
            text: m.message,
            sender: (currentUserId && m.senderId === currentUserId) ? 'recruiter' : 'candidate',
            time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now'
          }));
          this.selectedThread.messages = loaded;
          this.shouldScrollBottom = true;
          this.cdr.detectChanges();
          this.scrollToBottom(false);
        }
      },
      error: () => {}
    });
  }

  handleRealtimeIncomingMessage(msgData: any) {
    const currentUserId = this.getCurrentUserId();
    const isMe = currentUserId && msgData.senderId === currentUserId;
    const partnerId = isMe ? msgData.receiverId : msgData.senderId;

    // Check if message belongs to currently open thread
    if (this.selectedThread?.candidateUuid && (this.selectedThread.candidateUuid === msgData.senderId || this.selectedThread.candidateUuid === msgData.receiverId)) {
      const alreadyExists = this.selectedThread.messages.some(m => 
        m.id === msgData.id || (m.text === msgData.message && m.sender === (isMe ? 'recruiter' : 'candidate'))
      );
      if (!alreadyExists) {
        this.selectedThread.messages.push({
          id: msgData.id || Date.now(),
          text: msgData.message,
          sender: isMe ? 'recruiter' : 'candidate',
          time: msgData.createdAt ? new Date(msgData.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
        this.selectedThread.lastMessageTime = 'Just now';
        this.shouldScrollBottom = true;
        this.cdr.detectChanges();
        this.scrollToBottom(true);
      }
    } else {
      // Update other thread's badge / last message time
      const otherThread = this.chatThreads.find(t => t.candidateUuid === partnerId);
      if (otherThread) {
        otherThread.lastMessageTime = 'Just now';
        otherThread.unreadCount = (otherThread.unreadCount || 0) + 1;
        this.cdr.detectChanges();
      }
    }
  }

  sendMessage() {
    if (!this.selectedThread || !this.selectedThread.candidateUuid || !this.newMessage.trim()) return;

    const sentText = this.newMessage.trim();
    const candUuid = this.selectedThread.candidateUuid;
    const msg: ChatMessage = {
      id: Date.now(),
      text: sentText,
      sender: 'recruiter',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    this.selectedThread.messages.push(msg);
    this.selectedThread.lastMessageTime = 'Just now';
    this.newMessage = '';
    this.shouldScrollBottom = true;
    this.scrollToBottom(true);

    // 1. Send via WebSocket if open
    const sentViaWs = this.chatWebSocketService.sendMessage(candUuid, sentText);

    // Fallback to REST only if WebSocket is not currently connected
    if (!sentViaWs) {
      const token = sessionStorage.getItem('token') || localStorage.getItem('token');
      const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
      this.http.post('http://localhost:8080/api/v1/chat/messages', {
        receiverId: candUuid,
        message: sentText
      }, { headers }).subscribe({
        next: () => this.scrollToBottom(),
        error: (err) => console.warn('Recruiter chat post warning:', err)
      });
    }
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

  openPassportModal() {
    this.showPassportModal = true;
  }

  closePassportModal() {
    this.showPassportModal = false;
  }

  shortlistAndMoveToHiring(thread: CandidateChatThread) {
    if (!thread) return;
    this.triggerToast(`${thread.candidateName} shortlisted! Moving to Manage Hiring board...`);
    setTimeout(() => {
      this.router.navigate(['/recruiter/manage-hiring'], {
        queryParams: { shortlisted: thread.candidateName }
      });
    }, 800);
  }

  triggerToast(msg: string) {
    this.notificationService.showSuccess(msg);
  }
}
