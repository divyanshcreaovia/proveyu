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
          const seen = new Set<string>();
          const loaded: ChatMessage[] = [];
          for (const m of res.data.content) {
            const idKey = String(m.id || '');
            if (idKey && seen.has(idKey)) continue;
            if (idKey) seen.add(idKey);
            loaded.push({
              id: m.id,
              text: m.message || '',
              sender: (currentUserId && m.senderId === currentUserId) ? 'recruiter' : 'candidate',
              time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now',
              messageType: m.messageType || (m.fileName ? 'FILE' : 'TEXT'),
              fileName: m.fileName,
              fileSize: m.fileSize,
              fileContentType: m.fileContentType,
              filePath: m.filePath
            });
          }
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
      const isAlreadyShown = this.selectedThread.messages.some(m => 
        (m.id && msgData.id && String(m.id) === String(msgData.id)) ||
        (m.fileName && msgData.fileName && m.fileName === msgData.fileName) ||
        (!m.fileName && !msgData.fileName && (m.text || '').trim() === (msgData.message || '').trim() && m.sender === (isMe ? 'recruiter' : 'candidate'))
      );
      if (!isAlreadyShown) {
        this.selectedThread.messages.push({
          id: msgData.id || Date.now(),
          text: msgData.message || '',
          sender: isMe ? 'recruiter' : 'candidate',
          time: msgData.createdAt ? new Date(msgData.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          messageType: msgData.messageType || (msgData.fileName ? 'FILE' : 'TEXT'),
          fileName: msgData.fileName,
          fileSize: msgData.fileSize,
          fileContentType: msgData.fileContentType,
          filePath: msgData.filePath
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

  onFileSelected(event: any) {
    const file = event.target?.files?.[0];
    if (file) {
      if (file.size > 10 * 1024 * 1024) {
        this.notificationService.showError('File size exceeds the 10MB limit.');
        return;
      }
      this.selectedFile = file;
      this.cdr.detectChanges();
    }
  }

  removeSelectedFile() {
    this.selectedFile = null;
    const input = document.getElementById('chatFileInput') as HTMLInputElement;
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
    this.notificationService.showInfo(`Downloading ${msg.fileName || 'file' }...`);

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
        console.error('File download failed:', err);
        this.notificationService.showError('Could not download file. Please try again.');
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
        console.error('File preview failed:', err);
        if (token) {
          window.open(`http://localhost:8080/api/v1/chat/messages/${msg.id}/file?inline=true&token=${encodeURIComponent(token)}`, '_blank');
        } else {
          this.notificationService.showError('Could not preview file. Please try downloading it instead.');
        }
      }
    });
  }

  sendMessage() {
    if (!this.selectedThread || !this.selectedThread.candidateUuid) return;

    // Check if a file is staged for upload
    if (this.selectedFile) {
      this.sendFileMessage();
      return;
    }

    if (!this.newMessage.trim()) return;

    const sentText = this.newMessage.trim();
    const candUuid = this.selectedThread.candidateUuid;
    const msg: ChatMessage = {
      id: Date.now(),
      text: sentText,
      sender: 'recruiter',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      messageType: 'TEXT'
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

  sendFileMessage() {
    if (!this.selectedThread || !this.selectedThread.candidateUuid || !this.selectedFile) return;

    const candUuid = this.selectedThread.candidateUuid;
    const file = this.selectedFile;
    const caption = this.newMessage.trim();
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    const headers = token ? new HttpHeaders({ 'Authorization': 'Bearer ' + token }) : undefined;

    const formData = new FormData();
    formData.append('receiverId', candUuid);
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

        if (this.selectedThread && res.data) {
          const m = res.data;
          const isAlreadyShown = this.selectedThread.messages.some(existing => 
            (existing.id && m.id && String(existing.id) === String(m.id)) ||
            (existing.fileName && m.fileName && existing.fileName === m.fileName)
          );
          if (!isAlreadyShown) {
            this.selectedThread.messages.push({
              id: m.id,
              text: m.message || '',
              sender: 'recruiter',
              time: m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now',
              messageType: 'FILE',
              fileName: m.fileName,
              fileSize: m.fileSize,
              fileContentType: m.fileContentType,
              filePath: m.filePath
            });
            this.selectedThread.lastMessageTime = 'Just now';
            this.shouldScrollBottom = true;
            this.cdr.detectChanges();
            this.scrollToBottom(true);
          }
          this.triggerToast(`File "${m.fileName}" shared successfully!`);
        }
      },
      error: (err) => {
        this.isUploadingFile = false;
        console.error('Failed to upload file message:', err);
        this.notificationService.showError(err?.error?.message || 'Failed to upload and send file. Please try again.');
      }
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
