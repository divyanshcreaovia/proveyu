import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

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
  ticketSubmitted: boolean = false;
  ticketId: string = '';

  ticketForm = {
    name: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    category: 'Exam Booking & Slot Allotment',
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
      const matchesCategory = this.selectedCategory === 'All' || faq.category === this.selectedCategory;
      const matchesSearch = !this.searchQuery ||
        faq.question.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        faq.answer.toLowerCase().includes(this.searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }

  toggleFaq(faq: FAQ) {
    faq.isOpen = !faq.isOpen;
  }

  setPopularTag(tag: string) {
    this.searchQuery = tag;
  }

  submitTicket() {
    if (!this.ticketForm.subject || !this.ticketForm.message) return;
    this.ticketId = 'SUP-' + Math.floor(100000 + Math.random() * 900000);
    this.ticketSubmitted = true;
  }

  resetTicketForm() {
    this.ticketSubmitted = false;
    this.ticketForm.subject = '';
    this.ticketForm.message = '';
    this.ticketForm.category = '';
  }
}
