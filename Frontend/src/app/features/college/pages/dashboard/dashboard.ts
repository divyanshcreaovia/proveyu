import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HighchartsChartComponent } from 'highcharts-angular';
import * as Highcharts from 'highcharts';

export interface TopKpiStat {
  title: string;
  value: string;
  change: string;
  changePositive: boolean;
  icon: string;
  iconBg: string;
  iconColor: string;
}

export interface DriveStatusItem {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

export interface RecruiterItem {
  name: string;
  code: string;
  offers: number;
  bg: string;
  text: string;
  logoLetter: string;
}

export interface RecentDriveItem {
  company: string;
  code: string;
  role: string;
  date: string;
  eligible: number;
  status: string;
  statusClass: string;
  bg: string;
  text: string;
}

export interface ReadinessItem {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

export interface CampusPerformer {
  rank: number;
  rollNo: string;
  name: string;
  branch: string;
  score: number;
  maxScore: number;
  tier: string;
  avatarBg: string;
}

export interface FunnelStage {
  stage: string;
  count: number;
  formattedCount: string;
  percentage: number;
}

export interface PolicyComplianceItem {
  title: string;
  count: string;
  sub: string;
  icon: string;
  iconBg: string;
  iconColor: string;
}

export interface DriveCalendarEvent {
  day: string;
  month: string;
  company: string;
  role: string;
  status: string;
  statusClass: string;
  dateBg: string;
}

export interface AssessmentReportItem {
  title: string;
  attempted: number;
  date: string;
  avgScore: number;
  topSkill: string;
  remedialCount: number;
  statusBadge: string;
  badgeClass: string;
}

@Component({
  selector: 'app-college-dashboard',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule, HighchartsChartComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  Highcharts: typeof Highcharts = Highcharts;

  // Top Header State
  searchQuery: string = '';
  selectedAcademicYear: string = '2024 - 25';
  academicYears: string[] = ['2024 - 25', '2023 - 24', '2022 - 23'];

  // Filter options
  timeFilters: string[] = ['This Year', 'Last Year', '2024 Batch', '2025 Batch'];
  readinessTierFilters: string[] = ['Overall', 'Test 1', 'Test 2', 'Test 3', 'Test 4'];
  selectedOverviewFilter: string = 'This Year';
  selectedDriveStatusFilter: string = 'This Year';
  selectedReadinessFilter: string = 'This Year';
  selectedFunnelFilter: string = 'This Year';
  selectedAssessmentFilter: string = 'Overall';

  // Top Recruiters Sorting state
  recruiterSortKey: 'name' | 'offers' = 'offers';
  recruiterSortAsc: boolean = false; // default offers descending (high to low)

  toggleRecruiterSort(key: 'name' | 'offers'): void {
    if (this.recruiterSortKey === key) {
      this.recruiterSortAsc = !this.recruiterSortAsc;
    } else {
      this.recruiterSortKey = key;
      this.recruiterSortAsc = key === 'name' ? true : false;
    }
  }

  getFilteredTopRecruiters(): RecruiterItem[] {
    let list = [...this.topRecruiters];
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(r => r.name.toLowerCase().includes(q) || r.code.toLowerCase().includes(q));
    }

    list.sort((a, b) => {
      let comp = 0;
      if (this.recruiterSortKey === 'name') {
        comp = a.name.localeCompare(b.name);
      } else {
        comp = a.offers - b.offers;
      }
      return this.recruiterSortAsc ? comp : -comp;
    });

    return list;
  }

  getFilteredRecentDrives(): RecentDriveItem[] {
    if (!this.searchQuery.trim()) return this.recentDrives;
    const q = this.searchQuery.toLowerCase();
    return this.recentDrives.filter(d => d.company.toLowerCase().includes(q) || d.role.toLowerCase().includes(q) || d.status.toLowerCase().includes(q));
  }


  // 1. Top KPI Cards Row
  kpiStats: TopKpiStat[] = [
    {
      title: 'Total Companies',
      value: '245',
      change: '18% vs last year',
      changePositive: true,
      icon: 'bi-building',
      iconBg: '#EEF2FF',
      iconColor: '#4F46E5',
    },
    {
      title: 'Active Drives',
      value: '64',
      change: '15% vs last year',
      changePositive: true,
      icon: 'bi-calendar-event',
      iconBg: '#ECFDF5',
      iconColor: '#10B981',
    },
    {
      title: 'Students Registered',
      value: '3,248',
      change: '12% vs last year',
      changePositive: true,
      icon: 'bi-person-check',
      iconBg: '#F3E8FF',
      iconColor: '#9333EA',
    },
    {
      title: 'Offers Made',
      value: '426',
      change: '20% vs last year',
      changePositive: true,
      icon: 'bi-briefcase',
      iconBg: '#FFEDD5',
      iconColor: '#F97316',
    },
    {
      title: 'Placement Rate',
      value: '78.5%',
      change: '8% vs last year',
      changePositive: true,
      icon: 'bi-bullseye',
      iconBg: '#CCFBF1',
      iconColor: '#14B8A6',
    },
  ];

  // 2. Highcharts - Placement Overview
  placementOverviewOptions: Highcharts.Options = {};

  // 3. Drive Status Donut Chart & Legend
  driveStatusOptions: Highcharts.Options = {};
  driveStatusList: DriveStatusItem[] = [
    { label: 'Upcoming', count: 18, percentage: 28.1, color: '#3B82F6' },
    { label: 'Ongoing', count: 22, percentage: 34.4, color: '#10B981' },
    { label: 'Completed', count: 20, percentage: 31.3, color: '#8B5CF6' },
    { label: 'Cancelled', count: 4, percentage: 6.2, color: '#F97316' },
  ];

  // 4. Top Recruiters (30 companies with distinct colors & 3-letter tags)
  topRecruiters: RecruiterItem[] = [
    { name: 'TCS', code: 'TCS', offers: 89, bg: '#3730A3', text: '#FFFFFF', logoLetter: 'TCS' },
    { name: 'Infosys', code: 'INF', offers: 67, bg: '#0284C7', text: '#FFFFFF', logoLetter: 'INF' },
    { name: 'Wipro', code: 'WIP', offers: 43, bg: '#0F172A', text: '#FFFFFF', logoLetter: 'WIP' },
    { name: 'Amazon', code: 'AMZ', offers: 32, bg: '#FF9900', text: '#000000', logoLetter: 'amz' },
    { name: 'Deloitte', code: 'DEL', offers: 28, bg: '#86EFAC', text: '#064E3B', logoLetter: 'D.' },
    { name: 'Capgemini', code: 'CAP', offers: 24, bg: '#0284C7', text: '#FFFFFF', logoLetter: 'CAP' },
    { name: 'Accenture', code: 'ACC', offers: 21, bg: '#A855F7', text: '#FFFFFF', logoLetter: 'AC' },
    { name: 'Cognizant', code: 'COG', offers: 20, bg: '#1E3A8A', text: '#FFFFFF', logoLetter: 'CTS' },
    { name: 'LTIMindtree', code: 'LTI', offers: 18, bg: '#E11D48', text: '#FFFFFF', logoLetter: 'LTI' },
    { name: 'HCLTech', code: 'HCL', offers: 17, bg: '#2563EB', text: '#FFFFFF', logoLetter: 'HCL' },
    { name: 'Tech Mahindra', code: 'TCH', offers: 15, bg: '#DC2626', text: '#FFFFFF', logoLetter: 'TCH' },
    { name: 'Microsoft', code: 'MSFT', offers: 14, bg: '#00A4EF', text: '#FFFFFF', logoLetter: 'MSF' },
    { name: 'Google', code: 'GOOG', offers: 12, bg: '#EA4335', text: '#FFFFFF', logoLetter: 'GOG' },
    { name: 'IBM', code: 'IBM', offers: 11, bg: '#052F5F', text: '#FFFFFF', logoLetter: 'IBM' },
    { name: 'Oracle', code: 'ORCL', offers: 10, bg: '#C74634', text: '#FFFFFF', logoLetter: 'ORC' },
    { name: 'Goldman Sachs', code: 'GS', offers: 9, bg: '#739EC7', text: '#000000', logoLetter: 'GSA' },
    { name: 'JPMorgan Chase', code: 'JPM', offers: 9, bg: '#111827', text: '#FFFFFF', logoLetter: 'JPM' },
    { name: 'SAP Labs', code: 'SAP', offers: 8, bg: '#008FD3', text: '#FFFFFF', logoLetter: 'SAP' },
    { name: 'Cisco Systems', code: 'CSCO', offers: 8, bg: '#049FD9', text: '#FFFFFF', logoLetter: 'CSC' },
    { name: 'NVIDIA', code: 'NVDA', offers: 7, bg: '#76B900', text: '#000000', logoLetter: 'NVD' },
    { name: 'Intel Corporation', code: 'INTC', offers: 7, bg: '#0068B5', text: '#FFFFFF', logoLetter: 'INT' },
    { name: 'Reliance Jio', code: 'JIO', offers: 6, bg: '#0A2540', text: '#FFFFFF', logoLetter: 'JIO' },
    { name: 'Adani Group', code: 'ADA', offers: 6, bg: '#0F766E', text: '#FFFFFF', logoLetter: 'ADA' },
    { name: 'Uber Technologies', code: 'UBER', offers: 5, bg: '#000000', text: '#FFFFFF', logoLetter: 'UBR' },
    { name: 'Zomato', code: 'ZOM', offers: 5, bg: '#E23744', text: '#FFFFFF', logoLetter: 'ZOM' },
    { name: 'Swiggy', code: 'SWIG', offers: 5, bg: '#FC8019', text: '#FFFFFF', logoLetter: 'SWG' },
    { name: 'DE Shaw', code: 'DES', offers: 4, bg: '#4338CA', text: '#FFFFFF', logoLetter: 'DES' },
    { name: 'Barclays', code: 'BARC', offers: 4, bg: '#00AEEF', text: '#FFFFFF', logoLetter: 'BRC' },
    { name: 'Citibank', code: 'CITI', offers: 4, bg: '#003B70', text: '#FFFFFF', logoLetter: 'CIT' },
    { name: 'Atlassian', code: 'ATLA', offers: 4, bg: '#0052CC', text: '#FFFFFF', logoLetter: 'ATL' },
  ];

  // 5. Recent Drives (10 companies)
  recentDrives: RecentDriveItem[] = [
    {
      company: 'TCS',
      code: 'TCS',
      role: 'Digital Engineer',
      date: '25 May 2024',
      eligible: 320,
      status: 'Ongoing',
      statusClass: 'status-pill-ongoing',
      bg: '#3730A3',
      text: '#FFFFFF',
    },
    {
      company: 'Infosys',
      code: 'INF',
      role: 'System Engineer',
      date: '28 May 2024',
      eligible: 280,
      status: 'Completed',
      statusClass: 'status-pill-completed',
      bg: '#0284C7',
      text: '#FFFFFF',
    },
    {
      company: 'Amazon',
      code: 'AMZ',
      role: 'SDE Intern',
      date: '02 Jun 2024',
      eligible: 160,
      status: 'Ongoing',
      statusClass: 'status-pill-ongoing',
      bg: '#FF9900',
      text: '#000000',
    },
    {
      company: 'Deloitte',
      code: 'DEL',
      role: 'Business Analyst',
      date: '05 Jun 2024',
      eligible: 120,
      status: 'Completed',
      statusClass: 'status-pill-completed',
      bg: '#86EFAC',
      text: '#064E3B',
    },
    {
      company: 'Wipro',
      code: 'WIP',
      role: 'Project Engineer',
      date: '10 Jun 2024',
      eligible: 210,
      status: 'Completed',
      statusClass: 'status-pill-completed',
      bg: '#0F172A',
      text: '#FFFFFF',
    },
    {
      company: 'Capgemini',
      code: 'CAP',
      role: 'Senior Analyst',
      date: '14 Jun 2024',
      eligible: 195,
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming',
      bg: '#0284C7',
      text: '#FFFFFF',
    },
    {
      company: 'Accenture',
      code: 'ACC',
      role: 'Associate Software Eng',
      date: '18 Jun 2024',
      eligible: 240,
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming',
      bg: '#A855F7',
      text: '#FFFFFF',
    },
    {
      company: 'Cognizant',
      code: 'COG',
      role: 'Programmer Analyst',
      date: '22 Jun 2024',
      eligible: 200,
      status: 'Completed',
      statusClass: 'status-pill-completed',
      bg: '#1E3A8A',
      text: '#FFFFFF',
    },
    {
      company: 'LTIMindtree',
      code: 'LTI',
      role: 'Cloud Specialist',
      date: '26 Jun 2024',
      eligible: 150,
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming',
      bg: '#E11D48',
      text: '#FFFFFF',
    },
    {
      company: 'HCLTech',
      code: 'HCL',
      role: 'Graduate Trainee',
      date: '30 Jun 2024',
      eligible: 175,
      status: 'Completed',
      statusClass: 'status-pill-completed',
      bg: '#2563EB',
      text: '#FFFFFF',
    },
  ];

  // 6. Student Readiness Donut Chart & Legend
  // 6. Top Campus Performers Leaderboard
  performerFilters: string[] = ['Overall', 'Test 1', 'Test 2', 'Test 3', 'Test 4'];
  selectedPerformerFilter: string = 'Overall';

  performerDataMap: Record<string, CampusPerformer[]> = {
    'Overall': [
      { rank: 1, rollNo: '2024-CSE-015', name: 'Ananya Roy', branch: 'Computer Science', score: 92, maxScore: 100, tier: 'Tier 1', avatarBg: '#4F46E5' },
      { rank: 2, rollNo: '2024-IT-008', name: 'Sandhya Kulkarni', branch: 'Information Tech', score: 88, maxScore: 100, tier: 'Tier 1', avatarBg: '#0284C7' },
      { rank: 3, rollNo: '2024-CSE-001', name: 'Rahul Sharma', branch: 'Computer Science', score: 86, maxScore: 100, tier: 'Tier 1', avatarBg: '#10B981' },
      { rank: 4, rollNo: '2024-CSE-002', name: 'Priya Desai', branch: 'Computer Science', score: 82, maxScore: 100, tier: 'Tier 1', avatarBg: '#EC4899' },
      { rank: 5, rollNo: '2024-IT-025', name: 'Karan Malhotra', branch: 'Information Tech', score: 78, maxScore: 100, tier: 'Tier 1', avatarBg: '#8B5CF6' },
      { rank: 6, rollNo: '2024-ECE-012', name: 'Aarav Verma', branch: 'Electronics & Comm', score: 76, maxScore: 100, tier: 'Tier 1', avatarBg: '#F59E0B' },
      { rank: 7, rollNo: '2024-CSE-045', name: 'Neha Gupta', branch: 'Computer Science', score: 75, maxScore: 100, tier: 'Tier 1', avatarBg: '#06B6D4' },
      { rank: 8, rollNo: '2024-IT-019', name: 'Rohan Mehta', branch: 'Information Tech', score: 74, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 9, rollNo: '2024-ME-003', name: 'Vikram Singh', branch: 'Mechanical Engg', score: 72, maxScore: 100, tier: 'Tier 1', avatarBg: '#64748B' },
      { rank: 10, rollNo: '2024-CSE-033', name: 'Isha Patel', branch: 'Computer Science', score: 71, maxScore: 100, tier: 'Tier 1', avatarBg: '#14B8A6' },
      { rank: 11, rollNo: '2024-EE-010', name: 'Aditya Rao', branch: 'Electrical Engg', score: 70, maxScore: 100, tier: 'Tier 1', avatarBg: '#EF4444' },
      { rank: 12, rollNo: '2024-CSE-052', name: 'Tanvi Joshi', branch: 'Computer Science', score: 69, maxScore: 100, tier: 'Tier 2', avatarBg: '#3B82F6' },
      { rank: 13, rollNo: '2024-IT-031', name: 'Devansh Kapoor', branch: 'Information Tech', score: 68, maxScore: 100, tier: 'Tier 2', avatarBg: '#6366F1' },
      { rank: 14, rollNo: '2024-ECE-024', name: 'Siddharth Jain', branch: 'Electronics & Comm', score: 67, maxScore: 100, tier: 'Tier 2', avatarBg: '#8B5CF6' },
      { rank: 15, rollNo: '2024-CSE-068', name: 'Riya Shah', branch: 'Computer Science', score: 66, maxScore: 100, tier: 'Tier 2', avatarBg: '#EC4899' },
      { rank: 16, rollNo: '2024-IT-042', name: 'Kabir Bansal', branch: 'Information Tech', score: 65, maxScore: 100, tier: 'Tier 2', avatarBg: '#10B981' },
      { rank: 17, rollNo: '2024-ME-015', name: 'Manish Kumar', branch: 'Mechanical Engg', score: 64, maxScore: 100, tier: 'Tier 2', avatarBg: '#64748B' },
      { rank: 18, rollNo: '2024-CSE-079', name: 'Simran Arora', branch: 'Computer Science', score: 63, maxScore: 100, tier: 'Tier 2', avatarBg: '#F59E0B' },
      { rank: 19, rollNo: '2024-EE-022', name: 'Varun Nair', branch: 'Electrical Engg', score: 62, maxScore: 100, tier: 'Tier 2', avatarBg: '#0284C7' },
      { rank: 20, rollNo: '2024-ECE-037', name: 'Meera Kulkarni', branch: 'Electronics & Comm', score: 61, maxScore: 100, tier: 'Tier 2', avatarBg: '#A855F7' },
    ],
    'Test 1': [
      { rank: 1, rollNo: '2024-CSE-015', name: 'Ananya Roy', branch: 'Computer Science', score: 96, maxScore: 100, tier: 'Tier 1', avatarBg: '#4F46E5' },
      { rank: 2, rollNo: '2024-CSE-001', name: 'Rahul Sharma', branch: 'Computer Science', score: 92, maxScore: 100, tier: 'Tier 1', avatarBg: '#10B981' },
      { rank: 3, rollNo: '2024-IT-008', name: 'Sandhya Kulkarni', branch: 'Information Tech', score: 90, maxScore: 100, tier: 'Tier 1', avatarBg: '#0284C7' },
      { rank: 4, rollNo: '2024-IT-025', name: 'Karan Malhotra', branch: 'Information Tech', score: 85, maxScore: 100, tier: 'Tier 1', avatarBg: '#8B5CF6' },
      { rank: 5, rollNo: '2024-ECE-012', name: 'Aarav Verma', branch: 'Electronics & Comm', score: 83, maxScore: 100, tier: 'Tier 1', avatarBg: '#F59E0B' },
      { rank: 6, rollNo: '2024-CSE-002', name: 'Priya Desai', branch: 'Computer Science', score: 81, maxScore: 100, tier: 'Tier 1', avatarBg: '#EC4899' },
      { rank: 7, rollNo: '2024-CSE-045', name: 'Neha Gupta', branch: 'Computer Science', score: 79, maxScore: 100, tier: 'Tier 1', avatarBg: '#06B6D4' },
      { rank: 8, rollNo: '2024-IT-019', name: 'Rohan Mehta', branch: 'Information Tech', score: 77, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 9, rollNo: '2024-CSE-033', name: 'Isha Patel', branch: 'Computer Science', score: 75, maxScore: 100, tier: 'Tier 1', avatarBg: '#14B8A6' },
      { rank: 10, rollNo: '2024-EE-010', name: 'Aditya Rao', branch: 'Electrical Engg', score: 74, maxScore: 100, tier: 'Tier 1', avatarBg: '#EF4444' },
      { rank: 11, rollNo: '2024-ME-003', name: 'Vikram Singh', branch: 'Mechanical Engg', score: 73, maxScore: 100, tier: 'Tier 1', avatarBg: '#64748B' },
      { rank: 12, rollNo: '2024-CSE-052', name: 'Tanvi Joshi', branch: 'Computer Science', score: 71, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 13, rollNo: '2024-IT-031', name: 'Devansh Kapoor', branch: 'Information Tech', score: 69, maxScore: 100, tier: 'Tier 2', avatarBg: '#6366F1' },
      { rank: 14, rollNo: '2024-ECE-024', name: 'Siddharth Jain', branch: 'Electronics & Comm', score: 68, maxScore: 100, tier: 'Tier 2', avatarBg: '#8B5CF6' },
      { rank: 15, rollNo: '2024-CSE-068', name: 'Riya Shah', branch: 'Computer Science', score: 67, maxScore: 100, tier: 'Tier 2', avatarBg: '#EC4899' },
      { rank: 16, rollNo: '2024-IT-042', name: 'Kabir Bansal', branch: 'Information Tech', score: 66, maxScore: 100, tier: 'Tier 2', avatarBg: '#10B981' },
      { rank: 17, rollNo: '2024-ME-015', name: 'Manish Kumar', branch: 'Mechanical Engg', score: 65, maxScore: 100, tier: 'Tier 2', avatarBg: '#64748B' },
      { rank: 18, rollNo: '2024-CSE-079', name: 'Simran Arora', branch: 'Computer Science', score: 64, maxScore: 100, tier: 'Tier 2', avatarBg: '#F59E0B' },
      { rank: 19, rollNo: '2024-EE-022', name: 'Varun Nair', branch: 'Electrical Engg', score: 63, maxScore: 100, tier: 'Tier 2', avatarBg: '#0284C7' },
      { rank: 20, rollNo: '2024-ECE-037', name: 'Meera Kulkarni', branch: 'Electronics & Comm', score: 62, maxScore: 100, tier: 'Tier 2', avatarBg: '#A855F7' },
    ],
    'Test 2': [
      { rank: 1, rollNo: '2024-IT-008', name: 'Sandhya Kulkarni', branch: 'Information Tech', score: 94, maxScore: 100, tier: 'Tier 1', avatarBg: '#0284C7' },
      { rank: 2, rollNo: '2024-CSE-002', name: 'Priya Desai', branch: 'Computer Science', score: 91, maxScore: 100, tier: 'Tier 1', avatarBg: '#EC4899' },
      { rank: 3, rollNo: '2024-CSE-015', name: 'Ananya Roy', branch: 'Computer Science', score: 89, maxScore: 100, tier: 'Tier 1', avatarBg: '#4F46E5' },
      { rank: 4, rollNo: '2024-CSE-001', name: 'Rahul Sharma', branch: 'Computer Science', score: 85, maxScore: 100, tier: 'Tier 1', avatarBg: '#10B981' },
      { rank: 5, rollNo: '2024-CSE-045', name: 'Neha Gupta', branch: 'Computer Science', score: 82, maxScore: 100, tier: 'Tier 1', avatarBg: '#06B6D4' },
      { rank: 6, rollNo: '2024-IT-019', name: 'Rohan Mehta', branch: 'Information Tech', score: 80, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 7, rollNo: '2024-ECE-012', name: 'Aarav Verma', branch: 'Electronics & Comm', score: 78, maxScore: 100, tier: 'Tier 1', avatarBg: '#F59E0B' },
      { rank: 8, rollNo: '2024-IT-025', name: 'Karan Malhotra', branch: 'Information Tech', score: 76, maxScore: 100, tier: 'Tier 1', avatarBg: '#8B5CF6' },
      { rank: 9, rollNo: '2024-CSE-033', name: 'Isha Patel', branch: 'Computer Science', score: 73, maxScore: 100, tier: 'Tier 1', avatarBg: '#14B8A6' },
      { rank: 10, rollNo: '2024-ME-003', name: 'Vikram Singh', branch: 'Mechanical Engg', score: 71, maxScore: 100, tier: 'Tier 1', avatarBg: '#64748B' },
      { rank: 11, rollNo: '2024-EE-010', name: 'Aditya Rao', branch: 'Electrical Engg', score: 70, maxScore: 100, tier: 'Tier 1', avatarBg: '#EF4444' },
      { rank: 12, rollNo: '2024-CSE-052', name: 'Tanvi Joshi', branch: 'Computer Science', score: 68, maxScore: 100, tier: 'Tier 2', avatarBg: '#3B82F6' },
      { rank: 13, rollNo: '2024-IT-031', name: 'Devansh Kapoor', branch: 'Information Tech', score: 67, maxScore: 100, tier: 'Tier 2', avatarBg: '#6366F1' },
      { rank: 14, rollNo: '2024-ECE-024', name: 'Siddharth Jain', branch: 'Electronics & Comm', score: 66, maxScore: 100, tier: 'Tier 2', avatarBg: '#8B5CF6' },
      { rank: 15, rollNo: '2024-CSE-068', name: 'Riya Shah', branch: 'Computer Science', score: 65, maxScore: 100, tier: 'Tier 2', avatarBg: '#EC4899' },
      { rank: 16, rollNo: '2024-IT-042', name: 'Kabir Bansal', branch: 'Information Tech', score: 64, maxScore: 100, tier: 'Tier 2', avatarBg: '#10B981' },
      { rank: 17, rollNo: '2024-ME-015', name: 'Manish Kumar', branch: 'Mechanical Engg', score: 63, maxScore: 100, tier: 'Tier 2', avatarBg: '#64748B' },
      { rank: 18, rollNo: '2024-CSE-079', name: 'Simran Arora', branch: 'Computer Science', score: 62, maxScore: 100, tier: 'Tier 2', avatarBg: '#F59E0B' },
      { rank: 19, rollNo: '2024-EE-022', name: 'Varun Nair', branch: 'Electrical Engg', score: 61, maxScore: 100, tier: 'Tier 2', avatarBg: '#0284C7' },
      { rank: 20, rollNo: '2024-ECE-037', name: 'Meera Kulkarni', branch: 'Electronics & Comm', score: 60, maxScore: 100, tier: 'Tier 2', avatarBg: '#A855F7' },
    ],
    'Test 3': [
      { rank: 1, rollNo: '2024-CSE-001', name: 'Rahul Sharma', branch: 'Computer Science', score: 95, maxScore: 100, tier: 'Tier 1', avatarBg: '#10B981' },
      { rank: 2, rollNo: '2024-IT-025', name: 'Karan Malhotra', branch: 'Information Tech', score: 93, maxScore: 100, tier: 'Tier 1', avatarBg: '#8B5CF6' },
      { rank: 3, rollNo: '2024-CSE-015', name: 'Ananya Roy', branch: 'Computer Science', score: 88, maxScore: 100, tier: 'Tier 1', avatarBg: '#4F46E5' },
      { rank: 4, rollNo: '2024-IT-008', name: 'Sandhya Kulkarni', branch: 'Information Tech', score: 86, maxScore: 100, tier: 'Tier 1', avatarBg: '#0284C7' },
      { rank: 5, rollNo: '2024-ECE-012', name: 'Aarav Verma', branch: 'Electronics & Comm', score: 84, maxScore: 100, tier: 'Tier 1', avatarBg: '#F59E0B' },
      { rank: 6, rollNo: '2024-CSE-002', name: 'Priya Desai', branch: 'Computer Science', score: 82, maxScore: 100, tier: 'Tier 1', avatarBg: '#EC4899' },
      { rank: 7, rollNo: '2024-ME-003', name: 'Vikram Singh', branch: 'Mechanical Engg', score: 80, maxScore: 100, tier: 'Tier 1', avatarBg: '#64748B' },
      { rank: 8, rollNo: '2024-CSE-045', name: 'Neha Gupta', branch: 'Computer Science', score: 78, maxScore: 100, tier: 'Tier 1', avatarBg: '#06B6D4' },
      { rank: 9, rollNo: '2024-IT-019', name: 'Rohan Mehta', branch: 'Information Tech', score: 75, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 10, rollNo: '2024-CSE-033', name: 'Isha Patel', branch: 'Computer Science', score: 72, maxScore: 100, tier: 'Tier 1', avatarBg: '#14B8A6' },
      { rank: 11, rollNo: '2024-EE-010', name: 'Aditya Rao', branch: 'Electrical Engg', score: 71, maxScore: 100, tier: 'Tier 1', avatarBg: '#EF4444' },
      { rank: 12, rollNo: '2024-CSE-052', name: 'Tanvi Joshi', branch: 'Computer Science', score: 69, maxScore: 100, tier: 'Tier 2', avatarBg: '#3B82F6' },
      { rank: 13, rollNo: '2024-IT-031', name: 'Devansh Kapoor', branch: 'Information Tech', score: 68, maxScore: 100, tier: 'Tier 2', avatarBg: '#6366F1' },
      { rank: 14, rollNo: '2024-ECE-024', name: 'Siddharth Jain', branch: 'Electronics & Comm', score: 67, maxScore: 100, tier: 'Tier 2', avatarBg: '#8B5CF6' },
      { rank: 15, rollNo: '2024-CSE-068', name: 'Riya Shah', branch: 'Computer Science', score: 66, maxScore: 100, tier: 'Tier 2', avatarBg: '#EC4899' },
      { rank: 16, rollNo: '2024-IT-042', name: 'Kabir Bansal', branch: 'Information Tech', score: 65, maxScore: 100, tier: 'Tier 2', avatarBg: '#10B981' },
      { rank: 17, rollNo: '2024-ME-015', name: 'Manish Kumar', branch: 'Mechanical Engg', score: 64, maxScore: 100, tier: 'Tier 2', avatarBg: '#64748B' },
      { rank: 18, rollNo: '2024-CSE-079', name: 'Simran Arora', branch: 'Computer Science', score: 63, maxScore: 100, tier: 'Tier 2', avatarBg: '#F59E0B' },
      { rank: 19, rollNo: '2024-EE-022', name: 'Varun Nair', branch: 'Electrical Engg', score: 62, maxScore: 100, tier: 'Tier 2', avatarBg: '#0284C7' },
      { rank: 20, rollNo: '2024-ECE-037', name: 'Meera Kulkarni', branch: 'Electronics & Comm', score: 60, maxScore: 100, tier: 'Tier 2', avatarBg: '#A855F7' },
    ],
    'Test 4': [
      { rank: 1, rollNo: '2024-CSE-002', name: 'Priya Desai', branch: 'Computer Science', score: 97, maxScore: 100, tier: 'Tier 1', avatarBg: '#EC4899' },
      { rank: 2, rollNo: '2024-CSE-015', name: 'Ananya Roy', branch: 'Computer Science', score: 94, maxScore: 100, tier: 'Tier 1', avatarBg: '#4F46E5' },
      { rank: 3, rollNo: '2024-IT-008', name: 'Sandhya Kulkarni', branch: 'Information Tech', score: 91, maxScore: 100, tier: 'Tier 1', avatarBg: '#0284C7' },
      { rank: 4, rollNo: '2024-CSE-045', name: 'Neha Gupta', branch: 'Computer Science', score: 88, maxScore: 100, tier: 'Tier 1', avatarBg: '#06B6D4' },
      { rank: 5, rollNo: '2024-CSE-001', name: 'Rahul Sharma', branch: 'Computer Science', score: 86, maxScore: 100, tier: 'Tier 1', avatarBg: '#10B981' },
      { rank: 6, rollNo: '2024-IT-019', name: 'Rohan Mehta', branch: 'Information Tech', score: 84, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 7, rollNo: '2024-IT-025', name: 'Karan Malhotra', branch: 'Information Tech', score: 81, maxScore: 100, tier: 'Tier 1', avatarBg: '#8B5CF6' },
      { rank: 8, rollNo: '2024-ECE-012', name: 'Aarav Verma', branch: 'Electronics & Comm', score: 79, maxScore: 100, tier: 'Tier 1', avatarBg: '#F59E0B' },
      { rank: 9, rollNo: '2024-CSE-033', name: 'Isha Patel', branch: 'Computer Science', score: 77, maxScore: 100, tier: 'Tier 1', avatarBg: '#14B8A6' },
      { rank: 10, rollNo: '2024-ME-003', name: 'Vikram Singh', branch: 'Mechanical Engg', score: 75, maxScore: 100, tier: 'Tier 1', avatarBg: '#64748B' },
      { rank: 11, rollNo: '2024-EE-010', name: 'Aditya Rao', branch: 'Electrical Engg', score: 73, maxScore: 100, tier: 'Tier 1', avatarBg: '#EF4444' },
      { rank: 12, rollNo: '2024-CSE-052', name: 'Tanvi Joshi', branch: 'Computer Science', score: 70, maxScore: 100, tier: 'Tier 1', avatarBg: '#3B82F6' },
      { rank: 13, rollNo: '2024-IT-031', name: 'Devansh Kapoor', branch: 'Information Tech', score: 68, maxScore: 100, tier: 'Tier 2', avatarBg: '#6366F1' },
      { rank: 14, rollNo: '2024-ECE-024', name: 'Siddharth Jain', branch: 'Electronics & Comm', score: 67, maxScore: 100, tier: 'Tier 2', avatarBg: '#8B5CF6' },
      { rank: 15, rollNo: '2024-CSE-068', name: 'Riya Shah', branch: 'Computer Science', score: 66, maxScore: 100, tier: 'Tier 2', avatarBg: '#EC4899' },
      { rank: 16, rollNo: '2024-IT-042', name: 'Kabir Bansal', branch: 'Information Tech', score: 65, maxScore: 100, tier: 'Tier 2', avatarBg: '#10B981' },
      { rank: 17, rollNo: '2024-ME-015', name: 'Manish Kumar', branch: 'Mechanical Engg', score: 64, maxScore: 100, tier: 'Tier 2', avatarBg: '#64748B' },
      { rank: 18, rollNo: '2024-CSE-079', name: 'Simran Arora', branch: 'Computer Science', score: 63, maxScore: 100, tier: 'Tier 2', avatarBg: '#F59E0B' },
      { rank: 19, rollNo: '2024-EE-022', name: 'Varun Nair', branch: 'Electrical Engg', score: 61, maxScore: 100, tier: 'Tier 2', avatarBg: '#0284C7' },
      { rank: 20, rollNo: '2024-ECE-037', name: 'Meera Kulkarni', branch: 'Electronics & Comm', score: 60, maxScore: 100, tier: 'Tier 2', avatarBg: '#A855F7' },
    ],
  };

  get currentTopPerformers(): CampusPerformer[] {
    const list = this.performerDataMap[this.selectedPerformerFilter] || this.performerDataMap['Overall'];
    if (!this.searchQuery.trim()) return list;
    const q = this.searchQuery.toLowerCase();
    return list.filter(p => p.name.toLowerCase().includes(q) || p.rollNo.toLowerCase().includes(q) || p.branch.toLowerCase().includes(q));
  }

  getInitials(name: string): string {
    if (!name) return '';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

  studentReadinessOptions: Highcharts.Options = {};
  studentReadinessList: ReadinessItem[] = [
    { label: 'Highly Ready', count: 210, percentage: 37, color: '#10B981' },
    { label: 'Ready', count: 196, percentage: 34, color: '#06B6D4' },
    { label: 'In Progress', count: 132, percentage: 23, color: '#8B5CF6' },
    { label: 'Needs Attention', count: 30, percentage: 6, color: '#F97316' },
  ];

  // 7. Placement Funnel
  placementFunnel: FunnelStage[] = [
    { stage: 'Registered', count: 3248, formattedCount: '3,248', percentage: 100 },
    { stage: 'Eligible', count: 2156, formattedCount: '2,156', percentage: 66 },
    { stage: 'Shortlisted', count: 1024, formattedCount: '1,024', percentage: 31.5 },
    { stage: 'Interviewed', count: 614, formattedCount: '614', percentage: 18.9 },
    { stage: 'Offers Made', count: 426, formattedCount: '426', percentage: 13.1 },
  ];

  // 8. Readiness Assessment Conducted Row Data & Donut
  assessmentReadinessOptions: Highcharts.Options = {};
  assessmentReadinessList: ReadinessItem[] = [
    { label: 'Tier 1 (High Score ≥ 70%)', count: 450, percentage: 34.5, color: '#10B981' },
    { label: 'Tier 2 (Moderate 50-69%)', count: 593, percentage: 45.5, color: '#3B82F6' },
    { label: 'Tier 3 (Remedial < 50%)', count: 260, percentage: 20.0, color: '#F97316' },
  ];

  readinessDataMap: Record<string, {
    totalEvaluated: string;
    avgScore: string;
    completionRate: string;
    tiers: ReadinessItem[];
  }> = {
      'Overall': {
        totalEvaluated: '1,303',
        avgScore: '68.4',
        completionRate: '94.2',
        tiers: [
          { label: 'Tier 1 (High Score ≥ 70%)', count: 450, percentage: 34.5, color: '#10B981' },
          { label: 'Tier 2 (Moderate 50-69%)', count: 593, percentage: 45.5, color: '#3B82F6' },
          { label: 'Tier 3 (Remedial < 50%)', count: 260, percentage: 20.0, color: '#F97316' },
        ],
      },
      'Test 1': {
        totalEvaluated: '350',
        avgScore: '72.0',
        completionRate: '96.0',
        tiers: [
          { label: 'Tier 1 (High Score ≥ 70%)', count: 140, percentage: 40.0, color: '#10B981' },
          { label: 'Tier 2 (Moderate 50-69%)', count: 152, percentage: 43.4, color: '#3B82F6' },
          { label: 'Tier 3 (Remedial < 50%)', count: 58, percentage: 16.6, color: '#F97316' },
        ],
      },
      'Test 2': {
        totalEvaluated: '298',
        avgScore: '65.0',
        completionRate: '92.5',
        tiers: [
          { label: 'Tier 1 (High Score ≥ 70%)', count: 89, percentage: 29.9, color: '#10B981' },
          { label: 'Tier 2 (Moderate 50-69%)', count: 132, percentage: 44.3, color: '#3B82F6' },
          { label: 'Tier 3 (Remedial < 50%)', count: 77, percentage: 25.8, color: '#F97316' },
        ],
      },
      'Test 3': {
        totalEvaluated: '315',
        avgScore: '68.0',
        completionRate: '94.0',
        tiers: [
          { label: 'Tier 1 (High Score ≥ 70%)', count: 104, percentage: 33.0, color: '#10B981' },
          { label: 'Tier 2 (Moderate 50-69%)', count: 148, percentage: 47.0, color: '#3B82F6' },
          { label: 'Tier 3 (Remedial < 50%)', count: 63, percentage: 20.0, color: '#F97316' },
        ],
      },
      'Test 4': {
        totalEvaluated: '340',
        avgScore: '76.0',
        completionRate: '95.5',
        tiers: [
          { label: 'Tier 1 (High Score ≥ 70%)', count: 156, percentage: 45.9, color: '#10B981' },
          { label: 'Tier 2 (Moderate 50-69%)', count: 141, percentage: 41.5, color: '#3B82F6' },
          { label: 'Tier 3 (Remedial < 50%)', count: 43, percentage: 12.6, color: '#F97316' },
        ],
      },
    };

  get currentReadinessStats() {
    return this.readinessDataMap[this.selectedAssessmentFilter] || this.readinessDataMap['Overall'];
  }

  onReadinessTierFilterChange(): void {
    const stats = this.currentReadinessStats;
    this.assessmentReadinessList = stats.tiers;
    this.assessmentReadinessOptions = {
      ...this.assessmentReadinessOptions,
      series: [
        {
          name: 'Submissions',
          type: 'pie',
          data: stats.tiers.map((t) => ({
            name: t.label,
            y: t.count,
            color: t.color,
          })),
        },
      ],
    };
  }

  assessmentReports: AssessmentReportItem[] = [
    {
      title: 'Grand Proctored Readiness Assessment 2024',
      attempted: 350,
      date: '02 Oct 2024',
      avgScore: 72,
      topSkill: 'Data Structures & Algorithms',
      remedialCount: 18,
      statusBadge: 'Completed',
      badgeClass: 'bg-success-subtle text-success border-success-subtle',
    },
    {
      title: 'Technical Coding & System Architecture Evaluation',
      attempted: 298,
      date: '24 Sep 2024',
      avgScore: 65,
      topSkill: 'Object Oriented Programming',
      remedialCount: 32,
      statusBadge: 'Evaluated',
      badgeClass: 'bg-primary-subtle text-primary border-primary-subtle',
    },
    {
      title: 'Quantitative Aptitude & Logical Reasoning Diagnostic',
      attempted: 315,
      date: '15 Sep 2024',
      avgScore: 68,
      topSkill: 'Logical Deduction & Speed Math',
      remedialCount: 24,
      statusBadge: 'Evaluated',
      badgeClass: 'bg-info-subtle text-info border-info-subtle',
    },
    {
      title: 'Verbal Communication & Professional Business Writing',
      attempted: 340,
      date: '08 Sep 2024',
      avgScore: 76,
      topSkill: 'Business Writing & Grammar',
      remedialCount: 12,
      statusBadge: 'Evaluated',
      badgeClass: 'bg-purple-subtle text-purple border-purple-subtle',
    },
  ];

  // 9. Placement Policy Compliance
  policyCompliance: PolicyComplianceItem[] = [
    {
      title: 'Policy Documents',
      count: '12',
      sub: 'Uploaded',
      icon: 'bi-file-earmark-text',
      iconBg: '#EEF2FF',
      iconColor: '#3B82F6',
    },
    {
      title: 'Drives Compliant',
      count: '58',
      sub: '90.6%',
      icon: 'bi-check-circle-fill',
      iconBg: '#ECFDF5',
      iconColor: '#10B981',
    },
    {
      title: 'Audit Logs',
      count: '24',
      sub: 'This Month',
      icon: 'bi-journal-text',
      iconBg: '#F3E8FF',
      iconColor: '#8B5CF6',
    },
    {
      title: 'Pending Approvals',
      count: '6',
      sub: 'Requires Action',
      icon: 'bi-person-exclamation',
      iconBg: '#FFEDD5',
      iconColor: '#F97316',
    },
  ];

  // 10. Drive Calendar
  driveCalendar: DriveCalendarEvent[] = [
    {
      day: '25',
      month: 'MAY',
      company: 'TCS',
      role: 'Digital Engineer',
      status: 'Ongoing',
      statusClass: 'status-pill-ongoing',
      dateBg: '#3730A3',
    },
    {
      day: '28',
      month: 'MAY',
      company: 'Infosys',
      role: 'System Engineer',
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming',
      dateBg: '#10B981',
    },
    {
      day: '02',
      month: 'JUN',
      company: 'Amazon',
      role: 'SDE Intern',
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming-purple',
      dateBg: '#7C3AED',
    },
    {
      day: '05',
      month: 'JUN',
      company: 'Deloitte',
      role: 'Business Analyst',
      status: 'Upcoming',
      statusClass: 'status-pill-upcoming-orange',
      dateBg: '#F97316',
    },
  ];

  // 5. Session Readiness Tests Participation Bar Chart with Rich Tooltip
  sessionTestsChartOptions: Highcharts.Options = {};

  ngOnInit(): void {
    this.initCharts();
  }

  private initCharts(): void {
    // 1. Placement Overview Combined Bar + Line Chart
    this.placementOverviewOptions = {
      chart: {
        type: 'column',
        height: 250,
        backgroundColor: 'transparent',
        style: { fontFamily: 'Inter, system-ui, sans-serif' },
        spacing: [10, 5, 10, 5],
      },
      title: { text: undefined },
      credits: { enabled: false },
      xAxis: {
        categories: ['Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May'],
        crosshair: true,
        labels: { style: { color: '#64748B', fontSize: '10.5px' } },
        lineColor: '#E2E8F0',
        tickColor: '#E2E8F0',
      },
      yAxis: [
        {
          title: { text: undefined },
          labels: { style: { color: '#64748B', fontSize: '10px' } },
          gridLineColor: '#F1F5F9',
          min: 0,
          max: 500,
          tickInterval: 100,
        },
        {
          title: { text: undefined },
          labels: {
            format: '{value}%',
            style: { color: '#64748B', fontSize: '10px' },
          },
          gridLineWidth: 0,
          opposite: true,
          min: 0,
          max: 100,
          tickInterval: 25,
        },
      ],
      tooltip: {
        shared: true,
        backgroundColor: '#0F172A',
        borderColor: '#0F172A',
        borderRadius: 8,
        style: { color: '#FFFFFF', fontSize: '11px' },
      },
      legend: { enabled: false },
      plotOptions: {
        column: {
          borderRadius: 3,
          groupPadding: 0.18,
          pointPadding: 0.04,
          borderWidth: 0,
        },
      },
      series: [
        {
          name: 'Offers Made',
          type: 'column',
          data: [150, 175, 160, 205, 215, 195, 235, 270, 225, 265, 305, 370],
          color: '#3B82F6',
          yAxis: 0,
        },
        {
          name: 'Students Placed',
          type: 'column',
          data: [105, 125, 110, 145, 160, 135, 165, 195, 155, 185, 225, 265],
          color: '#10B981',
          yAxis: 0,
        },
        {
          name: 'Placement Rate (%)',
          type: 'spline',
          data: [46, 52, 50, 60, 67, 63, 68, 74, 79, 75, 84, 89],
          color: '#8B5CF6',
          yAxis: 1,
          lineWidth: 2.5,
          marker: {
            enabled: true,
            radius: 3.5,
            fillColor: '#8B5CF6',
          },
        },
      ],
    };

    // 2. Drive Status Donut Chart (Title rendered via CSS Absolute overlay)
    this.driveStatusOptions = {
      chart: {
        type: 'pie',
        height: 220,
        backgroundColor: 'transparent',
        style: { fontFamily: 'Inter, system-ui, sans-serif' },
        spacing: [0, 0, 0, 0],
      },
      title: { text: undefined },
      credits: { enabled: false },
      tooltip: {
        pointFormat: '<b>{point.name}</b>: {point.y} ({point.percentage:.1f}%)',
        backgroundColor: '#0F172A',
        borderRadius: 8,
        style: { color: '#FFFFFF', fontSize: '11px' },
      },
      plotOptions: {
        pie: {
          innerSize: '72%',
          borderWidth: 0,
          dataLabels: { enabled: false },
        },
      },
      legend: { enabled: false },
      series: [
        {
          name: 'Drives',
          type: 'pie',
          data: [
            { name: 'Upcoming', y: 18, color: '#3B82F6' },
            { name: 'Ongoing', y: 22, color: '#10B981' },
            { name: 'Completed', y: 20, color: '#8B5CF6' },
            { name: 'Cancelled', y: 4, color: '#F97316' },
          ],
        },
      ],
    };

    // 3. Student Readiness Donut Chart (Title rendered via CSS Absolute overlay)
    this.studentReadinessOptions = {
      chart: {
        type: 'pie',
        height: 190,
        backgroundColor: 'transparent',
        style: { fontFamily: 'Inter, system-ui, sans-serif' },
        spacing: [0, 0, 0, 0],
      },
      title: { text: undefined },
      credits: { enabled: false },
      tooltip: {
        pointFormat: '<b>{point.name}</b>: {point.y} ({point.percentage:.1f}%)',
        backgroundColor: '#0F172A',
        borderRadius: 8,
        style: { color: '#FFFFFF', fontSize: '11px' },
      },
      plotOptions: {
        pie: {
          innerSize: '72%',
          borderWidth: 0,
          dataLabels: { enabled: false },
        },
      },
      legend: { enabled: false },
      series: [
        {
          name: 'Students',
          type: 'pie',
          data: [
            { name: 'Highly Ready', y: 210, color: '#10B981' },
            { name: 'Ready', y: 196, color: '#06B6D4' },
            { name: 'In Progress', y: 132, color: '#8B5CF6' },
            { name: 'Needs Attention', y: 30, color: '#F97316' },
          ],
        },
      ],
    };

    // 4. Readiness Assessment Donut Chart
    this.assessmentReadinessOptions = {
      chart: {
        type: 'pie',
        height: 220,
        backgroundColor: 'transparent',
        style: { fontFamily: 'Inter, system-ui, sans-serif' },
        spacing: [0, 0, 0, 0],
      },
      title: { text: undefined },
      credits: { enabled: false },
      tooltip: {
        pointFormat: '<b>{point.name}</b>: {point.y} ({point.percentage:.1f}%)',
        backgroundColor: '#0F172A',
        borderRadius: 8,
        style: { color: '#FFFFFF', fontSize: '11px' },
      },
      plotOptions: {
        pie: {
          innerSize: '72%',
          borderWidth: 0,
          dataLabels: { enabled: false },
        },
      },
      legend: { enabled: false },
      series: [
        {
          name: 'Submissions',
          type: 'pie',
          data: [
            { name: 'Tier 1 (High)', y: 450, color: '#10B981' },
            { name: 'Tier 2 (Moderate)', y: 593, color: '#3B82F6' },
            { name: 'Tier 3 (Remedial)', y: 260, color: '#F97316' },
          ],
        },
      ],
    };

    // 5. Session Readiness Tests Bar Chart Options with Rich Custom Tooltip
    this.sessionTestsChartOptions = {
      chart: {
        type: 'column',
        height: 235,
        backgroundColor: 'transparent',
        style: { fontFamily: 'Inter, system-ui, sans-serif' },
        spacing: [10, 5, 5, 5],
      },
      title: { text: undefined },
      credits: { enabled: false },
      xAxis: {
        categories: ['Test 1', 'Test 2', 'Test 3', 'Test 4'],
        labels: { style: { color: '#64748B', fontSize: '11px', fontWeight: '600' } },
        lineColor: '#E2E8F0',
        tickColor: '#E2E8F0',
      },
      yAxis: {
        title: { text: undefined },
        labels: { style: { color: '#64748B', fontSize: '10px' } },
        gridLineColor: '#F1F5F9',
        min: 0,
        max: 400,
        tickInterval: 100,
      },
      tooltip: {
        shared: true,
        useHTML: true,
        backgroundColor: '#0F172A',
        borderColor: '#0F172A',
        borderRadius: 8,
        shadow: true,
        style: { color: '#FFFFFF', fontSize: '11px' },
        formatter: function () {
          const points = this.points || [];
          const testName = String(this.x);
          const appeared = points[0] ? Number(points[0].y) : 0;
          const absent = points[1] ? Number(points[1].y) : 0;
          const total = appeared + absent;
          const attendancePct = Math.round((appeared / (total || 1)) * 100);

          const detailsMap: Record<string, { fullTitle: string; avgScore: number; topSkill: string; remedial: number }> = {
            'Test 1': { fullTitle: 'Grand Proctored Readiness Assessment 2024', avgScore: 72, topSkill: 'Data Structures & Algorithms', remedial: 18 },
            'Test 2': { fullTitle: 'Technical Coding & System Architecture Evaluation', avgScore: 65, topSkill: 'Object Oriented Programming', remedial: 32 },
            'Test 3': { fullTitle: 'Quantitative Aptitude & Logical Reasoning Diagnostic', avgScore: 68, topSkill: 'Logical Deduction & Speed Math', remedial: 24 },
            'Test 4': { fullTitle: 'Verbal Communication & Professional Business Writing', avgScore: 76, topSkill: 'Business Writing & Grammar', remedial: 12 },
          };
          const info = detailsMap[testName] || { fullTitle: testName, avgScore: 70, topSkill: 'Core Competency', remedial: 20 };

          return `
            <div style="padding: 6px 8px; font-family: Inter, system-ui, sans-serif;">
              <div style="font-weight: 700; color: #60A5FA; font-size: 11.5px; margin-bottom: 4px; border-bottom: 1px solid #334155; padding-bottom: 4px;">
                📊 ${testName}: ${info.fullTitle}
              </div>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; margin-bottom: 6px; font-size: 11px;">
                <div>Appeared: <b style="color: #34D399;">${appeared} Candidates</b></div>
                <div>Absent: <b style="color: #F87171;">${absent} Candidates</b></div>
                <div>Attendance: <b style="color: #FBBF24;">${attendancePct}% Rate</b></div>
                <div>Avg Score: <b style="color: #60A5FA;">${info.avgScore} / 100</b></div>
              </div>
              <div style="border-top: 1px solid #334155; padding-top: 4px; font-size: 10.5px; color: #CBD5E1;">
                Top Skill: <b style="color: #FFFFFF;">${info.topSkill}</b><br/>
                Remedial Flagged: <b style="color: #F87171;">${info.remedial} Students</b>
              </div>
            </div>
          `;
        },
      },
      legend: {
        align: 'right',
        verticalAlign: 'top',
        y: -10,
        itemStyle: { color: '#64748B', fontSize: '10.5px', fontWeight: '500' },
      },
      plotOptions: {
        column: {
          borderRadius: 3,
          groupPadding: 0.18,
          pointPadding: 0.05,
          borderWidth: 0,
        },
      },
      series: [
        {
          name: 'Appeared',
          type: 'column',
          data: [350, 298, 315, 340],
          color: '#3B82F6',
        },
        {
          name: 'Absent',
          type: 'column',
          data: [30, 82, 65, 40],
          color: '#F87171',
        },
      ],
    };

  }
}



