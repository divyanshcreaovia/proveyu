import { Component, OnInit, OnDestroy, AfterViewChecked, inject, ChangeDetectorRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { RecruiterJobService } from '../../../recruiter/services/recruiter-job.service';
import { ChatWebSocketService } from '../../../../shared/services/chat-websocket.service';

export interface ChatMessage {
  id: number | string;
  text?: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
  messageType?: 'TEXT' | 'FILE';
  filePath?: string;
  fileName?: string;
  fileSize?: number;
  fileContentType?: string;
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
  selectedFile: File | null = null;
  isUploadingFile = false;

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
      const isAlreadyShown = this.selectedInterview.messages.some(m => 
        (m.id && msgData.id && String(m.id) === String(msgData.id)) ||
        (m.fileName && msgData.fileName && m.fileName === msgData.fileName) ||
        (!m.fileName && !msgData.fileName && (m.text || '').trim() === (msgData.message || '').trim() && m.sender === sender)
      );

      if (!isAlreadyShown) {
        this.selectedInterview.messages.push({
          id: msgData.id || Date.now(),
          text: msgData.message || '',
          sender,
          time: msgData.createdAt ? new Date(msgData.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          messageType: msgData.messageType || (msgData.fileName ? 'FILE' : 'TEXT'),
          fileName: msgData.fileName,
          fileSize: msgData.fileSize,
          fileContentType: msgData.fileContentType,
          filePath: msgData.filePath
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
            const rawStatus = (inv.status || '').toUpperCase();
            let status: 'Pending' | 'Accepted' | 'Declined' = 'Pending';
            let statusText = 'Decision Pending';
            let statusClass = 'expires';

            if (rawStatus === 'ACCEPT') {
              status = 'Accepted';
              statusText = 'Accepted';
              statusClass = 'confirmed';
            } else if (rawStatus === 'INTERVIEWING' || rawStatus === 'INTERVIEW') {
              status = 'Accepted';
              statusText = 'Interviewing';
              statusClass = 'confirmed';
            } else if (rawStatus === 'OFFERED') {
              status = 'Accepted';
              statusText = 'Offer Extended';
              statusClass = 'confirmed';
            } else if (rawStatus === 'HIRED' || rawStatus === 'HIERED') {
              status = 'Accepted';
              statusText = 'Hired';
              statusClass = 'confirmed';
            } else if (rawStatus === 'DECLINE' || rawStatus === 'DECLINED') {
              status = 'Declined';
              statusText = 'Declined';
              statusClass = 'declined';
            } else if (rawStatus === 'REJECTED') {
              status = 'Declined';
              statusText = 'Not Selected';
              statusClass = 'declined';
            } else {
              status = 'Pending';
              statusText = 'Decision Pending';
              statusClass = 'expires';
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
              const statusDesc = rawStatus === 'OFFERED' ? 'Offer Extended! Direct communication unlocked.'
                : (rawStatus === 'HIRED' || rawStatus === 'HIERED') ? 'Hired! Welcome to the team.'
                : (rawStatus === 'INTERVIEWING' || rawStatus === 'INTERVIEW') ? 'Interview Stage in progress. Now you can chat'
                : 'Accepted. Now you can chat';
              msgs.push({
                id: 2,
                text: statusDesc,
                sender: 'system',
                time: 'Just now'
              });
            } else if (status === 'Declined') {
              msgs.push({
                id: 2,
                text: rawStatus === 'REJECTED' ? 'Application status: Not selected in interview evaluation.' : 'Request Declined',
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

          // If all invitations are accepted or progressing in hiring pipeline, clear stale invite notifications
          if (backendList.every(i => i.status !== 'Pending')) {
            this.markInviteNotificationsAsRead();
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

  onFileSelected(event: any) {
    const file = event.target?.files?.[0];
    if (file) {
      if (file.size > 10 * 1024 * 1024) {
        this.triggerToast('File size exceeds the 10MB limit.');
        return;
      }
      this.selectedFile = file;
      this.cdr.detectChanges();
    }
  }

  removeSelectedFile() {
    this.selectedFile = null;
    const input = document.getElementById('candidateChatFileInput') as HTMLInputElement;
    if (input) input.value = '';
    this.cdr.detectChanges();
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  getFileIconClass(fileName?: string, contentType?: string): string {
    const ext = fileName ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase() : '';
    if (ext === '.pdf' || contentType?.includes('pdf')) return 'bi-file-earmark-pdf-fill text-danger';
    if (['.jpg', '.jpeg', '.png', '.webp', '.svg'].includes(ext) || contentType?.includes('image')) return 'bi-file-earmark-image-fill text-primary';
    if (['.doc', '.docx'].includes(ext) || contentType?.includes('word')) return 'bi-file-earmark-word-fill text-info';
    if (['.zip', '.rar', '.7z', '.tar', '.gz'].includes(ext) || contentType?.includes('zip')) return 'bi-file-earmark-zip-fill text-warning';
    if (['.xls', '.xlsx', '.csv'].includes(ext) || contentType?.includes('excel')) return 'bi-file-earmark-excel-fill text-success';
    return 'bi-file-earmark-text-fill text-secondary';
  }

  downloadFile(msg: ChatMessage) {
    if (!msg.id) return;
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;
    this.triggerToast(`Downloading ${msg.fileName || 'file'}...`);

    this.http.get(`http://localhost:8080/api/v1/chat/messages/${msg.id}/file`, {
      headers,
      responseType: 'blob'
    }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = msg.fileName || 'attachment';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => window.URL.revokeObjectURL(url), 1000);
      },
      error: (err) => {
        console.error('Candidate file download error:', err);
        this.triggerToast('Could not download file. Please try again.');
      }
    });
  }

  viewFile(msg: ChatMessage) {
    if (!msg.id) return;
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;

    this.http.get(`http://localhost:8080/api/v1/chat/messages/${msg.id}/file?inline=true`, {
      headers,
      responseType: 'blob'
    }).subscribe({
      next: (blob: Blob) => {
        const fileType = msg.fileContentType || blob.type || 'application/octet-stream';
        const fileBlob = new Blob([blob], { type: fileType });
        const fileUrl = window.URL.createObjectURL(fileBlob);
        window.open(fileUrl, '_blank');
        setTimeout(() => window.URL.revokeObjectURL(fileUrl), 60000);
      },
      error: (err) => {
        console.error('Candidate file preview error:', err);
        if (token) {
          window.open(`http://localhost:8080/api/v1/chat/messages/${msg.id}/file?inline=true&token=${encodeURIComponent(token)}`, '_blank');
        } else {
          this.triggerToast('Could not preview file. Please download it.');
        }
      }
    });
  }

  sendMessage() {
    if (!this.selectedInterview || !this.selectedInterview.recruiterId) return;

    if (this.selectedFile) {
      this.sendFileMessage();
      return;
    }

    if (!this.newMessage.trim()) return;

    const textToSend = this.newMessage.trim();
    const msg: ChatMessage = {
      id: Date.now(),
      text: textToSend,
      sender: 'candidate',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      messageType: 'TEXT'
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

  sendFileMessage() {
    if (!this.selectedInterview || !this.selectedInterview.recruiterId || !this.selectedFile) return;

    const recruiterId = this.selectedInterview.recruiterId;
    const file = this.selectedFile;
    const caption = this.newMessage.trim();
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;

    const formData = new FormData();
    formData.append('receiverId', recruiterId);
    formData.append('file', file);
    if (caption) {
      formData.append('message', caption);
    }

    this.isUploadingFile = true;
    this.http.post<any>('http://localhost:8080/api/v1/chat/messages/file', formData, { headers }).subscribe({
      next: (res) => {
        this.isUploadingFile = false;
        this.removeSelectedFile();
        this.newMessage = '';

        if (this.selectedInterview && res.data) {
          const m = res.data;
          const isAlreadyShown = this.selectedInterview.messages.some(existing => 
            (existing.id && m.id && String(existing.id) === String(m.id)) ||
            (existing.fileName && m.fileName && existing.fileName === m.fileName)
          );
          if (!isAlreadyShown) {
            this.selectedInterview.messages.push({
              id: m.id,
              text: m.message || '',
              sender: 'candidate',
              time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now',
              messageType: 'FILE',
              fileName: m.fileName,
              fileSize: m.fileSize,
              fileContentType: m.fileContentType,
              filePath: m.filePath
            });
            this.shouldScrollBottom = true;
            this.cdr.detectChanges();
            this.scrollToBottom(true);
          }
          this.triggerToast(`File "${m.fileName}" shared successfully!`);
        }
      },
      error: (err) => {
        this.isUploadingFile = false;
        console.error('Candidate failed to upload file message:', err);
        this.triggerToast(err?.error?.message || 'Failed to upload and send file. Please try again.');
      }
    });
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
          this.markInviteNotificationsAsRead();
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
          this.markInviteNotificationsAsRead();
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
          const seen = new Set<string>();
          const loadedMsgs: ChatMessage[] = [];
          for (const m of res.data.content) {
            const idKey = String(m.id || '');
            if (idKey && seen.has(idKey)) continue;
            if (idKey) seen.add(idKey);
            const isMe = currentUserId && (m.senderId === currentUserId);
            loadedMsgs.push({
              id: m.id,
              text: m.message || '',
              sender: (isMe ? 'candidate' : 'recruiter') as ('candidate' | 'recruiter'),
              time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now',
              messageType: m.messageType || (m.fileName ? 'FILE' : 'TEXT'),
              fileName: m.fileName,
              fileSize: m.fileSize,
              fileContentType: m.fileContentType,
              filePath: m.filePath
            });
          }

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

  markInviteNotificationsAsRead() {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    if (!token) return;
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.get<any>('http://localhost:8080/api/v1/notifications', { headers }).subscribe({
      next: (res) => {
        if (res && res.data) {
          const unreadInvites = res.data.filter((n: any) => !n.read && n.type === 'INTERVIEW_INVITATION');
          for (const notif of unreadInvites) {
            this.http.put(`http://localhost:8080/api/v1/notifications/${notif.id}/read`, {}, { headers }).subscribe();
          }
        }
      },
      error: () => {}
    });
  }
}
