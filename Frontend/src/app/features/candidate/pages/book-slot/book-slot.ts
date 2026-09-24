import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

export interface ExamDomain {
  id: number;
  name: string;
  code: string;
  category: string;
  duration: string;
  mode: string;
  price: number;
  desc: string;
  skills: string[];
  passRate: string;
  icon: string;
}

export interface DateOption {
  dayName: string;
  dateNum: number;
  month: string;
  fullDate: string;
}

export interface TimeSlotOption {
  time: string;
  period: string;
  seatsLeft: number;
  note?: string;
}

@Component({
  selector: 'app-book-slot',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './book-slot.html',
  styleUrl: './book-slot.scss',
})
export class BookSlot implements OnInit {
  currentStep = 1;

  // Domain Categories
  categories: string[] = ['All', 'Coding & DSA', 'Web & Frontend', 'Backend & Cloud', 'Data & AI', 'DevOps'];
  selectedCategory = 'All';
  searchQuery = '';

  // All Available Exam Domains
  exams: ExamDomain[] = [
    {
      id: 1,
      name: 'Data Structures & Algorithms (DSA)',
      code: 'EXAM-DSA-101',
      category: 'Coding & DSA',
      duration: '90 Minutes',
      mode: 'In-Person (AI Proctored)',
      price: 999,
      desc: 'Comprehensive evaluation of Arrays, Trees, Graphs, Dynamic Programming and Algorithm Optimization.',
      skills: ['Arrays', 'Graphs', 'Dynamic Programming', 'Recursion', 'Time Complexity'],
      passRate: '88% Pass Rate',
      icon: 'bi-code-square'
    },
    {
      id: 2,
      name: 'Full Stack Web Development (React & Node)',
      code: 'EXAM-FS-202',
      category: 'Web & Frontend',
      duration: '120 Minutes',
      mode: 'In-Person (AI Proctored)',
      price: 1499,
      desc: 'Build full-stack modern web apps using React 18, Express, REST APIs, and MongoDB/SQL.',
      skills: ['React', 'Node.js', 'Express', 'TypeScript', 'REST API'],
      passRate: '82% Pass Rate',
      icon: 'bi-journal-code'
    },
    {
      id: 3,
      name: 'System Design & Distributed Architecture',
      code: 'EXAM-SD-303',
      category: 'Backend & Cloud',
      duration: '120 Minutes',
      mode: 'In-Person (AI Proctored)',
      price: 1499,
      desc: 'Architect scalable distributed systems, microservices, caching layers, and database sharding.',
      skills: ['Microservices', 'Load Balancers', 'Redis', 'Kafka', 'SQL Sharding'],
      passRate: '75% Pass Rate',
      icon: 'bi-diagram-3'
    },
    {
      id: 4,
      name: 'Cloud Computing & DevOps Engineering',
      code: 'EXAM-DO-404',
      category: 'DevOps',
      duration: '90 Minutes',
      mode: 'In-Person (AI Proctored)',
      price: 1199,
      desc: 'Assess proficiency in Docker containerization, Kubernetes orchestration, CI/CD pipelines, and AWS.',
      skills: ['Docker', 'Kubernetes', 'AWS', 'GitHub Actions', 'Terraform'],
      passRate: '79% Pass Rate',
      icon: 'bi-cloud-check'
    },
    {
      id: 5,
      name: 'Data Science & Machine Learning Engineering',
      code: 'EXAM-AI-505',
      category: 'Data & AI',
      duration: '90 Minutes',
      mode: 'In-Person (AI Proctored)',
      price: 1299,
      desc: 'Practical data science evaluation covering Pandas, Scikit-learn, Model evaluation, and PyTorch.',
      skills: ['Python', 'Pandas', 'Scikit-Learn', 'Feature Engineering', 'Model Tuning'],
      passRate: '81% Pass Rate',
      icon: 'bi-cpu'
    }
  ];

  selectedExam: ExamDomain | null = null;
  isDropdownOpen = false;

  // Modal for Exam Detail View
  isModalOpen = false;
  selectedDetailExam: ExamDomain | null = null;

  // City & Test Center Location Selection
  cities: string[] = ['Bengaluru', 'Hyderabad', 'Pune', 'NCR Delhi'];
  selectedCity = 'Bengaluru';

  centers: { name: string; seats: number }[] = [
    { name: 'iON Digital Zone, Koramangala Center', seats: 14 },
    { name: 'iON Digital Zone, Whitefield Tech Park', seats: 22 },
    { name: 'AssessHub Academy, Indiranagar', seats: 5 }
  ];
  selectedCenter = 'iON Digital Zone, Koramangala Center';

  // Available Date Options (7 Days)
  availableDates: DateOption[] = [
    { dayName: 'Wed', dateNum: 24, month: 'Sep', fullDate: '24 Sep, 2024' },
    { dayName: 'Thu', dateNum: 25, month: 'Sep', fullDate: '25 Sep, 2024' },
    { dayName: 'Fri', dateNum: 26, month: 'Sep', fullDate: '26 Sep, 2024' },
    { dayName: 'Sat', dateNum: 27, month: 'Sep', fullDate: '27 Sep, 2024' },
    { dayName: 'Sun', dateNum: 28, month: 'Sep', fullDate: '28 Sep, 2024' },
    { dayName: 'Mon', dateNum: 29, month: 'Sep', fullDate: '29 Sep, 2024' },
    { dayName: 'Tue', dateNum: 30, month: 'Sep', fullDate: '30 Sep, 2024' }
  ];
  selectedDate: DateOption = this.availableDates[6]; // Default Tue 30 Sep as in ref HTML

  // Time Slot Selection
  timeSlots: TimeSlotOption[] = [
    { time: '09:00 AM - 10:30 AM', period: 'Morning', seatsLeft: 6 },
    { time: '02:30 PM - 04:00 PM', period: 'Afternoon', seatsLeft: 12, note: 'Report 15m prior' },
    { time: '05:00 PM - 06:30 PM', period: 'Evening', seatsLeft: 8 }
  ];
  selectedTimeSlot = '02:30 PM - 04:00 PM';

  // Step 2 Review & Payment State
  promoCode = 'PROVEYU10';
  promoApplied = false;
  discountAmount = 0;
  finalPrice = 999;
  selectedPaymentMethod = 'razorpay';
  isConfirmed = false;

  ngOnInit() {
    this.calculateTotal();
  }

  get filteredExams(): ExamDomain[] {
    return this.exams.filter(exam => {
      const matchesCategory = this.selectedCategory === 'All' || exam.category === this.selectedCategory;
      const matchesSearch = !this.searchQuery ||
        exam.name.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        exam.code.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        exam.skills.some(s => s.toLowerCase().includes(this.searchQuery.toLowerCase()));
      return matchesCategory && matchesSearch;
    });
  }

  selectExam(exam: ExamDomain) {
    this.selectedExam = exam;
    this.calculateTotal();
    this.isDropdownOpen = false;
  }

  toggleDropdown() {
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  openExamModal(exam: ExamDomain, event?: Event) {
    if (event) event.stopPropagation();
    this.selectedDetailExam = exam;
    this.isModalOpen = true;
  }

  closeExamModal() {
    this.isModalOpen = false;
    this.selectedDetailExam = null;
  }

  applyPromo() {
    if (this.promoCode.trim().toUpperCase() === 'PROVEYU10' && !this.promoApplied) {
      this.promoApplied = true;
      this.discountAmount = 100;
      this.calculateTotal();
    }
  }

  calculateTotal() {
    const base = this.selectedExam ? this.selectedExam.price : 999;
    this.finalPrice = this.promoApplied ? Math.max(0, base - this.discountAmount) : base;
  }

  goToStep(step: number) {
    if (step === 2 && !this.selectedExam) return;
    this.currentStep = step;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  completeBooking() {
    this.isConfirmed = true;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
