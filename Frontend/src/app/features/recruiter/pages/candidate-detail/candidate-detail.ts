import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { RecruiterChatService } from '../../services/recruiter-chat.service';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface SkillScore {
  label: string;
  score: string;
}

export interface CandidateProfileItem {
  id: number;
  name: string;
  collegeExp: string;
  percentileBadge: string;
  tierBadge: string;
  score: number;
  primaryTrack: string;
  location: string;
  verifiedCenter: string;
  skillsBreakdown: SkillScore[];
  isInvited: boolean;
  isShortlisted?: boolean;
  passportId: string;
}

@Component({
  selector: 'app-candidate-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './candidate-detail.html',
  styleUrl: './candidate-detail.scss',
})
export class CandidateDetail implements OnInit {
  viewMode: 'grid' | 'table' = 'grid';
  selectedTrack: string = 'All';
  searchQuery: string = '';
  minScoreFilter: number = 0;
  sortBy: string = 'Relevance';

  setViewMode(mode: 'grid' | 'table') {
    this.viewMode = mode;
  }

  selectedCandidateForPassport: CandidateProfileItem | null = null;
  showPassportModal: boolean = false;

  candidates: CandidateProfileItem[] = [
    {
      id: 1,
      name: 'Rahul Sharma',
      collegeExp: 'RV College of Engineering · Fresher (2026)',
      percentileBadge: 'Top 3%',
      tierBadge: 'Tier 1 Verified',
      score: 92,
      primaryTrack: 'Universal Fresher Competency',
      location: 'Bengaluru',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '88%' },
        { label: 'Backend APIs', score: '92%' },
        { label: 'DSA & Algorithms', score: '85%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-8921',
    },
    {
      id: 2,
      name: 'Candidate #CF-7412',
      collegeExp: 'BMS College of Engineering · 1-3 Yrs Exp',
      percentileBadge: 'Top 5%',
      tierBadge: 'Tier 1 Verified',
      score: 89,
      primaryTrack: 'Java Backend (Spring Boot)',
      location: 'Bengaluru',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '88%' },
        { label: 'Backend APIs', score: '92%' },
        { label: 'DSA & Algorithms', score: '85%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-7412',
    },
    {
      id: 3,
      name: 'Priya Nambiar',
      collegeExp: 'PES University · Fresher (2026)',
      percentileBadge: 'Top 1%',
      tierBadge: 'Tier 1 Verified',
      score: 95,
      primaryTrack: 'QA & Automation Testing',
      location: 'Hyderabad',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '88%' },
        { label: 'Backend APIs', score: '92%' },
        { label: 'DSA & Algorithms', score: '85%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-9501',
    },
    {
      id: 4,
      name: 'Amitav Ghosh',
      collegeExp: 'IIT Kanpur · Fresher (2025)',
      percentileBadge: 'Top 10%',
      tierBadge: 'Tier 2 Verified',
      score: 86,
      primaryTrack: 'Python Backend & Data',
      location: 'Delhi NCR',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '88%' },
        { label: 'Backend APIs', score: '92%' },
        { label: 'DSA & Algorithms', score: '85%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-8640',
    },
    {
      id: 5,
      name: 'Siddharth Rao',
      collegeExp: 'MS Ramaiah Inst. of Technology · Fresher (2026)',
      percentileBadge: 'Top 2%',
      tierBadge: 'Tier 1 Verified',
      score: 94,
      primaryTrack: 'Full Stack Node & React',
      location: 'Bengaluru',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '90%' },
        { label: 'Backend APIs', score: '95%' },
        { label: 'DSA & Algorithms', score: '88%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-9410',
    },
    {
      id: 6,
      name: 'Neha Kulkarni',
      collegeExp: 'COEP Pune · 1-2 Yrs Exp',
      percentileBadge: 'Top 4%',
      tierBadge: 'Tier 1 Verified',
      score: 90,
      primaryTrack: 'QA & Automation Testing',
      location: 'Pune',
      verifiedCenter: 'TCS iON Digital Zone',
      skillsBreakdown: [
        { label: 'System Design', score: '86%' },
        { label: 'Backend APIs', score: '91%' },
        { label: 'DSA & Algorithms', score: '84%' },
      ],
      isInvited: false,
      isShortlisted: false,
      passportId: 'PASSPORT-2026-9042',
    }
  ];

  constructor(
    private router: Router,
    private recruiterChatService: RecruiterChatService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {}

  get filteredCandidates(): CandidateProfileItem[] {
    let result = this.candidates.filter(c => {
      const matchesTrack = this.selectedTrack === 'All' || c.primaryTrack.toLowerCase().includes(this.selectedTrack.toLowerCase());
      const matchesScore = c.score >= this.minScoreFilter;
      const matchesSearch = !this.searchQuery || 
        c.name.toLowerCase().includes(this.searchQuery.toLowerCase()) || 
        c.collegeExp.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.primaryTrack.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.location.toLowerCase().includes(this.searchQuery.toLowerCase());
      return matchesTrack && matchesScore && matchesSearch;
    });

    if (this.sortBy === 'ScoreHigh') {
      result.sort((a, b) => b.score - a.score);
    } else if (this.sortBy === 'ScoreLow') {
      result.sort((a, b) => a.score - b.score);
    }

    return result;
  }

  toggleShortlist(candidate: CandidateProfileItem) {
    candidate.isShortlisted = !candidate.isShortlisted;
    if (candidate.isShortlisted) {
      this.recruiterChatService.addCandidateToChat(candidate);
      this.notificationService.showSuccess(`${candidate.name} shortlisted and added to Candidate Chats!`, 'Shortlisted');
    } else {
      this.notificationService.showInfo(`${candidate.name} removed from shortlist.`, 'Shortlist Updated');
    }
  }

  openChat(candidate: CandidateProfileItem) {
    this.recruiterChatService.addCandidateToChat(candidate);
    this.router.navigate(['/recruiter/chat']);
  }

  openPassport(candidate: CandidateProfileItem) {
    this.selectedCandidateForPassport = candidate;
    this.showPassportModal = true;
  }

  closePassport() {
    this.showPassportModal = false;
    this.selectedCandidateForPassport = null;
  }

  sendInvite(candidate: CandidateProfileItem) {
    candidate.isInvited = true;
    candidate.isShortlisted = true;
    this.recruiterChatService.addCandidateToChat(candidate);
    this.closePassport();
    this.router.navigate(['/recruiter/chat']);
  }
}
