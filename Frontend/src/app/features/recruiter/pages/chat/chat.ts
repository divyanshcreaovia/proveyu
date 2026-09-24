import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RecruiterChatService, CandidateChatThread, ChatMessage } from '../../services/recruiter-chat.service';
import { NotificationService } from '../../../../shared/services/notification.service';

@Component({
  selector: 'app-recruiter-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class Chat implements OnInit {
  filter: 'all' | 'accepted' | 'pending' = 'all';
  searchQuery = '';
  newMessage = '';

  showPassportModal = false;
  chatThreads: CandidateChatThread[] = [];
  selectedThread!: CandidateChatThread;

  constructor(
    private recruiterChatService: RecruiterChatService,
    private router: Router,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    this.chatThreads = this.recruiterChatService.getThreads();
    this.selectedThread = this.recruiterChatService.getSelectedThread();
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
  }

  sendMessage() {
    if (!this.selectedThread || !this.newMessage.trim()) return;

    const msg: ChatMessage = {
      id: Date.now(),
      text: this.newMessage.trim(),
      sender: 'recruiter',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    this.selectedThread.messages.push(msg);
    this.newMessage = '';

    setTimeout(() => {
      const chatContainer = document.querySelector('.chat-scroll-area');
      if (chatContainer) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      }
    }, 50);
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
