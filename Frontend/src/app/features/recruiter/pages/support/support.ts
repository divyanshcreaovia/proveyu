import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface TicketReply {
  id: string;
  sender: 'user' | 'support';
  senderName: string;
  senderRole: string;
  avatar?: string;
  timestamp: string;
  message: string;
}

export interface SupportTicket {
  id: string;
  subject: string;
  category: string;
  priority: 'Low' | 'Medium' | 'High' | 'Urgent';
  status: 'Open' | 'In Progress' | 'Resolved';
  createdAt: string;
  updatedAt: string;
  candidateRef?: string;
  description: string;
  repliesCount: number;
  messages: TicketReply[];
}

interface FAQItem {
  id: number;
  category: string;
  question: string;
  answer: string;
  tags: string[];
  isOpen?: boolean;
}

@Component({
  selector: 'app-recruiter-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './support.html',
  styleUrl: './support.scss',
})
export class Support {
  searchQuery: string = '';
  activeFaqCategory: string = 'all';
  activeTicketFilter: string = 'all';
  replyInputText: string = '';

  // Dedicated Account Manager Details
  accountManager = {
    name: 'Divyansh Gupta',
    role: 'Senior Enterprise Account Manager',
    email: 'divyansh.g@proveyu.com',
    phone: '+91 98765 43210',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
    status: 'Online',
    availability: 'Mon - Fri | 9:00 AM - 7:00 PM IST',
    avgResponseTime: '< 2 hrs'
  };

  // Support Tickets List
  tickets: SupportTicket[] = [
    {
      id: 'TK-9824',
      subject: 'Invigilation Log Request for Frontend Dev Candidate (Ref #PY-4921)',
      category: 'Candidate Invigilation',
      priority: 'High',
      status: 'In Progress',
      createdAt: 'Today, 11:30 AM',
      updatedAt: '25 mins ago',
      candidateRef: 'PY-4921 (Vikram Malhotra)',
      description: 'Requesting verified video invigilation log and screen recording snippet for candidate Vikram Malhotra for Frontend Lead evaluation.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-1',
          sender: 'user',
          senderName: 'Divyansh Gupta',
          senderRole: 'Recruiter Lead',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: 'Today, 11:30 AM',
          message: 'Requesting verified video invigilation log and screen recording snippet for candidate Vikram Malhotra for Frontend Lead evaluation.'
        },
        {
          id: 'REP-2',
          sender: 'support',
          senderName: 'ProveYu Proctoring Team',
          senderRole: 'Compliance Officer',
          timestamp: '25 mins ago',
          message: 'Hello Divyansh, our proctoring compliance desk is retrieving the dual-camera audit recordings for #PY-4921. We will update the snippet download link here within 1 hour.'
        }
      ]
    },
    {
      id: 'TK-9781',
      subject: 'Greenhouse ATS Webhook Sync Authorization Latency',
      category: 'ATS & Webhooks',
      priority: 'Medium',
      status: 'Resolved',
      createdAt: 'Yesterday, 04:15 PM',
      updatedAt: 'Yesterday, 06:40 PM',
      description: 'Webhook payload delivery delay noted during peak hours on candidate stage progression.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-10',
          sender: 'user',
          senderName: 'Divyansh Gupta',
          senderRole: 'Recruiter Lead',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: 'Yesterday, 04:15 PM',
          message: 'Webhook payload delivery delay noted during peak hours on candidate stage progression.'
        },
        {
          id: 'REP-11',
          sender: 'support',
          senderName: 'ProveYu API Desk',
          senderRole: 'Integration Specialist',
          timestamp: 'Yesterday, 06:40 PM',
          message: 'Webhook rate limits have been increased for your recruiter organization. All backlogged events for Greenhouse ATS are synced.'
        }
      ]
    },
    {
      id: 'TK-9640',
      subject: 'Quarterly Corporate GST Tax Invoice & Verification Audit Receipt',
      category: 'Billing & Credits',
      priority: 'Low',
      status: 'Resolved',
      createdAt: '22 Sep 2026',
      updatedAt: '22 Sep 2026',
      description: 'Need formal tax invoice breakdown with GSTIN 27AABCP1324E1ZM for accounting.',
      repliesCount: 1,
      messages: [
        {
          id: 'REP-20',
          sender: 'user',
          senderName: 'Divyansh Gupta',
          senderRole: 'Recruiter Lead',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: '22 Sep 2026',
          message: 'Need formal tax invoice breakdown with GSTIN 27AABCP1324E1ZM for accounting.'
        }
      ]
    }
  ];

  // New Ticket Modal & State
  showNewTicketModal: boolean = false;
  selectedTicket: SupportTicket | null = null;
  
  newTicket = {
    subject: '',
    category: 'Candidate Invigilation',
    priority: 'Medium' as 'Low' | 'Medium' | 'High' | 'Urgent',
    candidateRef: '',
    description: '',
    attachmentName: ''
  };

  // Toast Notification
  toastMessage: string | null = null;

  // FAQ Knowledge Base
  faqList: FAQItem[] = [
    {
      id: 1,
      category: 'invigilation',
      question: 'How are ProveYu Candidate Passports invigilated and verified?',
      answer: 'Every candidate undergoes live, AI-monitored & human-proctored invigilation tests at certified ProveYu physical test centers or secure proctored environments. We verify identity (Aadhaar/PAN), code integrity, background screen feeds, and hands-on coding tests.',
      tags: ['Invigilation Audit', 'Scorecard Verification'],
      isOpen: true
    },
    {
      id: 2,
      category: 'invigilation',
      question: 'What happens if a candidate disputes their invigilation result?',
      answer: 'Recruiters can raise an "Invigilation Audit Request" with the candidate reference ID. Our proctoring compliance team re-evaluates the dual-camera recordings and keystroke dynamics within 24 business hours.',
      tags: ['Invigilation Audit'],
      isOpen: false
    },
    {
      id: 3,
      category: 'ats',
      question: 'How do I integrate ProveYu candidate passports with Greenhouse / Lever / Workday?',
      answer: 'Navigate to Recruiter Settings > Integrations & Webhooks. Generate your secret API token and paste your webhook endpoint URL. Candidates moved to "Interview Invited" in ProveYu will automatically sync to your ATS candidate pipeline.',
      tags: ['ATS Webhooks', 'API Keys'],
      isOpen: false
    },
    {
      id: 4,
      category: 'billing',
      question: 'How do employer verification credits work and how to top up?',
      answer: '1 Verification Credit is consumed when you unlock a full invigilated Skill Passport or issue a direct interview invite. Unused credits roll over monthly. You can top up under Settings or contact your Account Manager.',
      tags: ['Seat Credits', 'GST Tax Invoice'],
      isOpen: false
    },
    {
      id: 5,
      category: 'hiring',
      question: 'Can we customize custom tech stacks and evaluation questions for requirements?',
      answer: 'Yes! When posting a requirement, check "Custom Tech Assessment". You can upload your technical benchmark tests or select from our pre-validated senior engineering assessment modules.',
      tags: ['Scorecard Verification'],
      isOpen: false
    }
  ];

  categories = [
    { id: 'all', label: 'All Topics', icon: 'bi-grid-fill' },
    { id: 'invigilation', label: 'Invigilation & Passports', icon: 'bi-patch-check-fill' },
    { id: 'ats', label: 'ATS & Webhook Integrations', icon: 'bi-puzzle-fill' },
    { id: 'billing', label: 'Billing, Credits & GST', icon: 'bi-credit-card-fill' },
    { id: 'hiring', label: 'Job Requirements & Pipeline', icon: 'bi-kanban-fill' }
  ];

  get filteredFaqs(): FAQItem[] {
    return this.faqList.filter(faq => {
      const matchesCategory = this.activeFaqCategory === 'all' || faq.category === this.activeFaqCategory;
      const q = this.searchQuery.toLowerCase().trim();
      const matchesSearch = !q || 
        faq.question.toLowerCase().includes(q) || 
        faq.answer.toLowerCase().includes(q) ||
        faq.tags.some(t => t.toLowerCase().includes(q));
      return matchesCategory && matchesSearch;
    });
  }

  get filteredTickets(): SupportTicket[] {
    if (this.activeTicketFilter === 'all') return this.tickets;
    return this.tickets.filter(t => t.status.toLowerCase() === this.activeTicketFilter.toLowerCase());
  }

  toggleFaq(faq: FAQItem) {
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
    this.newTicket = {
      subject: '',
      category: 'Candidate Invigilation',
      priority: 'Medium',
      candidateRef: '',
      description: '',
      attachmentName: ''
    };
  }

  submitTicket() {
    if (!this.newTicket.subject || !this.newTicket.description) {
      this.showToast('Please fill in the subject and description.');
      return;
    }

    const createdId = `TK-${Math.floor(1000 + Math.random() * 9000)}`;
    const created: SupportTicket = {
      id: createdId,
      subject: this.newTicket.subject,
      category: this.newTicket.category,
      priority: this.newTicket.priority,
      status: 'Open',
      createdAt: 'Just now',
      updatedAt: 'Just now',
      candidateRef: this.newTicket.candidateRef || undefined,
      description: this.newTicket.description,
      repliesCount: 1,
      messages: [
        {
          id: 'REP-INIT-' + Date.now(),
          sender: 'user',
          senderName: 'Divyansh Gupta',
          senderRole: 'Recruiter Lead',
          avatar: this.accountManager.avatar,
          timestamp: 'Just now',
          message: this.newTicket.description
        }
      ]
    };

    this.tickets.unshift(created);
    this.closeNewTicketModal();
    this.showToast(`Support Ticket ${created.id} submitted successfully! Our proctoring team will respond shortly.`);
  }

  viewTicketDetails(ticket: SupportTicket) {
    this.selectedTicket = ticket;
    this.replyInputText = '';
    if (!this.selectedTicket.messages) {
      this.selectedTicket.messages = [
        {
          id: 'REP-INIT',
          sender: 'user',
          senderName: 'Divyansh Gupta',
          senderRole: 'Recruiter Lead',
          avatar: this.accountManager.avatar,
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

    const userReply: TicketReply = {
      id: 'REP-' + Date.now(),
      sender: 'user',
      senderName: 'Divyansh Gupta',
      senderRole: 'Recruiter Lead',
      avatar: this.accountManager.avatar,
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

    // Dynamic auto-reply from ProveYu support desk for real-time interaction
    const activeTicket = this.selectedTicket;
    setTimeout(() => {
      if (activeTicket) {
        activeTicket.messages.push({
          id: 'REP-SUP-' + Date.now(),
          sender: 'support',
          senderName: 'ProveYu Proctoring Desk',
          senderRole: 'Support Specialist',
          timestamp: 'Just now',
          message: `Received your reply regarding ticket ${activeTicket.id}. Our proctoring team is processing your request.`
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

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.newTicket.attachmentName = file.name;
    }
  }

  onAvatarError(event: any) {
    event.target.src = 'https://ui-avatars.com/api/?name=Divyansh+Gupta&background=16122b&color=fff&size=128';
  }

  showToast(message: string) {
    this.toastMessage = message;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }
}



