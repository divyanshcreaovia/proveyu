import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface CollegeTicketReply {
  id: string;
  sender: 'user' | 'support';
  senderName: string;
  senderRole: string;
  avatar?: string;
  timestamp: string;
  message: string;
}

export interface CollegeSupportTicket {
  id: string;
  subject: string;
  category: string;
  priority: 'Low' | 'Medium' | 'High' | 'Urgent';
  status: 'Open' | 'In Progress' | 'Resolved';
  createdAt: string;
  updatedAt: string;
  assessmentRef?: string;
  description: string;
  repliesCount: number;
  messages: CollegeTicketReply[];
}

interface FAQ {
  question: string;
  answer: string;
  category: string;
  isOpen?: boolean;
}

@Component({
  selector: 'app-college-support',
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
  selectedTicket: CollegeSupportTicket | null = null;
  toastMessage: string | null = null;

  // Dedicated College Account Director / Institutional Helpdesk Lead
  campusManager = {
    name: 'Dr. Vikram Malhotra',
    role: 'Head of Campus Alliances & Institutional Desk',
    email: 'campus.relations@proveyu.com',
    phone: '1800-419-CAMPUS (Ext 4)',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80',
    status: 'Available Now',
    availability: 'Mon - Sat | 9:00 AM - 6:30 PM IST',
    avgResponseTime: '< 45 mins'
  };

  // Sample College Support Tickets
  tickets: CollegeSupportTicket[] = [
    {
      id: 'TK-CLG-8921',
      subject: 'Batch CSV Roster Sync Error for 2026 CS Batch (48 Students)',
      category: 'Batch Roster & CSV Import',
      priority: 'High',
      status: 'In Progress',
      createdAt: 'Today, 09:30 AM',
      updatedAt: '25 mins ago',
      assessmentRef: 'PY-CAMPUS-2026-T1 (Test 1 Diagnostic)',
      description: 'Uploaded CSV containing 250 students, but 48 students in CS-B section failed validation due to duplicate Roll ID mismatch.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-1',
          sender: 'user',
          senderName: 'Prof. Ramesh K. (TPO)',
          senderRole: 'Placement Officer',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: 'Today, 09:30 AM',
          message: 'Uploaded CSV containing 250 students, but 48 students in CS-B section failed validation due to duplicate Roll ID mismatch.'
        },
        {
          id: 'REP-2',
          sender: 'support',
          senderName: 'Dr. Vikram Malhotra',
          senderRole: 'Campus Operations Desk',
          avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80',
          timestamp: '25 mins ago',
          message: 'We sanitized the duplicate roll prefix format in the database. You can now re-upload the sanitized CS-B batch template from your dashboard.'
        }
      ]
    },
    {
      id: 'TK-CLG-8874',
      subject: 'Corporate Drive Shortlist Sync: Amazon & Infosys Tier 1 Pool',
      category: 'Corporate Drive Coordination',
      priority: 'Urgent',
      status: 'Open',
      createdAt: 'Yesterday, 04:15 PM',
      updatedAt: '2 hours ago',
      assessmentRef: 'Annual Placement Drive 2026',
      description: 'Need candidate Skill Passport links mapped directly to Amazon recruiter portal for candidates clearing Tier 1 benchmark.',
      repliesCount: 1,
      messages: [
        {
          id: 'REP-10',
          sender: 'user',
          senderName: 'Prof. Ramesh K. (TPO)',
          senderRole: 'Placement Officer',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: 'Yesterday, 04:15 PM',
          message: 'Need candidate Skill Passport links mapped directly to Amazon recruiter portal for candidates clearing Tier 1 benchmark.'
        }
      ]
    },
    {
      id: 'TK-CLG-8710',
      subject: 'Custom Benchmark Cutoff Adjustment for Mechanical Core Aptitude',
      category: 'Diagnostic & Tier Benchmarking',
      priority: 'Medium',
      status: 'Resolved',
      createdAt: '22 Sep 2026',
      updatedAt: '23 Sep 2026',
      description: 'Requested separate Tier 2 threshold (62% instead of 65%) for core mechanical and civil branches for upcoming TCS drive.',
      repliesCount: 2,
      messages: [
        {
          id: 'REP-20',
          sender: 'user',
          senderName: 'Prof. Ramesh K. (TPO)',
          senderRole: 'Placement Officer',
          avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
          timestamp: '22 Sep 2026',
          message: 'Requested separate Tier 2 threshold (62% instead of 65%) for core mechanical and civil branches for upcoming TCS drive.'
        },
        {
          id: 'REP-21',
          sender: 'support',
          senderName: 'ProveYu Campus Team',
          senderRole: 'Assessment Operations',
          timestamp: '23 Sep 2026',
          message: 'Custom branch-specific cutoff configured and applied to the 2026 Campus Drive profile.'
        }
      ]
    }
  ];

  ticketForm = {
    name: 'Prof. Ramesh K.',
    email: 'tpo@college.edu.in',
    category: 'Batch Roster & CSV Import',
    priority: 'Medium' as 'Low' | 'Medium' | 'High' | 'Urgent',
    subject: '',
    message: ''
  };

  browseTopics = [
    { title: 'Student Roster & Batch Import', desc: 'Bulk CSV imports, roll mapping, batch updates', icon: 'bi-people-fill', bg: '#f0ebff', color: '#6366f1' },
    { title: 'Proctored Assessments & Schedule', desc: '7 proctored tests, center coordination, re-tests', icon: 'bi-shield-check', bg: '#e6f9f0', color: '#10b981' },
    { title: 'Tier Benchmarks & Analytics', desc: 'Cutoff management, score sync, improvement curves', icon: 'bi-bar-chart-line-fill', bg: '#f3e8ff', color: '#8b5cf6' },
    { title: 'Corporate Drives & Shortlisting', desc: 'Recruiter passport sharing, interview shortlists', icon: 'bi-briefcase-fill', bg: '#fff0e6', color: '#f97316' },
    { title: 'Executive Dossier & Reports', desc: 'Board summaries, student transcripts, exports', icon: 'bi-file-earmark-bar-graph-fill', bg: '#e6f4fe', color: '#0284c7' },
    { title: 'Campus Billing & Licences', desc: 'Student token credits, renewals, institutional invoices', icon: 'bi-credit-card-2-front-fill', bg: '#fdebf7', color: '#ec4899' }
  ];

  categories = [
    { id: 'all', label: 'All Topics', icon: 'bi-grid-fill' },
    { id: 'roster', label: 'Roster & Batches', icon: 'bi-people-fill' },
    { id: 'assessments', label: 'Proctored Tests', icon: 'bi-shield-check' },
    { id: 'benchmarks', label: 'Tier Cutoffs', icon: 'bi-bar-chart-fill' },
    { id: 'drives', label: 'Campus Drives', icon: 'bi-briefcase-fill' },
    { id: 'billing', label: 'Billing & Token Credits', icon: 'bi-credit-card-fill' }
  ];

  faqs: FAQ[] = [
    {
      question: 'How many proctored tests are included in our campus partnership bundle?',
      answer: 'The standard institutional partnership includes 7 comprehensive proctored tests conducted over 6-7 months. Each test includes Sectional Aptitude, Logical Reasoning, Coding/Domain benchmarks, and Skill Passport generation.',
      category: 'Proctored Tests',
      isOpen: true
    },
    {
      question: 'Can college TPO / administrators adjust Tier cutoffs after tests have commenced?',
      answer: 'Tier cutoffs for live or concluded tests remain locked to preserve integrity. However, TPOs can define custom department-specific or drive-specific cutoffs for upcoming tests directly from the Settings or by raising a priority support ticket.',
      category: 'Tier Cutoffs',
      isOpen: false
    },
    {
      question: 'How are certified student Skill Passports made accessible to partner recruiters?',
      answer: 'Candidates who achieve Tier 1 or Tier 2 verification status are automatically showcased on the ProveYu Enterprise Recruiter Portal. Corporate hiring partners can directly issue interview invitations and download verified scorecards.',
      category: 'Campus Drives',
      isOpen: false
    },
    {
      question: 'What is the required CSV file schema for bulk student enrolment and batch sync?',
      answer: 'The CSV format requires the following columns: Student Name, Roll Number, Official College Email, Department/Branch, CGPA, and Graduation Batch Year. You can download the pre-formatted Excel/CSV template under Students > Import.',
      category: 'Roster & Batches',
      isOpen: false
    },
    {
      question: 'How does ProveYu handle power outages or network interruptions during campus lab tests?',
      answer: 'Our testing engine features continuous delta sync. If a lab student loses connectivity, answers up to that second are safely cached in memory, and the timer pauses until the session is re-authenticated by the faculty proctor.',
      category: 'Proctored Tests',
      isOpen: false
    },
    {
      question: 'How can our institution request additional student testing credits or token packs?',
      answer: 'You can top up token packs by contacting Dr. Vikram Malhotra directly or selecting "Raise Support Ticket" with category "Billing & Licences". Additional tokens are instantly provisioned within 2 hours.',
      category: 'Billing & Token Credits',
      isOpen: false
    }
  ];

  get filteredTickets(): CollegeSupportTicket[] {
    if (this.activeTicketFilter === 'all') return this.tickets;
    return this.tickets.filter(t => t.status.toLowerCase() === this.activeTicketFilter);
  }

  get filteredFaqs(): FAQ[] {
    return this.faqs.filter(f => {
      const matchesSearch = !this.searchQuery.trim() ||
        f.question.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        f.answer.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        f.category.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesCat = this.selectedCategory === 'All' ||
        this.selectedCategory === 'All Topics' ||
        f.category.toLowerCase() === this.selectedCategory.toLowerCase();

      return matchesSearch && matchesCat;
    });
  }

  showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }

  copyToClipboard(text: string, label: string) {
    navigator.clipboard.writeText(text).then(() => {
      this.showToast(`${label} copied to clipboard!`);
    }).catch(() => {
      this.showToast(`${label}: ${text}`);
    });
  }

  openNewTicketModal() {
    this.showNewTicketModal = true;
  }

  closeNewTicketModal() {
    this.showNewTicketModal = false;
  }

  submitTicket() {
    if (!this.ticketForm.subject.trim() || !this.ticketForm.message.trim()) {
      this.showToast('Please fill in both Subject and Description.');
      return;
    }

    const newTicket: CollegeSupportTicket = {
      id: `TK-CLG-${Math.floor(1000 + Math.random() * 9000)}`,
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
          id: `REP-${Date.now()}`,
          sender: 'user',
          senderName: this.ticketForm.name,
          senderRole: 'College Administrator',
          timestamp: 'Just now',
          message: this.ticketForm.message
        }
      ]
    };

    this.tickets.unshift(newTicket);
    this.ticketForm.subject = '';
    this.ticketForm.message = '';
    this.showNewTicketModal = false;
    this.showToast(`Support Ticket #${newTicket.id} created successfully! Our Campus Desk will respond shortly.`);
  }

  viewTicketDetails(ticket: CollegeSupportTicket) {
    this.selectedTicket = ticket;
    this.replyInputText = '';
  }

  closeTicketDetails() {
    this.selectedTicket = null;
  }

  sendReply() {
    if (!this.replyInputText.trim() || !this.selectedTicket) return;

    const reply: CollegeTicketReply = {
      id: `REP-${Date.now()}`,
      sender: 'user',
      senderName: 'Prof. Ramesh K. (TPO)',
      senderRole: 'Placement Officer',
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
      timestamp: 'Just now',
      message: this.replyInputText.trim()
    };

    this.selectedTicket.messages.push(reply);
    this.selectedTicket.repliesCount = this.selectedTicket.messages.length;
    this.selectedTicket.updatedAt = 'Just now';
    this.replyInputText = '';
    this.showToast('Reply dispatched to ProveYu Institutional Desk.');
  }

  toggleFaq(faq: FAQ) {
    faq.isOpen = !faq.isOpen;
  }

  onAvatarError(event: any) {
    event.target.src = 'https://ui-avatars.com/api/?name=Campus+Support&background=16122b&color=fff';
  }
}
