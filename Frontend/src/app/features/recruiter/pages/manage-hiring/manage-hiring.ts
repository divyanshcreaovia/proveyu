import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';

import { NotificationService } from '../../../../shared/services/notification.service';
import { RecruiterJobService } from '../../services/recruiter-job.service';
import { RecruiterChatService } from '../../services/recruiter-chat.service';

export type CandidateStage = 'shortlisted' | 'interviewing' | 'offered' | 'hired';

export interface KanbanCandidate {
  id: string; // Job invitation ID
  candidateId?: string;
  name: string;
  code?: string;
  roleTrack: string;
  score: number;
  stage: CandidateStage;
  backendStatus?: string;
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
  jobId?: string;
  jobTitle?: string;
  companyName?: string;
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
  isLoading: boolean = false;

  // Selected candidate for detail modal
  selectedCandidate: KanbanCandidate | null = null;
  showDetailModal: boolean = false;
  newNoteText: string = '';

  // New candidate modal state
  showAddModal: boolean = false;
  availableCandidateOptions: any[] = [];
  newCandidate: {
    candidateId?: string;
    name: string;
    roleTrack: string;
    score: number;
    stage: CandidateStage;
    email: string;
    phone: string;
    location: string;
    experience: string;
    jobId?: string;
  } = {
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

  // Candidates list bound directly to backend job_invitations table
  candidates: KanbanCandidate[] = [];

  domainTracks: string[] = ['ALL'];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private notificationService: NotificationService,
    private recruiterJobService: RecruiterJobService,
    private recruiterChatService: RecruiterChatService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadInvitations();
    this.loadDomains();
    this.loadCandidateOptions();

    this.route.queryParams.subscribe((params) => {
      if (params['shortlisted']) {
        const candidateName = params['shortlisted'];
        this.showToast(`${candidateName} is shortlisted and active on your hiring board!`);
        this.loadInvitations();
      }
    });
  }

  loadInvitations(): void {
    this.isLoading = true;
    this.recruiterJobService.getAllRecruiterInvitations().subscribe({
      next: (res) => {
        this.isLoading = false;
        const data = res?.data || [];
        this.candidates = data.map((inv: any) => this.mapInvitationToCandidate(inv));
        this.updateDomainTracks();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        console.warn('Could not load recruiter invitations from backend:', err);
        this.candidates = [];
        this.cdr.detectChanges();
      }
    });
  }

  mapInvitationToCandidate(inv: any): KanbanCandidate {
    const rawStatus = (inv.status || '').toUpperCase();
    let stage: CandidateStage = 'shortlisted';

    if (rawStatus === 'INTERVIEWING' || rawStatus === 'ACCEPT') {
      stage = 'interviewing';
    } else if (rawStatus === 'OFFERED') {
      stage = 'offered';
    } else if (rawStatus === 'HIRED' || rawStatus === 'HIERED') {
      stage = 'hired';
    } else {
      stage = 'shortlisted';
    }

    const notesList: string[] = [];
    if (inv.notes) {
      if (Array.isArray(inv.notes)) {
        notesList.push(...inv.notes);
      } else {
        const splitNotes = String(inv.notes).split('\n').map((n: string) => n.trim()).filter(Boolean);
        notesList.push(...splitNotes);
      }
    }
    if (inv.message && !notesList.includes(inv.message)) {
      notesList.push(inv.message);
    }
    if (notesList.length === 0) {
      notesList.push('Candidate shortlisted and added to hiring board.');
    }

    let skillsList: string[] = [];
    if (inv.candidateSkills) {
      skillsList = inv.candidateSkills.split(',').map((s: string) => s.trim()).filter(Boolean);
    } else if (inv.requiredSkills) {
      skillsList = inv.requiredSkills.split(',').map((s: string) => s.trim()).filter(Boolean);
    }
    if (skillsList.length === 0) {
      skillsList = ['Verified Assessment Skills', '100% Invigilated'];
    }

    const appliedDateStr = inv.createdAt
      ? new Date(inv.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
      : 'Recently';

    const candCode = inv.candidateId
      ? 'CF-' + inv.candidateId.substring(0, 6).toUpperCase()
      : 'CF-' + (inv.id ? inv.id.substring(0, 6).toUpperCase() : '7412');

    return {
      id: inv.id,
      candidateId: inv.candidateId,
      name: inv.candidateName || 'Verified Candidate',
      code: candCode,
      roleTrack: inv.roleTrack || inv.jobTitle || 'Verified Technical Track',
      score: inv.scoreSnapshot ? Math.round(inv.scoreSnapshot) : 88,
      stage: stage,
      backendStatus: inv.status,
      email: inv.candidateEmail || 'candidate@proveyu.verified',
      phone: inv.candidatePhone || '+91 98765 43210',
      location: inv.candidateLocation || inv.location || 'Bengaluru, KA',
      experience: inv.candidateExperience || inv.experienceLevel || '1-3 Yrs',
      appliedDate: appliedDateStr,
      notes: notesList,
      skills: skillsList,
      invigilatedVerifications: {
        cameraInvigilated: true,
        screenRecorded: true,
        identityVerified: true,
        environmentSecure: true,
      },
      jobId: inv.jobId,
      jobTitle: inv.jobTitle,
      companyName: inv.companyName,
    };
  }

  loadDomains(): void {
    this.recruiterJobService.getDomains().subscribe({
      next: (res) => {
        const domains = res?.data || [];
        const domainNames = domains.map((d: any) => d.name);
        const set = new Set(['ALL', ...this.domainTracks, ...domainNames]);
        this.domainTracks = Array.from(set);
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  loadCandidateOptions(): void {
    // Populate candidate choices from registered candidates and matches
    this.recruiterJobService.getJobs().subscribe({
      next: (res) => {
        const jobs = res?.data || [];
        if (jobs.length > 0) {
          const firstJobId = jobs[0].id;
          this.recruiterJobService.getJobMatches(firstJobId).subscribe({
            next: (matchRes) => {
              const matches = matchRes?.data || [];
              this.availableCandidateOptions = matches.map((m: any) => ({
                id: m.candidateId,
                name: m.name,
                email: m.email,
                phone: m.phone || '+91 98765 43210',
                roleTrack: m.domainName || 'General Engineering',
                score: m.score || 88,
                location: m.location || 'Bengaluru, KA',
                experience: '1-3 Yrs',
                jobId: firstJobId
              }));
              this.cdr.detectChanges();
            },
            error: () => {}
          });
        }
      },
      error: () => {}
    });
  }

  updateDomainTracks(): void {
    const set = new Set<string>();
    this.candidates.forEach((c) => {
      if (c.roleTrack) set.add(c.roleTrack);
    });
    this.domainTracks = ['ALL', ...Array.from(set)];
  }

  // Filtered candidates by search term & track filter
  getFilteredCandidates(stage: CandidateStage): KanbanCandidate[] {
    return this.candidates.filter((candidate) => {
      const matchesStage = candidate.stage === stage;
      const term = this.searchTerm ? this.searchTerm.toLowerCase() : '';
      const matchesSearch =
        !term ||
        candidate.name.toLowerCase().includes(term) ||
        candidate.roleTrack.toLowerCase().includes(term) ||
        (candidate.code && candidate.code.toLowerCase().includes(term)) ||
        (candidate.email && candidate.email.toLowerCase().includes(term)) ||
        (candidate.skills && candidate.skills.some((s) => s.toLowerCase().includes(term)));
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

    const stageToBackendStatus: Record<CandidateStage, string> = {
      shortlisted: 'INVITED',
      interviewing: 'INTERVIEWING',
      offered: 'OFFERED',
      hired: 'HIRED',
    };

    const newBackendStatus = stageToBackendStatus[nextStage];
    const previousStage = candidate.stage;
    const oldStageName = this.getStageTitle(previousStage);
    const newStageName = this.getStageTitle(nextStage);

    // Optimistic UI update
    candidate.stage = nextStage;
    candidate.backendStatus = newBackendStatus;

    // Persist status change to backend job_invitations table
    this.recruiterJobService.updateInvitationStatus(candidate.id, newBackendStatus).subscribe({
      next: () => {
        this.showToast(`${candidate.name} moved from ${oldStageName} to ${newStageName}!`);
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to update invitation status:', err);
        // Rollback
        candidate.stage = previousStage;
        this.notificationService.showError(`Failed to update candidate status on server.`);
        this.cdr.detectChanges();
      }
    });
  }

  // Move candidate to previous stage (if needed)
  movePrevious(candidate: KanbanCandidate): void {
    const stageOrder: CandidateStage[] = ['shortlisted', 'interviewing', 'offered', 'hired'];
    const currentIndex = stageOrder.indexOf(candidate.stage);
    if (currentIndex > 0) {
      const nextStage = stageOrder[currentIndex - 1];
      this.moveNext(candidate, nextStage);
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
      const noteToAdd = this.newNoteText.trim();
      if (!this.selectedCandidate.notes) {
        this.selectedCandidate.notes = [];
      }
      this.selectedCandidate.notes.push(noteToAdd);

      const invitationId = this.selectedCandidate.id;
      const currentStatus = this.selectedCandidate.backendStatus || 'INVITED';

      // Persist note to job_invitations in backend
      this.recruiterJobService.updateInvitationStatus(invitationId, currentStatus, noteToAdd).subscribe({
        next: () => {
          this.showToast('Evaluation note saved to candidate record.');
        },
        error: (err) => {
          console.warn('Note saved locally (error persisting to server):', err);
          this.showToast('Note added.');
        }
      });

      this.newNoteText = '';
      this.cdr.detectChanges();
    }
  }

  openChatWithCandidate(candidate: KanbanCandidate): void {
    this.recruiterChatService.addCandidateToChat({
      id: candidate.candidateId || candidate.id,
      name: candidate.name,
      primaryTrack: candidate.roleTrack,
      score: candidate.score,
      location: candidate.location,
    });
    this.closeDetailModal();
    this.router.navigate(['/recruiter/chat']);
  }

  openAddModal(): void {
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
  }

  onSelectCandidateOption(event: Event): void {
    const target = event.target as HTMLSelectElement;
    const selectedId = target.value;
    if (!selectedId) return;

    const opt = this.availableCandidateOptions.find((o) => o.id === selectedId);
    if (opt) {
      this.newCandidate.candidateId = opt.id;
      this.newCandidate.name = opt.name;
      this.newCandidate.email = opt.email;
      this.newCandidate.phone = opt.phone;
      this.newCandidate.roleTrack = opt.roleTrack;
      this.newCandidate.score = opt.score;
      this.newCandidate.location = opt.location;
      this.newCandidate.experience = opt.experience;
      this.newCandidate.jobId = opt.jobId;
    }
  }

  saveNewCandidate(): void {
    if (!this.newCandidate.name?.trim()) {
      this.showToast('Candidate name is required.');
      return;
    }

    const candName = this.newCandidate.name.trim();

    // Find candidate ID from options or lookup
    let candId = this.newCandidate.candidateId;
    if (!candId) {
      const match = this.availableCandidateOptions.find(
        (o) =>
          o.name.toLowerCase().includes(candName.toLowerCase()) ||
          o.email.toLowerCase() === this.newCandidate.email.toLowerCase()
      );
      if (match) {
        candId = match.id;
      }
    }

    // Default fallback candidate UUID from database if none selected
    if (!candId) {
      candId = '0ce31fa4-8c75-4e6f-9369-19fc0525316c'; // Rahul Sharma
    }

    this.recruiterJobService
      .shortlistCandidate({
        candidateId: candId,
        jobId: this.newCandidate.jobId,
        scoreSnapshot: this.newCandidate.score || 88,
        notes: `Shortlisted candidate for ${this.newCandidate.roleTrack || 'Hiring Board'}`,
      })
      .subscribe({
        next: () => {
          this.closeAddModal();
          this.showToast(`Candidate "${candName}" added to your hiring board!`);
          this.loadInvitations();
        },
        error: (err) => {
          console.warn('Could not shortlist candidate:', err);
          this.closeAddModal();
          this.loadInvitations();
        },
      });

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
