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
    this.recruiterChatService.fetchCandidateThreads().subscribe(threads => {
      this.chatThreads = threads;
      if (threads.length > 0) {
        this.selectedThread = threads[0];
        this.recruiterChatService.setSelectedThreadId(this.selectedThread.id);
        this.loadMessages();
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
    this.loadMessages();
  }

  loadMessages() {
    this.recruiterChatService.fetchMessagesForThread(this.selectedThread.id).subscribe(msgs => {
      this.selectedThread.messages = msgs;
      this.scrollToBottom();
    });
  }

  scrollToBottom() {
    setTimeout(() => {
      const chatContainer = document.querySelector('.chat-scroll-area');
      if (chatContainer) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      }
    }, 100);
  }

  sendMessage() {
    if (!this.selectedThread || !this.newMessage.trim()) return;

    const msgText = this.newMessage.trim();
    this.newMessage = ''; // clear input immediately for snappy UI

    // Optimistic UI update: push temporary message
    const tempId = 'temp-' + Date.now();
    const newMsg: ChatMessage = {
      id: tempId,
      text: msgText,
      sender: 'recruiter',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true })
    };
    this.selectedThread.messages.push(newMsg);
    this.scrollToBottom();

    // Send to backend
    this.recruiterChatService.sendMessage(this.selectedThread.id, msgText).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          // Replace temp ID with real DB ID
          const msg = this.selectedThread.messages.find(m => m.id === tempId);
          if (msg) {
            msg.id = res.data.id;
          }
        }
      },
      error: () => {
        this.triggerToast('Error connecting to chat server. Message may not have been sent.');
        // Optionally remove the message if it failed
        this.selectedThread.messages = this.selectedThread.messages.filter(m => m.id !== tempId);
      }
    });
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
