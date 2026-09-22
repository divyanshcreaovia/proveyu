import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface SkillItem {
  name: string;
  subTags: string[];
  level: 'ADVANCED' | 'STRONG' | 'DEVELOPING';
  score: number;
  progressColor: string;
  note: string;
  icon: string;
  iconBg: string;
  iconColor: string;
  proficiencyLabel: string;
  badgeIcon: string;
}

export interface StrengthItem {
  id: string;
  score: number;
  title: string;
  description: string;
  badge: string;
}

export interface HistoryItem {
  period: string;
  subtitle: string;
  scoreText: string;
  rankText: string;
  isActive?: boolean;
}

export interface VerificationCheck {
  title: string;
  description: string;
}

export interface RoleReadinessItem {
  role: string;
  matchScore: number;
  tags: string[];
  matchBadgeText: string;
  matchBadgeClass: string;
}

export interface PassportRecord {
  id: string;
  title: string;
  date: string;
  assessmentId: string;
  cohort: string;
  proctorLevel: string;
  integrityHash: string;
  readinessScore: number;
  readinessTrajectoryText: string;
  percentileText: string;
  percentileSubtext: string;
  meritRankText: string;
  meritSubtext: string;
  isQualified: boolean;
  placementStatus: string;
  placementSubtext: string;
  clearanceCode: string;
  assessmentResult: {
    name: string;
    scoreText: string;
    percentile: string;
    meritRank: string;
    completion: string;
    status: 'QUALIFIED' | 'NOT QUALIFIED';
  };
  skills: SkillItem[];
  strengths: StrengthItem[];
  focusItemsText: string;
  evaluatorRecommendation: string;
  history: HistoryItem[];
  compoundLiftText: string;
  verificationChecks: VerificationCheck[];
  roleReadiness: RoleReadinessItem[];
  ledgerRef: string;
}

@Component({
  selector: 'app-my-passport',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './my-passport.html',
  styleUrl: './my-passport.scss',
})
export class MyPassport implements AfterViewInit, OnDestroy {
  activeNavTab: string = 'result';
  selectedPassportId: string = 'PYU-892415';
  private observer: IntersectionObserver | null = null;

  // Candidate Profile Data
  candidate = {
    name: 'Divyansh Gupta',
    degree: 'B.Tech Computer Science (Class of 2026)',
    institution: 'Delhi Technological University (DTU)',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
    verificationBadge: 'PROVEYU VERIFIED & QUALIFIED',
    isVerified: true
  };

  // List of Passports for Candidate (1 QUALIFIED, 1 UNQUALIFIED)
  passportsList: PassportRecord[] = [
    {
      id: 'PYU-892415',
      title: 'Full Stack & Systems Assessment — September 2026 (QUALIFIED · 82/100)',
      date: '22 Sep 2026',
      assessmentId: 'PYU-ASS-984210',
      cohort: 'PYU-2026-ENG-A',
      proctorLevel: 'Level-4 Proctored',
      integrityHash: 'SHA-256: 7c2f...b91a',
      readinessScore: 82,
      readinessTrajectoryText: '+8 pts trajectory vs. August sprint',
      percentileText: '88th',
      percentileSubtext: 'Top 12% of assessed engineering cohort',
      meritRankText: '#42 of 350',
      meritSubtext: 'DTU Class of 2026 Talent Pool',
      isQualified: true,
      placementStatus: 'QUALIFIED',
      placementSubtext: 'ENTERPRISE PLACEMENT DRIVES',
      clearanceCode: 'DTU-CL-2026',
      assessmentResult: {
        name: 'ProveYu Placement Readiness Assessment — September 2026',
        scoreText: '82/100',
        percentile: '88th',
        meritRank: '#42 / 350',
        completion: '100%',
        status: 'QUALIFIED'
      },
      skills: [
        {
          name: 'OOP (Object-Oriented Programming)',
          subTags: ['Design Patterns', 'SOLID'],
          level: 'ADVANCED',
          score: 91,
          progressColor: 'bg-emerald',
          note: 'Mastery in polymorphism, encapsulation decoupling, and clean domain boundary segregation.',
          icon: 'bi-box-seam',
          iconBg: '#e6f9f0',
          iconColor: '#10b981',
          proficiencyLabel: 'EXCELLENT PROFICIENCY',
          badgeIcon: 'bi-crown'
        },
        {
          name: 'Java & Microservices Architecture',
          subTags: ['Spring Boot', 'JVM Internals'],
          level: 'ADVANCED',
          score: 86,
          progressColor: 'bg-emerald',
          note: 'High-performance garbage collection tuning, concurrent multi-threading, and gRPC endpoint contracts.',
          icon: 'bi-hdd-stack',
          iconBg: '#e8f2ff',
          iconColor: '#3b82f6',
          proficiencyLabel: 'STRONG PROFICIENCY',
          badgeIcon: 'bi-crown'
        },
        {
          name: 'Data Structures & Algorithms',
          subTags: ['Trees', 'Dynamic Programming & Graphs'],
          level: 'STRONG',
          score: 82,
          progressColor: 'bg-purple',
          note: 'Validated asymptotic efficiency (O(N log N) thresholds), balance trees, and memoized recursion.',
          icon: 'bi-diagram-3',
          iconBg: '#f3e8ff',
          iconColor: '#8b5cf6',
          proficiencyLabel: 'STRONG PROFICIENCY',
          badgeIcon: 'bi-bar-chart-line-fill'
        },
        {
          name: 'SQL & Query Optimization',
          subTags: ['PostgreSQL', 'Indexing & Transactions'],
          level: 'STRONG',
          score: 79,
          progressColor: 'bg-purple',
          note: 'ACID compliance, B-Tree index utilization, CTE subquery flattening, and deadlock mitigation.',
          icon: 'bi-database',
          iconBg: '#fff2e6',
          iconColor: '#f97316',
          proficiencyLabel: 'STRONG PROFICIENCY',
          badgeIcon: 'bi-bar-chart-line-fill'
        },
        {
          name: 'Algorithmic Problem Solving',
          subTags: ['Edge-Case Resiliency'],
          level: 'STRONG',
          score: 76,
          progressColor: 'bg-purple',
          note: 'Demonstrated robust logic handling under synthetic latency and boundary-value stress suites.',
          icon: 'bi-code-slash',
          iconBg: '#ffe6ea',
          iconColor: '#ef4444',
          proficiencyLabel: 'COMPETENT PROFICIENCY',
          badgeIcon: 'bi-bar-chart-line-fill'
        },
        {
          name: 'Technical Communication',
          subTags: ['Documentation & RFC Formulation'],
          level: 'DEVELOPING',
          score: 71,
          progressColor: 'bg-slate',
          note: 'Architectural decision recording (ADR), OpenAPI parameter definitions, and team engineering handover.',
          icon: 'bi-chat-left-text',
          iconBg: '#fef3c7',
          iconColor: '#d97706',
          proficiencyLabel: 'DEVELOPING PROFICIENCY',
          badgeIcon: 'bi-graph-up-arrow'
        }
      ],
      strengths: [
        {
          id: '01',
          score: 91,
          title: 'Object-Oriented Architecture',
          description: 'Exhibited top 3% percentile structural design patterns with clean loose-coupling in distributed systems modules.',
          badge: '✓ Production-grade modularity'
        },
        {
          id: '02',
          score: 86,
          title: 'Java Microservices Execution',
          description: 'Zero lock contention during concurrent transaction simulations. Implemented robust REST resilience patterns.',
          badge: '✓ Low-latency JVM tuning'
        },
        {
          id: '03',
          score: 82,
          title: 'Data Structures & Complexity',
          description: 'Strong algorithmic rigor with recursive memoization and optimal space complexity trade-offs.',
          badge: '✓ Optimal runtime efficiency'
        }
      ],
      focusItemsText: 'SQL Plan Analysis (79%) and Algorithmic Problem Solving (76%).',
      evaluatorRecommendation: '"Target 85+ score in upcoming sprint assessment"',
      history: [
        { period: 'July 2026 Sprint', subtitle: 'Core Microservices Baseline', scoreText: '68/100', rankText: 'Rank #112', isActive: false },
        { period: 'August 2026 Sprint', subtitle: 'Data Structures & Logic', scoreText: '75/100', rankText: 'Rank #78', isActive: false },
        { period: 'September 2026 (Active)', subtitle: 'Full Stack & Systems', scoreText: '82/100', rankText: 'Rank #42', isActive: true }
      ],
      compoundLiftText: '+20.5% Compound Lift',
      verificationChecks: [
        { title: 'Candidate Identity Verified', description: 'Aadhaar & DTU University Roll attested (Roll #2K22/CO/145)' },
        { title: 'Live Proctored Assessment', description: '180-min continuous browser lockdown · Zero flag anomalies' },
        { title: '6 Core Skills Calibrated', description: 'Automated evaluation harness with isolated code compilation sandbox' },
        { title: 'Composite Merit Index Calculated', description: 'Normalized against DTU 2026 Engineering cohort statistics' },
        { title: 'College Placement Cell Clearance', description: 'Institutional clearance granted for on-campus & remote drives' }
      ],
      roleReadiness: [
        {
          role: 'Java Backend Engineer',
          matchScore: 94,
          tags: ['Java', 'Spring Boot', 'Microservices', 'SQL'],
          matchBadgeText: 'Excellent Match',
          matchBadgeClass: 'bg-emerald-subtle text-emerald border border-emerald-subtle'
        },
        {
          role: 'Software Engineer',
          matchScore: 91,
          tags: ['DSA', 'System Design', 'Problem Solving'],
          matchBadgeText: 'Strong Match',
          matchBadgeClass: 'bg-purple-subtle text-purple border border-purple-subtle'
        },
        {
          role: 'Full Stack Developer',
          matchScore: 78,
          tags: ['JavaScript', 'Angular', 'Spring Boot', 'SQL'],
          matchBadgeText: 'Good Match',
          matchBadgeClass: 'bg-purple-subtle text-purple border border-purple-subtle'
        }
      ],
      ledgerRef: 'v7629-PYU-PROV'
    },
    {
      id: 'PYU-410382',
      title: 'Core Fundamentals & Algorithmic Logic — May 2026 (UNQUALIFIED · 48/100)',
      date: '10 May 2026',
      assessmentId: 'PYU-ASS-410382',
      cohort: 'PYU-2026-ENG-A',
      proctorLevel: 'Level-2 Proctored',
      integrityHash: 'SHA-256: 3a1f...d82e',
      readinessScore: 48,
      readinessTrajectoryText: '-12 pts below qualifying threshold (60)',
      percentileText: '35th',
      percentileSubtext: 'Bottom 65% of assessed engineering cohort',
      meritRankText: '#248 of 350',
      meritSubtext: 'DTU Class of 2026 Talent Pool',
      isQualified: false,
      placementStatus: 'NOT QUALIFIED',
      placementSubtext: 'RE-ASSESSMENT REQUIRED',
      clearanceCode: 'DTU-HOLD-2026',
      assessmentResult: {
        name: 'ProveYu Core Logic Diagnostic Assessment — May 2026',
        scoreText: '48/100',
        percentile: '35th',
        meritRank: '#248 / 350',
        completion: '100%',
        status: 'NOT QUALIFIED'
      },
      skills: [
        {
          name: 'Dynamic Programming & State Machines',
          subTags: ['Memoization', 'Subproblems'],
          level: 'DEVELOPING',
          score: 38,
          progressColor: 'bg-danger',
          note: 'Failed 4/5 hidden test cases on overlapping subproblem formulation and state space transitions.',
          icon: 'bi-diagram-3',
          iconBg: '#ffe6ea',
          iconColor: '#ef4444',
          proficiencyLabel: 'NEEDS IMPROVEMENT',
          badgeIcon: 'bi-exclamation-triangle-fill'
        },
        {
          name: 'System Logic & Bitwise Calculations',
          subTags: ['Bitwise Manipulation'],
          level: 'DEVELOPING',
          score: 42,
          progressColor: 'bg-danger',
          note: 'Integer overflow exceptions and unhandled boundary shifts during stress suite execution.',
          icon: 'bi-cpu',
          iconBg: '#ffe6ea',
          iconColor: '#ef4444',
          proficiencyLabel: 'NEEDS IMPROVEMENT',
          badgeIcon: 'bi-exclamation-triangle-fill'
        },
        {
          name: 'Data Structures & Pointers',
          subTags: ['Trees', 'Linked Lists'],
          level: 'DEVELOPING',
          score: 48,
          progressColor: 'bg-danger',
          note: 'Null pointer dereference flags detected during tree balancing cycle evaluation.',
          icon: 'bi-code-slash',
          iconBg: '#ffe6ea',
          iconColor: '#ef4444',
          proficiencyLabel: 'DEVELOPING PROFICIENCY',
          badgeIcon: 'bi-exclamation-triangle-fill'
        },
        {
          name: 'Time & Space Complexity Analysis',
          subTags: ['Big-O Evaluation'],
          level: 'DEVELOPING',
          score: 52,
          progressColor: 'bg-warning',
          note: 'Sub-optimal O(N^2) time bound submitted on queries requiring O(N log N) limits.',
          icon: 'bi-hourglass-split',
          iconBg: '#fef3c7',
          iconColor: '#d97706',
          proficiencyLabel: 'DEVELOPING PROFICIENCY',
          badgeIcon: 'bi-graph-up-arrow'
        },
        {
          name: 'SQL Queries & Joins',
          subTags: ['Basic Schema Queries'],
          level: 'DEVELOPING',
          score: 58,
          progressColor: 'bg-warning',
          note: 'Table scan bottleneck detected on un-indexed foreign key join queries.',
          icon: 'bi-database',
          iconBg: '#fef3c7',
          iconColor: '#d97706',
          proficiencyLabel: 'COMPETENT PROFICIENCY',
          badgeIcon: 'bi-graph-up-arrow'
        },
        {
          name: 'Object-Oriented Programming',
          subTags: ['Basic Inheritance'],
          level: 'STRONG',
          score: 62,
          progressColor: 'bg-purple',
          note: 'Basic encapsulation and interface contracts implemented correctly.',
          icon: 'bi-box-seam',
          iconBg: '#f3e8ff',
          iconColor: '#8b5cf6',
          proficiencyLabel: 'STRONG PROFICIENCY',
          badgeIcon: 'bi-bar-chart-line-fill'
        }
      ],
      strengths: [
        {
          id: '01',
          score: 62,
          title: 'Basic OOP Structure',
          description: 'Basic interface contracts and standard encapsulation principles implemented.',
          badge: '⚠️ Baseline proficiency'
        },
        {
          id: '02',
          score: 58,
          title: 'Standard SQL Operations',
          description: 'Understands SELECT, WHERE, and simple inner joins.',
          badge: '⚠️ Requires optimization'
        },
        {
          id: '03',
          score: 52,
          title: 'Linear Data Search',
          description: 'Array traversal and basic linear search implemented under relaxed time limits.',
          badge: '⚠️ Needs refinement'
        }
      ],
      focusItemsText: 'Dynamic Programming (38%), Bitwise Manipulation (42%), Data Structures (48%).',
      evaluatorRecommendation: '"Mandatory retake required. Minimum 60/100 score needed for campus placement drive clearance."',
      history: [
        { period: 'April 2026 Baseline', subtitle: 'Diagnostic Screening', scoreText: '40/100', rankText: 'Rank #290', isActive: false },
        { period: 'May 2026 (Active)', subtitle: 'Core Logic Assessment', scoreText: '48/100', rankText: 'Rank #248', isActive: true }
      ],
      compoundLiftText: '+8.0% Progress',
      verificationChecks: [
        { title: 'Candidate Identity Verified', description: 'Aadhaar & DTU University Roll attested (Roll #2K22/CO/145)' },
        { title: 'Live Proctored Assessment', description: '180-min continuous browser lockdown · Zero flag anomalies' },
        { title: '6 Core Skills Calibrated', description: 'Automated evaluation harness completed' },
        { title: 'Composite Merit Index Calculated', description: 'Score below placement cutoff threshold (60/100)' },
        { title: 'Placement Clearance Status', description: 'Clearance withheld until successful re-assessment retake' }
      ],
      roleReadiness: [
        {
          role: 'Junior Backend Developer',
          matchScore: 54,
          tags: ['Java', 'SQL', 'Basic OOP'],
          matchBadgeText: 'Developing Match',
          matchBadgeClass: 'bg-amber-subtle text-amber border border-amber-subtle'
        },
        {
          role: 'Software Engineer',
          matchScore: 48,
          tags: ['DSA', 'System Design', 'Logic'],
          matchBadgeText: 'Needs Improvement',
          matchBadgeClass: 'bg-danger-subtle text-danger border border-danger-subtle'
        },
        {
          role: 'Full Stack Developer',
          matchScore: 42,
          tags: ['JavaScript', 'Basic SQL'],
          matchBadgeText: 'Needs Improvement',
          matchBadgeClass: 'bg-danger-subtle text-danger border border-danger-subtle'
        }
      ],
      ledgerRef: 'v4103-PYU-UNQ'
    }
  ];

  // Currently active passport record
  get activePassport(): PassportRecord {
    return this.passportsList.find(p => p.id === this.selectedPassportId) || this.passportsList[0];
  }

  ngAfterViewInit() {
    this.initScrollSpy();
  }

  ngOnDestroy() {
    if (this.observer) {
      this.observer.disconnect();
    }
  }

  private initScrollSpy() {
    const sections = document.querySelectorAll('.passport-section-card');
    if (!sections.length) return;

    this.observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            this.activeNavTab = entry.target.id;
          }
        });
      },
      {
        threshold: 0.3,
        rootMargin: '-80px 0px -50% 0px'
      }
    );

    sections.forEach((section) => this.observer?.observe(section));
  }

  onSelectPassport(passportId: string) {
    this.selectedPassportId = passportId;
  }

  scrollToSection(sectionId: string) {
    this.activeNavTab = sectionId;
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  sharePassport() {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(window.location.href);
    }
  }

  copyVerificationId() {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(this.activePassport.assessmentId);
    }
  }

  downloadPassportPdf() {
    window.print();
  }
}

