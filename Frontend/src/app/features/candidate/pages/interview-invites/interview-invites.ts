import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface ChatMessage {
  id: number;
  text: string;
  sender: 'recruiter' | 'candidate' | 'system';
  time: string;
  isBoldPassport?: boolean;
}

export interface InterviewInvite {
  id: number;
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
export class InterviewInvites implements OnInit {
  filter: 'all' | 'pending' | 'accepted' | 'declined' = 'all';
  searchQuery = '';

  // Toast Notification
  toastMessage = '';
  showToast = false;

  // Job Description Modal state
  showJobDescModal = false;

  interviews: InterviewInvite[] = [
    {
      id: 1,
      company: 'Google',
      logo: 'https://upload.wikimedia.org/wikipedia/commons/c/c1/Google_%22G%22_logo.svg',
      role: 'SDE-1 (Algorithms & Backend)',
      level: 'Junior / Mid Level',
      ctc: '₹22 – ₹28 LPA',
      location: 'Bengaluru / Remote',
      type: 'Technical Video Interview (60 Min)',
      status: 'Pending',
      statusText: 'Expires in 3 days',
      statusClass: 'expires',
      scheduledDate: '2024-09-26',
      scheduledTime: '02:00 PM',
      recruiterInitials: 'SS',
      recruiterName: 'Sarah Smith',
      recruiterBadge: 'Verified Recruiter',
      recruiterRole: 'Senior Technical Recruiter at Google',
      jobDesc: 'Join Google Software Engineering team to build high-scale distributed systems and core search backend infrastructure.',
      requirements: ['Data Structures & Algorithms', 'C++, Java, or Python', 'System Design fundamentals'],
      topics: ['Arrays & Graphs', 'Dynamic Programming', 'Concurrency'],
      messages: [
        { id: 1, text: 'Hi Rahul! Thanks for sharing your ProveYu Skill Passport. Your DSA evaluation scores are impressive!', sender: 'recruiter', time: '10:00 AM' },
        { id: 2, text: 'We would love to invite you for a 60-minute technical interview for the SDE-1 position.', sender: 'recruiter', time: '10:02 AM' }
      ]
    },
    {
      id: 2,
      company: 'Microsoft',
      logo: 'https://upload.wikimedia.org/wikipedia/commons/4/44/Microsoft_logo.svg',
      role: 'Software Engineer - Azure Cloud',
      level: 'Mid Level',
      ctc: '₹26 – ₹32 LPA',
      location: 'Hyderabad (Hybrid)',
      type: 'System Design & Coding (90 Min)',
      status: 'Accepted',
      statusText: 'Confirmed: Sep 25, 11 AM',
      statusClass: 'confirmed',
      scheduledDate: '2024-09-25',
      scheduledTime: '11:00 AM',
      recruiterInitials: 'AV',
      recruiterName: 'Ananya Verma',
      recruiterBadge: 'Verified HM',
      recruiterRole: 'Talent Acquisition Lead at Microsoft',
      jobDesc: 'Architect cloud native microservices on Azure. Focus on scalability, fault tolerance, and API integration.',
      requirements: ['C# / .NET or Java', 'Azure Cloud Platform', 'REST APIs & Microservices'],
      topics: ['System Architecture', 'Database Sharding', 'Azure Services'],
      messages: [
        { id: 1, text: 'Hello Rahul, congratulations on passing the Microsoft Azure assessment on ProveYu!', sender: 'recruiter', time: 'Yesterday' },
        { id: 2, text: 'Interview confirmed for Sept 25th at 11:00 AM IST.', sender: 'system', time: 'Yesterday' }
      ]
    },
    {
      id: 3,
      company: 'Amazon',
      logo: 'https://upload.wikimedia.org/wikipedia/commons/a/a9/Amazon_logo.svg',
      role: 'Frontend Engineer (React / Next.js)',
      level: 'Senior Level',
      ctc: '₹30 – ₹38 LPA',
      location: 'Bengaluru / Hybrid',
      type: 'Live Coding & Architecture Walkthrough (45 Min)',
      status: 'Pending',
      statusText: 'Expires in 1 day',
      statusClass: 'expires',
      scheduledDate: 'Saturday, Sep 28th',
      scheduledTime: '04:00 PM',
      recruiterInitials: 'VM',
      recruiterName: 'Vikram Malhotra',
      recruiterBadge: 'Verified HM',
      recruiterRole: 'Engineering Manager at Amazon Prime Video',
      jobDesc: 'Lead frontend engineering for high-performance video streaming web applications using React, TypeScript and Web Performance optimization techniques.',
      requirements: ['5+ years React / TypeScript experience', 'Web Performance Optimization', 'Amazon Leadership Principles'],
      topics: ['Frontend Architecture', 'State Management', 'React Performance'],
      messages: [
        { id: 1, text: 'Hi Rahul, Amazon Prime Video team is looking for a Senior Frontend Engineer.', sender: 'recruiter', time: '09:30 AM' }
      ]
    }
  ];

  // Default to Amazon (Invite #3) as shown in reference screenshot
  selectedInterview: InterviewInvite = this.interviews[2];
  newMessage = '';

  ngOnInit() {
    this.selectedInterview = this.interviews[2];
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
  }

  sendMessage() {
    if (!this.selectedInterview) return;

    if (this.newMessage.trim()) {
      const msg: ChatMessage = {
        id: Date.now(),
        text: this.newMessage.trim(),
        sender: 'candidate',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      this.selectedInterview.messages.push(msg);
      this.newMessage = '';

      setTimeout(() => {
        const chatContainer = document.querySelector('.chat-scroll-area');
        if (chatContainer) {
          chatContainer.scrollTop = chatContainer.scrollHeight;
        }
      }, 50);
    }
  }

  acceptRequest() {
    if (!this.selectedInterview) return;

    this.selectedInterview.status = 'Accepted';
    this.selectedInterview.statusText = `Confirmed: ${this.selectedInterview.scheduledDate}`;
    this.selectedInterview.statusClass = 'confirmed';

    const systemMsg: ChatMessage = {
      id: Date.now(),
      text: `🎉 You accepted the interview invitation from ${this.selectedInterview.company} for ${this.selectedInterview.scheduledDate} at ${this.selectedInterview.scheduledTime}. Calendar invite sent to your registered email!`,
      sender: 'system',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    this.selectedInterview.messages.push(systemMsg);

    this.triggerToast(`Interview request accepted with ${this.selectedInterview.company}! Direct chat unlocked.`);
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
