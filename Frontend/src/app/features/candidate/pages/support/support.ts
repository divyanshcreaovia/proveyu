import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface CandidateTicketReply {
  id: string;
  sender: 'user' | 'support';
  senderName: string;
  senderRole: string;
  avatar?: string;
  timestamp: string;
  message: string;
}

export interface CandidateSupportTicket {
  id: string;
  subject: string;
  category: string;
  priority: 'Low' | 'Medium' | 'High' | 'Urgent';
  status: 'Open' | 'In Progress' | 'Resolved';
  createdAt: string;
  updatedAt: string;
  examRef?: string;
  description: string;
  repliesCount: number;
  messages: CandidateTicketReply[];
}

interface FAQ {
  question: string;
  answer: string;
  category: string;
  isOpen?: boolean;
}

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './support.html',
  styleUrl: './support.scss',
})
export class Support {
  searchQuery: string = '';
  selectedCategory: string = 'All';
  activeTicketFilter: string = 'all';
  replyInputText: string = '';
  showNewTicketModal: boolean = false;
  selectedTicket: CandidateSupportTicket | null = null;
  toastMessage: string | null = null;

  // Candidate Helpdesk Lead Details
  helpdeskLead = {
    name: 'Priya Sharma',
    role: 'Senior Candidate Support Lead',
    email: 'support@proveyu.com',
    phone: '1800-419-PROVEYU',
    avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=300&auto=format&fit=crop&q=80',
    status: 'Available Now',
    availability: 'Mon - Sat | 9:00 AM - 6:00 PM IST',
    avgResponseTime: '< 2 hrs'
  };

  // Sample Candidate Tickets List
  tickets: CandidateSupportTicket[] = [
    {
      id: 'TK-CND-4821',
      subject: 'Admit Card Generation Delay for Upcoming React Assessment',
      category: 'Admit Card & Venue',
      priority: 'High',
      status: 'In Progress',
      createdAt: 'Today, 10:15 AM',
      updatedAt: '40 mins ago',
      examRef: 'PY-EX-9920 (React Dev Standard)',
      description: 'Booked exam for 26th Sep at iON Digital Center Bangalore. Need urgent admit card download link.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-1',
          sender: 'user',
          senderName: 'Rahul Sharma',
          senderRole: 'Candidate',
          avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80',
          timestamp: 'Today, 10:15 AM',
          message: 'Booked exam for 26th Sep at iON Digital Center Bangalore. Need urgent admit card download link.'
        },
        {
          id: 'REP-2',
          sender: 'support',
          senderName: 'Priya Sharma',
          senderRole: 'Candidate Support Desk',
          avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=300&auto=format&fit=crop&q=80',
          timestamp: '40 mins ago',
          message: 'Hi Rahul, your Admit Card is generated 48 hours before the exam date. It will be unlocked on your dashboard at 09:00 AM tomorrow.'
        }
      ]
    },
    {
      id: 'TK-CND-4790',
      subject: 'Slot Reschedule Confirmation for Fullstack Node.js Benchmark',
      category: 'Exam Booking & Slots',
      priority: 'Medium',
      status: 'Resolved',
      createdAt: '21 Sep 2026',
      updatedAt: '21 Sep 2026',
      description: 'Requested slot shift from morning session to afternoon 2:00 PM batch due to travel.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-10',
          sender: 'user',
          senderName: 'Rahul Sharma',
          senderRole: 'Candidate',
          avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80',
          timestamp: '21 Sep 2026',
          message: 'Requested slot shift from morning session to afternoon 2:00 PM batch due to travel.'
        },
        {
          id: 'REP-11',
          sender: 'support',
          senderName: 'ProveYu Test Desk',
          senderRole: 'Slot Coordinator',
          timestamp: '21 Sep 2026',
          message: 'Slot successfully updated to 2:00 PM - 5:00 PM session at certified partner test center.'
        }
      ]
    }
  ];

  ticketForm = {
    name: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    category: 'Exam Booking & Slot Allotment',
    priority: 'Medium' as 'Low' | 'Medium' | 'High' | 'Urgent',
    subject: '',
    message: ''
  };

  popularTags = [
    'Admit Card',
    'Reschedule',
    'Test Center',
    'Scorecard',
    'Verification',
    'Technical Issues'
  ];

  browseTopics = [
    { title: 'Exam Booking & Slots', desc: 'Booking, reschedule, slot issues', icon: 'bi-calendar-event-fill', bg: '#f0ebff', color: '#6366f1' },
    { title: 'Admit Card & Test Venue', desc: 'Download, ID proof, center details', icon: 'bi-file-earmark-text-fill', bg: '#e6f9f0', color: '#10b981' },
    { title: 'Skill Passport & Scores', desc: 'Scorecard, percentile, verification', icon: 'bi-bar-chart-fill', bg: '#f3e8ff', color: '#8b5cf6' },
    { title: 'Technical & Proctoring', desc: 'System requirements, AI-proctoring, issues', icon: 'bi-laptop-fill', bg: '#fff0e6', color: '#f97316' },
    { title: 'Account & Profile', desc: 'Profile, documents, mobile/email update', icon: 'bi-person-fill', bg: '#e6f4fe', color: '#0284c7' },
    { title: 'Other Queries', desc: 'General questions, feedback', icon: 'bi-chat-left-dots-fill', bg: '#fdebf7', color: '#ec4899' }
  ];

  filterCategories: string[] = ['All', 'Exam Booking', 'Admit Card', 'Scores', 'Technical', 'Account'];

  categories = [
    { id: 'all', label: 'All Topics', icon: 'bi-grid-fill' },
    { id: 'exam', label: 'Exam Booking & Slots', icon: 'bi-calendar-check-fill' },
    { id: 'admit', label: 'Admit Card & Test Venue', icon: 'bi-card-heading' },
    { id: 'scores', label: 'Skill Passport & Scores', icon: 'bi-patch-check-fill' },
    { id: 'technical', label: 'Technical & Proctoring', icon: 'bi-cpu-fill' }
  ];

  faqs: FAQ[] = [
    {
      question: 'When will my Admit Card be generated after booking an exam slot?',
      answer: 'Your Admit Card is generated 48 hours prior to your exam date. You will receive an email and SMS alert, and it will also be available for instant download on your Candidate Dashboard under "Next Scheduled Exam" and "Skill Passport" pages.',
      category: 'Admit Card',
      isOpen: true
    },
    {
      question: 'What documents are allowed at the test center?',
      answer: 'Candidates must carry a physical original copy of Aadhar Card, PAN Card, Voter ID, or Passport along with the printed Admit Card. Soft copies on mobile phones are strictly not permitted.',
      category: 'Admit Card',
      isOpen: false
    },
    {
      question: 'Can I reschedule my booked assessment slot?',
      answer: 'Yes, you can reschedule your exam up to 24 hours prior to the scheduled slot time directly from your Overview Dashboard by clicking on "Reschedule Slot". No rescheduling fee is charged for the first attempt.',
      category: 'Exam Booking',
      isOpen: false
    },
    {
      question: 'How is the Skill Passport percentile calculated?',
      answer: 'Skill Passport scores are benchmarked across all candidates taking standardized domain tests nationwide. Percentiles reflect your relative standing among all test takers in the last 12 months.',
      category: 'Scores',
      isOpen: false
    },
    {
      question: 'How do recruiters contact candidates after verified test scores?',
      answer: 'Top hiring partners review candidate Skill Passports. When your verified percentile matches an open role, recruiters send direct Interview Invites via your "Interview Invites" section.',
      category: 'Scores',
      isOpen: false
    },
    {
      question: 'What happens if there is a technical power outage during my in-person assessment?',
      answer: 'All partner test centers feature uninterruptible power supply (UPS) and backup servers. Your test state is auto-saved every 30 seconds, so you will resume exactly from the last saved question without loss of time.',
      category: 'Technical',
      isOpen: false
    }
  ];

  get filteredFaqs(): FAQ[] {
    return this.faqs.filter(faq => {
      const matchesCategory = this.selectedCategory === 'All' || faq.category.toLowerCase().includes(this.selectedCategory.toLowerCase());
      const matchesSearch = !this.searchQuery ||
        faq.question.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        faq.answer.toLowerCase().includes(this.searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }

  get filteredTickets(): CandidateSupportTicket[] {
    if (this.activeTicketFilter === 'all') return this.tickets;
    return this.tickets.filter(t => t.status.toLowerCase() === this.activeTicketFilter.toLowerCase());
  }

  toggleFaq(faq: FAQ) {
    faq.isOpen = !faq.isOpen;
  }

  setPopularTag(tag: string) {
    this.searchQuery = tag;
  }

  openNewTicketModal() {
    this.showNewTicketModal = true;
  }

  closeNewTicketModal() {
    this.showNewTicketModal = false;
    this.ticketForm.subject = '';
    this.ticketForm.message = '';
  }

  submitTicket() {
    if (!this.ticketForm.subject || !this.ticketForm.message) {
      this.showToast('Please fill in the subject and message.');
      return;
    }

    const createdId = `TK-CND-${Math.floor(1000 + Math.random() * 9000)}`;
    const created: CandidateSupportTicket = {
      id: createdId,
      subject: this.ticketForm.subject,
      category: this.ticketForm.category,
      priority: this.ticketForm.priority,
      status: 'Open',
      createdAt: 'Just now',
      updatedAt: 'Just now',
      description: this.ticketForm.message,
      repliesCount: 1,
      messages: [
        {
          id: 'REP-INIT-' + Date.now(),
          sender: 'user',
          senderName: this.ticketForm.name,
          senderRole: 'Candidate',
          timestamp: 'Just now',
          message: this.ticketForm.message
        }
      ]
    };

    this.tickets.unshift(created);
    this.closeNewTicketModal();
    this.showToast(`Support Ticket ${created.id} submitted successfully! Our helpdesk will respond shortly.`);
  }

  viewTicketDetails(ticket: CandidateSupportTicket) {
    this.selectedTicket = ticket;
    this.replyInputText = '';
    if (!this.selectedTicket.messages) {
      this.selectedTicket.messages = [
        {
          id: 'REP-INIT',
          sender: 'user',
          senderName: 'Rahul Sharma',
          senderRole: 'Candidate',
          timestamp: ticket.createdAt,
          message: ticket.description
        }
      ];
    }
  }

  closeTicketDetails() {
    this.selectedTicket = null;
    this.replyInputText = '';
  }

  sendReply() {
    if (!this.selectedTicket) return;
    const trimmed = this.replyInputText.trim();
    if (!trimmed) {
      this.showToast('Please type your reply message in the textbox.');
      return;
    }

    const userReply: CandidateTicketReply = {
      id: 'REP-' + Date.now(),
      sender: 'user',
      senderName: 'Rahul Sharma',
      senderRole: 'Candidate',
      timestamp: 'Just now',
      message: trimmed
    };

    if (!this.selectedTicket.messages) {
      this.selectedTicket.messages = [];
    }

    this.selectedTicket.messages.push(userReply);
    this.selectedTicket.repliesCount = this.selectedTicket.messages.length;
    this.selectedTicket.updatedAt = 'Just now';

    this.replyInputText = '';
    this.showToast('Reply added successfully to ticket ' + this.selectedTicket.id);

    // Dynamic auto-reply from ProveYu candidate desk for real-time interaction
    const activeTicket = this.selectedTicket;
    setTimeout(() => {
      if (activeTicket) {
        activeTicket.messages.push({
          id: 'REP-SUP-' + Date.now(),
          sender: 'support',
          senderName: 'ProveYu Candidate Helpdesk',
          senderRole: 'Support Specialist',
          avatar: this.helpdeskLead.avatar,
          timestamp: 'Just now',
          message: `Received your reply regarding ticket ${activeTicket.id}. Our support team is attending to your request.`
        });
        activeTicket.repliesCount = activeTicket.messages.length;
        activeTicket.updatedAt = 'Just now';
      }
    }, 1200);
  }

  copyToClipboard(text: string, label: string) {
    navigator.clipboard.writeText(text);
    this.showToast(`${label} copied to clipboard!`);
  }

  onAvatarError(event: any) {
    event.target.src = 'https://ui-avatars.com/api/?name=Priya+Sharma&background=16122b&color=fff&size=128';
  }

  showToast(message: string) {
    this.toastMessage = message;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }
}

