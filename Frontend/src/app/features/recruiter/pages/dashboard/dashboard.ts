import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

export interface DashboardStatCard {
  label: string;
  value: string | number;
  subtext: string;
  icon: string;
  colorClass: string;
  badgeText?: string;
}

export interface PipelineStageSummary {
  id: string;
  name: string;
  count: number;
  badgeBg: string;
  candidatesPreview: string[];
}

export interface RequirementSummary {
  id: number;
  jobTitle: string;
  domainTrack: string;
  targetClient: string;
  location: string;
  salaryPackage: string;
  minScoreThreshold: number;
  candidateMatchesCount: number;
  postedDate: string;
}

export interface RecentActivityItem {
  id: number;
  candidateName: string;
  action: string;
  timestamp: string;
  icon: string;
  iconBg: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  companyName: string = 'ProveYu Enterprise Tech';
  recruiterName: string = 'Divyansh Gupta';
  recruiterRole: string = 'Senior Technical Recruiter';
  recruiterAvatar: string = 'images/logo1.png';

  // Overall KPI Cards
  kpiStats: DashboardStatCard[] = [
    {
      label: 'ACTIVE REQUIREMENTS',
      value: 3,
      subtext: '100% Verified Listings',
      icon: 'bi-briefcase-fill',
      colorClass: 'text-navy',
      badgeText: 'Live Pipeline',
    },
    {
      label: 'CANDIDATES IN PIPELINE',
      value: 5,
      subtext: 'Kanban Stage Tracking',
      icon: 'bi-kanban-fill',
      colorClass: 'text-primary-custom',
      badgeText: 'Active',
    },
    {
      label: 'VERIFIED MATCHES',
      value: 74,
      subtext: 'Score ≥ 80 Cutoff',
      icon: 'bi-check-circle-fill',
      colorClass: 'text-success',
      badgeText: 'Invigilated',
    },
    {
      label: 'AVG MATCH CUTOFF',
      value: '84%',
      subtext: 'Proctored Passports',
      icon: 'bi-speedometer2',
      colorClass: 'text-warning',
      badgeText: 'Top 5% Tier',
    },
  ];

  // Pipeline Stage Summary
  pipelineStages: PipelineStageSummary[] = [
    {
      id: 'shortlisted',
      name: 'Shortlisted',
      count: 2,
      badgeBg: '#56617a',
      candidatesPreview: ['Candidate #CF-7412', 'Candidate #CF-3301'],
    },
    {
      id: 'interviewing',
      name: 'Interviewing',
      count: 1,
      badgeBg: '#00875a',
      candidatesPreview: ['Rahul Sharma'],
    },
    {
      id: 'offered',
      name: 'Offered',
      count: 1,
      badgeBg: '#9100fa',
      candidatesPreview: ['Priya Nambiar'],
    },
    {
      id: 'hired',
      name: 'Hired',
      count: 1,
      badgeBg: '#00c875',
      candidatesPreview: ['Amitav Ghosh'],
    },
  ];

  // Active Job Requirements List
  activeRequirements: RequirementSummary[] = [
    {
      id: 1,
      jobTitle: 'Associate QA Automation Engineer',
      domainTrack: 'QA & Testing',
      targetClient: 'CloudScale Technologies',
      location: 'Bengaluru / Remote',
      salaryPackage: '₹8 - 10 LPA',
      minScoreThreshold: 85,
      candidateMatchesCount: 18,
      postedDate: '23 Sep 2026',
    },
    {
      id: 2,
      jobTitle: 'Junior Java Spring Boot Developer',
      domainTrack: 'Java Spring Boot Microservices',
      targetClient: 'Apex Placement Agency',
      location: 'Hyderabad / On-site',
      salaryPackage: '₹10 - 12 LPA',
      minScoreThreshold: 80,
      candidateMatchesCount: 32,
      postedDate: '20 Sep 2026',
    },
    {
      id: 3,
      jobTitle: 'Full Stack React & Node Engineer',
      domainTrack: 'Full Stack Engineering',
      targetClient: 'InnoTech Solutions',
      location: 'Pune / Hybrid',
      salaryPackage: '₹12 - 15 LPA',
      minScoreThreshold: 88,
      candidateMatchesCount: 24,
      postedDate: '15 Sep 2026',
    },
  ];

  // Invigilated Proctoring Verification Stats
  verificationMetrics = [
    { label: 'Webcam AI Proctoring', percentage: 100, icon: 'bi-camera-video-fill' },
    { label: 'Screen & Environment Lock', percentage: 100, icon: 'bi-display-fill' },
    { label: 'Govt Biometric Verification', percentage: 98, icon: 'bi-fingerprint' },
    { label: 'Live Code Execution Trace', percentage: 100, icon: 'bi-code-square' },
  ];

  // Recent Hiring Activity Log
  recentActivities: RecentActivityItem[] = [
    {
      id: 1,
      candidateName: 'Priya Nambiar',
      action: 'Offer letter generated at ₹9.5 LPA (QA & Automation)',
      timestamp: '10 mins ago',
      icon: 'bi-file-earmark-check-fill',
      iconBg: '#9100fa',
    },
    {
      id: 2,
      candidateName: 'Rahul Sharma',
      action: 'Completed Technical Round 1 - Recommended for Offer',
      timestamp: '1 hour ago',
      icon: 'bi-person-check-fill',
      iconBg: '#00875a',
    },
    {
      id: 3,
      candidateName: 'Candidate #CF-7412',
      action: 'Achieved 89/100 score on Java Spring Boot Invigilated Lab',
      timestamp: '3 hours ago',
      icon: 'bi-patch-check-fill',
      iconBg: '#56617a',
    },
    {
      id: 4,
      candidateName: 'Amitav Ghosh',
      action: 'Offer Accepted! Joining date set to Oct 1, 2026',
      timestamp: 'Yesterday',
      icon: 'bi-trophy-fill',
      iconBg: '#00c875',
    },
  ];

  ngOnInit(): void {}
}
