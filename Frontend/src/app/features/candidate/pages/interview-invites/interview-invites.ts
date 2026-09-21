import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-interview-invites',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-invites.html',
  styleUrl: './interview-invites.scss',
})
export class InterviewInvites {
  filter = 'all';
  
  interviews = [
    { id: 1, company: 'Google', logo: 'https://upload.wikimedia.org/wikipedia/commons/c/c1/Google_%22G%22_logo.svg', role: 'SDE-1', level: 'Junior', status: 'Pending', statusText: 'Expires in 3 days', statusClass: 'bg-warning text-dark' },
    { id: 2, company: 'Microsoft', logo: 'https://upload.wikimedia.org/wikipedia/commons/4/44/Microsoft_logo.svg', role: 'Software Engineer', level: 'Mid', status: 'Accepted', statusText: 'Interview: Sep 25, 11 AM', statusClass: 'bg-success text-white' },
    { id: 3, company: 'Amazon', logo: 'https://upload.wikimedia.org/wikipedia/commons/a/a9/Amazon_logo.svg', role: 'Frontend Developer', level: 'Senior', status: 'Pending', statusText: 'Expires in 1 day', statusClass: 'bg-warning text-dark' }
  ];

  selectedInterview = this.interviews[0];

  messages = [
    { text: 'Hi Rahul, thanks for applying to Google! We are impressed by your profile.', sender: 'recruiter', time: '10:00 AM' },
    { text: 'Can you interview on September 24th at 2:00 PM?', sender: 'recruiter', time: '10:02 AM' },
    { text: 'Hi! Yes, that time works perfectly for me.', sender: 'candidate', time: '10:15 AM' },
    { text: 'Great. I will send over the meeting invite and preparation materials shortly.', sender: 'recruiter', time: '10:20 AM' }
  ];

  newMessage = '';

  selectInterview(invite: any) {
    this.selectedInterview = invite;
  }

  sendMessage() {
    if (this.newMessage.trim()) {
      this.messages.push({
        text: this.newMessage,
        sender: 'candidate',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      });
      this.newMessage = '';
    }
  }
}
