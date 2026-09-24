import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';

import { NotificationService } from '../../../../shared/services/notification.service';

export type CandidateStage = 'shortlisted' | 'interviewing' | 'offered' | 'hired';

export interface KanbanCandidate {
  id: string;
  name: string;
  code?: string;
  roleTrack: string;
  score: number;
  stage: CandidateStage;
  email?: string;
  phone?: string;
  location?: string;
  experience?: string;
  appliedDate?: string;
  avatarUrl?: string;
  notes?: string[];
  skills?: string[];
  invigilatedVerifications?: {
    cameraInvigilated: boolean;
    screenRecorded: boolean;
    identityVerified: boolean;
    environmentSecure: boolean;
  };
}

export interface KanbanColumn {
  id: CandidateStage;
  title: string;
  colorClass: string;
  badgeBg: string;
  nextStage?: CandidateStage;
  nextStageActionLabel?: string;
  nextStageButtonStyle?: 'outline' | 'solid-green' | 'solid-orange' | 'badge-green';
}

@Component({
  selector: 'app-manage-hiring',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './manage-hiring.html',
  styleUrl: './manage-hiring.scss',
})
export class ManageHiring implements OnInit {
  searchTerm: string = '';
  selectedTrackFilter: string = 'ALL';
  toastMessage: string | null = null;
  draggedCandidateId: string | null = null;
  dragOverStage: CandidateStage | null = null;

  // Selected candidate for detail modal
  selectedCandidate: KanbanCandidate | null = null;
  showDetailModal: boolean = false;
  newNoteText: string = '';

  // New candidate modal state
  showAddModal: boolean = false;
  newCandidate: Partial<KanbanCandidate> = {
    name: '',
    roleTrack: 'Java Backend (Spring Boot)',
    score: 85,
    stage: 'shortlisted',
    email: '',
    phone: '',
    location: 'Bengaluru / Hybrid',
    experience: '1-3 Yrs',
  };

  columns: KanbanColumn[] = [
    {
      id: 'shortlisted',
      title: 'Shortlisted',
      colorClass: 'column-shortlisted',
      badgeBg: '#56617a',
      nextStage: 'interviewing',
      nextStageActionLabel: 'Move to Interview →',
      nextStageButtonStyle: 'outline',
    },
    {
      id: 'interviewing',
      title: 'Interviewing',
      colorClass: 'column-interviewing',
      badgeBg: '#00875a',
      nextStage: 'offered',
      nextStageActionLabel: 'Extend Offer →',
      nextStageButtonStyle: 'solid-green',
    },
    {
      id: 'offered',
      title: 'Offered',
      colorClass: 'column-offered',
      badgeBg: '#9100fa',
      nextStage: 'hired',
      nextStageActionLabel: 'Mark as Hired',
      nextStageButtonStyle: 'solid-orange',
    },
    {
      id: 'hired',
      title: 'Hired',
      colorClass: 'column-hired',
      badgeBg: '#00c875',
      nextStageButtonStyle: 'badge-green',
    },
  ];

  candidates: KanbanCandidate[] = [
    {
      id: 'CAND-7412',
      name: 'Candidate #CF-7412',
      code: 'CF-7412',
      roleTrack: 'Java Backend (Spring Boot)',
      score: 89,
      stage: 'shortlisted',
      email: 'candidate.7412@proveyu.verified',
      phone: '+91 98765 43210',
      location: 'Bengaluru, KA',
      experience: '2 Yrs',
      appliedDate: 'Sep 21, 2026',
      notes: [
        'Passed invigilated Java Spring Boot coding lab with 89% score.',
        'Strong microservices architecture and Docker foundation.'
      ],
      skills: ['Java 17', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST API'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    },
    {
      id: 'CAND-3301',
      name: 'Candidate #CF-3301',
      code: 'CF-3301',
      roleTrack: 'Web Development (Full Stack)',
      score: 88,
      stage: 'shortlisted',
      email: 'candidate.3301@proveyu.verified',
      phone: '+91 97112 88400',
      location: 'Pune, MH',
      experience: '1.5 Yrs',
      appliedDate: 'Sep 22, 2026',
      notes: [
        'Scored 88/100 on live Angular + Node.js full-stack challenge.',
        'Verified ID and live webcam proctoring validated.'
      ],
      skills: ['Angular', 'TypeScript', 'Node.js', 'MongoDB', 'RxJS'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    },
    {
      id: 'CAND-9012',
      name: 'Rahul Sharma',
      code: 'CF-9012',
      roleTrack: 'Universal Fresher Competency',
      score: 92,
      stage: 'interviewing',
      email: 'rahul.sharma@example.com',
      phone: '+91 98200 11223',
      location: 'Hyderabad, TS',
      experience: 'Fresher (0-1 Yr)',
      appliedDate: 'Sep 18, 2026',
      notes: [
        'Round 1 Technical Interview completed with Technical Lead.',
        'Invigilated DSA score: 92/100. Excellent problem solver.'
      ],
      skills: ['Data Structures', 'C++', 'Java', 'SQL', 'Problem Solving'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    },
    {
      id: 'CAND-4410',
      name: 'Priya Nambiar',
      code: 'CF-4410',
      roleTrack: 'QA & Automation Testing',
      score: 95,
      stage: 'offered',
      email: 'priya.nambiar@example.com',
      phone: '+91 99401 55667',
      location: 'Bengaluru, KA',
      experience: '3 Yrs',
      appliedDate: 'Sep 15, 2026',
      notes: [
        'Offer letter drafted at ₹9.5 LPA.',
        'Invigilated Selenium & Playwright automation score: 95/100.'
      ],
      skills: ['Selenium', 'Playwright', 'Java', 'Cucumber BDD', 'TestNG', 'CI/CD'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    },
    {
      id: 'CAND-1198',
      name: 'Amitav Ghosh',
      code: 'CF-1198',
      roleTrack: 'Python Backend & Data',
      score: 86,
      stage: 'hired',
      email: 'amitav.ghosh@example.com',
      phone: '+91 98334 77889',
      location: 'Kolkata / Remote',
      experience: '2.5 Yrs',
      appliedDate: 'Sep 10, 2026',
      notes: [
        'Offer accepted! Joining Date: Oct 1, 2026.',
        'Invigilated FastAPI & Pandas score: 86/100.'
      ],
      skills: ['Python', 'FastAPI', 'Pandas', 'PostgreSQL', 'Redis'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    },
  ];

  domainTracks: string[] = [
    'ALL',
    'Java Backend (Spring Boot)',
    'Web Development (Full Stack)',
    'Universal Fresher Competency',
    'QA & Automation Testing',
    'Python Backend & Data',
  ];

  constructor(
    private route: ActivatedRoute,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      if (params['shortlisted']) {
        const candidateName = params['shortlisted'];
        const existing = this.candidates.find((c) =>
          c.name.toLowerCase().includes(candidateName.toLowerCase())
        );
        if (existing) {
          existing.stage = 'shortlisted';
          this.showToast(`${existing.name} is in Shortlisted stage on your board!`);
        } else {
          const newCand: KanbanCandidate = {
            id: `CAND-${Math.floor(1000 + Math.random() * 9000)}`,
            name: candidateName,
            code: `CF-${Math.floor(1000 + Math.random() * 9000)}`,
            roleTrack: 'QA & Automation Testing',
            score: 92,
            stage: 'shortlisted',
            email: `${candidateName.toLowerCase().replace(/\s+/g, '.')}@example.com`,
            phone: '+91 98765 43210',
            location: 'Bengaluru, KA',
            experience: '1-2 Yrs',
            appliedDate: 'Just now',
            notes: ['Shortlisted directly from Candidate Chats.'],
            skills: ['Verified Passport Skills'],
            invigilatedVerifications: {
              cameraInvigilated: true,
              screenRecorded: true,
              identityVerified: true,
              environmentSecure: true,
            },
          };
          this.candidates.unshift(newCand);
          this.showToast(`${candidateName} shortlisted and added to your Manage Hiring board!`);
        }
      }
    });
  }

  // Filtered candidates by search term & track filter
  getFilteredCandidates(stage: CandidateStage): KanbanCandidate[] {
    return this.candidates.filter((candidate) => {
      const matchesStage = candidate.stage === stage;
      const matchesSearch =
        !this.searchTerm ||
        candidate.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        candidate.roleTrack.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        (candidate.code && candidate.code.toLowerCase().includes(this.searchTerm.toLowerCase()));
      const matchesTrack =
        this.selectedTrackFilter === 'ALL' || candidate.roleTrack === this.selectedTrackFilter;

      return matchesStage && matchesSearch && matchesTrack;
    });
  }

  // Count by stage
  getStageCount(stage: CandidateStage): number {
    return this.getFilteredCandidates(stage).length;
  }

  // Move candidate to next stage via button click
  moveNext(candidate: KanbanCandidate, targetStage?: CandidateStage): void {
    const stageOrder: CandidateStage[] = ['shortlisted', 'interviewing', 'offered', 'hired'];
    let nextStage: CandidateStage;

    if (targetStage) {
      nextStage = targetStage;
    } else {
      const currentIndex = stageOrder.indexOf(candidate.stage);
      if (currentIndex < stageOrder.length - 1) {
        nextStage = stageOrder[currentIndex + 1];
      } else {
        return;
      }
    }

    const oldStageName = this.getStageTitle(candidate.stage);
    candidate.stage = nextStage;
    const newStageName = this.getStageTitle(nextStage);

    this.showToast(`${candidate.name} moved from ${oldStageName} to ${newStageName}!`);
  }

  // Move candidate to previous stage (if needed)
  movePrevious(candidate: KanbanCandidate): void {
    const stageOrder: CandidateStage[] = ['shortlisted', 'interviewing', 'offered', 'hired'];
    const currentIndex = stageOrder.indexOf(candidate.stage);
    if (currentIndex > 0) {
      const nextStage = stageOrder[currentIndex - 1];
      candidate.stage = nextStage;
      this.showToast(`${candidate.name} moved back to ${this.getStageTitle(nextStage)}.`);
    }
  }

  getStageTitle(stage: CandidateStage): string {
    const col = this.columns.find((c) => c.id === stage);
    return col ? col.title : stage;
  }

  // HTML5 Drag and Drop handlers
  onDragStart(event: DragEvent, candidate: KanbanCandidate): void {
    this.draggedCandidateId = candidate.id;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', candidate.id);
    }
  }

  onDragOver(event: DragEvent, stage: CandidateStage): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dragOverStage = stage;
  }

  onDragLeave(event: DragEvent): void {
    this.dragOverStage = null;
  }

  onDrop(event: DragEvent, targetStage: CandidateStage): void {
    event.preventDefault();
    this.dragOverStage = null;
    const candidateId = this.draggedCandidateId || event.dataTransfer?.getData('text/plain');

    if (candidateId) {
      const candidate = this.candidates.find((c) => c.id === candidateId);
      if (candidate && candidate.stage !== targetStage) {
        this.moveNext(candidate, targetStage);
      }
    }
    this.draggedCandidateId = null;
  }

  // Modal Handlers
  openDetailModal(candidate: KanbanCandidate): void {
    this.selectedCandidate = candidate;
    this.showDetailModal = true;
  }

  closeDetailModal(): void {
    this.showDetailModal = false;
    this.selectedCandidate = null;
    this.newNoteText = '';
  }

  addNote(): void {
    if (this.selectedCandidate && this.newNoteText.trim()) {
      if (!this.selectedCandidate.notes) {
        this.selectedCandidate.notes = [];
      }
      this.selectedCandidate.notes.push(this.newNoteText.trim());
      this.newNoteText = '';
      this.showToast('Note added successfully!');
    }
  }

  openAddModal(): void {
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
  }

  saveNewCandidate(): void {
    if (!this.newCandidate.name?.trim()) {
      this.showToast('Candidate name is required.');
      return;
    }

    const newId = `CAND-${Math.floor(1000 + Math.random() * 9000)}`;
    const candidateToAdd: KanbanCandidate = {
      id: newId,
      name: this.newCandidate.name.trim(),
      code: `CF-${Math.floor(1000 + Math.random() * 9000)}`,
      roleTrack: this.newCandidate.roleTrack || 'Java Backend (Spring Boot)',
      score: this.newCandidate.score || 85,
      stage: this.newCandidate.stage || 'shortlisted',
      email: this.newCandidate.email || `${this.newCandidate.name.toLowerCase().replace(/\s+/g, '.')}@example.com`,
      phone: this.newCandidate.phone || '+91 98000 00000',
      location: this.newCandidate.location || 'Bengaluru, KA',
      experience: this.newCandidate.experience || '1-2 Yrs',
      appliedDate: 'Just now',
      notes: ['Added to hiring pipeline via ProveYu Recruiter Portal.'],
      skills: ['Verified Passport Skills'],
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
    };

    this.candidates.unshift(candidateToAdd);
    this.closeAddModal();
    this.showToast(`Candidate "${candidateToAdd.name}" added to ${this.getStageTitle(candidateToAdd.stage)}!`);

    // Reset form
    this.newCandidate = {
      name: '',
      roleTrack: 'Java Backend (Spring Boot)',
      score: 85,
      stage: 'shortlisted',
      email: '',
      phone: '',
      location: 'Bengaluru / Hybrid',
      experience: '1-3 Yrs',
    };
  }

  showToast(msg: string): void {
    this.notificationService.showSuccess(msg);
  }
}
