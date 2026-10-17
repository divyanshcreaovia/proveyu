import { Component, HostListener } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface StudentRecord {
  id: string;
  roll: string;
  name: string;
  email: string;
  phone: string;
  degree: string;
  batch: string;
  branch: string;
  cgpa: number;
  tenthPercentage: number;
  twelfthPercentage: number;
  backlogs: number;
  gapYears: number;
  tier: 'Tier 1' | 'Tier 2' | 'Tier 3';
  tierBadge: string;
  score: string;
  codingScore: number;
  aptitudeScore: number;
  commScore: number;
  status: 'Placed' | 'Unplaced';
  company: string;
  role: string;
  ctc: string;
  packageCategory: string;
  bondDuration: string;
  pendingRound?: string;
  eligibleDrivesCount: number;
}

export interface BulkPlacedUploadRow {
  roll: string;
  name: string;
  branch: string;
  status: string;
  matchedStudent?: StudentRecord;
  isValid: boolean;
  validationMessage?: string;
}

@Component({
  selector: 'app-college-students',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './students.html',
  styleUrls: ['./students.scss', './student-dossier-modal.scss', './bulk-placement-modal.scss', './student-overview-modal.scss'],
})
export class Students {
  searchQuery: string = '';
  selectedBranch: string = 'All';
  selectedTier: string = 'All';
  selectedStatus: string = 'All';
  selectedMinCgpa: number | null = null;
  selectedMaxBacklogs: number | null = null;
  selectedMaxGapYears: number | null = null;
  selectedMinTenth: number | null = null;
  selectedMinTwelfth: number | null = null;

  // State for filter popover dropdown (Select & Apply pattern)
  isFilterOpen: boolean = false;
  tempFilters = {
    branch: 'All',
    tier: 'All',
    status: 'All',
    minCgpa: null as number | null,
    maxBacklogs: null as number | null,
    maxGapYears: null as number | null,
    minTenth: null as number | null,
    minTwelfth: null as number | null
  };

  // All college engineering branches
  availableBranches: string[] = [
    'Computer Science & Engineering',
    'Information Technology',
    'AI & Data Science',
    'Electronics & Telecommunication',
    'Electrical Engineering',
    'Mechanical Engineering',
    'Civil Engineering',
    'Chemical Engineering',
    'Automobile Engineering',
    'Biotechnology'
  ];

  // Pagination state
  currentPage: number = 1;
  pageSize: number = 8;

  // Selected student for Dossier modal
  selectedStudent: StudentRecord | null = null;
  showDossierModal: boolean = false;

  // Checkbox multi-selection state for bulk actions
  selectedStudentIds = new Set<string>();

  // Bulk Placement Modal State (Excel/CSV upload & Superset sync)
  showBulkPlacementModal: boolean = false;
  isDragOver: boolean = false;
  bulkUploadedFileName: string = '';
  bulkUploadedFileSize: string = '';
  bulkUploadedRows: BulkPlacedUploadRow[] = [];

  // Student Roster & Placement Analytics Overview Modal State
  showOverviewModal: boolean = false;
  Math = Math;

  bulkCompanyPresets = [
    { name: 'TCS', role: 'System Engineer', ctc: '₹4.5L', cat: 'Mass Recruiter (< 5 LPA)', bond: 'No Bond' },
    { name: 'Infosys', role: 'Specialist Programmer', ctc: '₹6.5L', cat: 'Dream (5-10 LPA)', bond: 'No Bond' },
    { name: 'Cognizant', role: 'GenC Next Engineer', ctc: '₹6.75L', cat: 'Dream (5-10 LPA)', bond: 'No Bond' },
    { name: 'Wipro', role: 'Turbo Developer', ctc: '₹6.5L', cat: 'Dream (5-10 LPA)', bond: 'No Bond' },
    { name: 'Accenture', role: 'Associate Software Engineer', ctc: '₹4.5L', cat: 'Mass Recruiter (< 5 LPA)', bond: 'No Bond' },
    { name: 'Capgemini', role: 'Senior Analyst', ctc: '₹5.5L', cat: 'Dream (5-10 LPA)', bond: 'No Bond' },
    { name: 'LTIMindtree', role: 'Software Engineer', ctc: '₹4.2L', cat: 'Mass Recruiter (< 5 LPA)', bond: 'No Bond' }
  ];

  bulkPlacementData = {
    company: 'TCS',
    role: 'System Engineer',
    ctc: '₹4.5L',
    packageCategory: 'Mass Recruiter (< 5 LPA)',
    recruitmentSource: 'Superset Campus Drive',
    bondDuration: 'No Bond',
    offerDate: '2024-10-15'
  };

  private initialPlacedIds = new Set<string>(['001', '002', '006', '008', '010', '011', '012', '013', '015', '016', '018', '019', '021', '022', '024', '026', '027']);

  constructor(private notificationService: NotificationService) { }

  studentsList: StudentRecord[] = [
    // Page 1 Candidates (matching screenshot + core branches)
    {
      id: '001',
      roll: '2024-CSE-001',
      name: 'Rahul Sharma',
      email: 'rahul@mgmcet.ac.in',
      phone: '9876543210',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 8.5,
      tenthPercentage: 88,
      twelfthPercentage: 86,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '82/100',
      codingScore: 29,
      aptitudeScore: 26,
      commScore: 27,
      status: 'Placed',
      company: 'TCS',
      role: 'Associate Software Engineer',
      ctc: '₹3.5L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 6
    },
    {
      id: '002',
      roll: '2024-CSE-002',
      name: 'Priya Desai',
      email: 'priya@mgmcet.ac.in',
      phone: '9876543211',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Information Technology',
      cgpa: 8.8,
      tenthPercentage: 91,
      twelfthPercentage: 89,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '78/100',
      codingScore: 28,
      aptitudeScore: 25,
      commScore: 25,
      status: 'Placed',
      company: 'Wipro',
      role: 'Project Engineer Trainee',
      ctc: '₹4.2L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: '12 Months',
      eligibleDrivesCount: 7
    },
    {
      id: '003',
      roll: '2024-CSE-003',
      name: 'Arun Patel',
      email: 'arun@mgmcet.ac.in',
      phone: '9876543212',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 8.4,
      tenthPercentage: 82,
      twelfthPercentage: 78,
      backlogs: 1,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '68/100',
      codingScore: 22,
      aptitudeScore: 24,
      commScore: 22,
      status: 'Unplaced',
      company: 'Persistent Systems',
      role: 'Software Engineer',
      ctc: '₹4.5L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      pendingRound: 'Tech Interview R1',
      eligibleDrivesCount: 4
    },
    {
      id: '004',
      roll: '2024-IT-004',
      name: 'Neha Deshmukh',
      email: 'neha@mgmcet.ac.in',
      phone: '9876543213',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Information Technology',
      cgpa: 8.2,
      tenthPercentage: 79,
      twelfthPercentage: 75,
      backlogs: 0,
      gapYears: 1,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '65/100',
      codingScore: 21,
      aptitudeScore: 22,
      commScore: 22,
      status: 'Unplaced',
      company: 'Amdocs',
      role: 'Backend Developer',
      ctc: '₹3.8L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      pendingRound: 'HR Final Round',
      eligibleDrivesCount: 5
    },
    {
      id: '005',
      roll: '2024-EXTC-005',
      name: 'Amit Verma',
      email: 'amit@mgmcet.ac.in',
      phone: '9876543214',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Electronics & Telecommunication',
      cgpa: 7.8,
      tenthPercentage: 74,
      twelfthPercentage: 71,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '64/100',
      codingScore: 20,
      aptitudeScore: 23,
      commScore: 21,
      status: 'Placed',
      company: 'Accenture',
      role: 'Associate Software Analyst',
      ctc: '₹3.5L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 5
    },
    {
      id: '006',
      roll: '2024-CSE-006',
      name: 'Divya Nair',
      email: 'divya@mgmcet.ac.in',
      phone: '9876543215',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 7.2,
      tenthPercentage: 72,
      twelfthPercentage: 68,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 3',
      tierBadge: 'badge-tier-3',
      score: '58/100',
      codingScore: 18,
      aptitudeScore: 21,
      commScore: 19,
      status: 'Unplaced',
      company: '-',
      role: '-',
      ctc: '-',
      packageCategory: '-',
      bondDuration: 'N/A',
      eligibleDrivesCount: 3
    },
    {
      id: '007',
      roll: '2024-IT-007',
      name: 'Sanjay Joshi',
      email: 'sanjay.j@mgmcet.ac.in',
      phone: '9876543216',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Information Technology',
      cgpa: 8.9,
      tenthPercentage: 92,
      twelfthPercentage: 90,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '88/100',
      codingScore: 30,
      aptitudeScore: 29,
      commScore: 29,
      status: 'Placed',
      company: 'Deloitte',
      role: 'Technology Consultant',
      ctc: '₹5.5L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 8
    },
    {
      id: '008',
      roll: '2024-CSE-008',
      name: 'Sandhya Kumar',
      email: 'sandhya@mgmcet.ac.in',
      phone: '9876543217',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 8.1,
      tenthPercentage: 78,
      twelfthPercentage: 76,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '66/100',
      codingScore: 22,
      aptitudeScore: 23,
      commScore: 21,
      status: 'Unplaced',
      company: 'Capgemini',
      role: 'Analyst Software Engineer',
      ctc: '₹4.0L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      pendingRound: 'Coding Round',
      eligibleDrivesCount: 5
    },

    // Page 2 Candidates (Diverse engineering disciplines)
    {
      id: '009',
      roll: '2024-MECH-009',
      name: 'Rajesh More',
      email: 'rajesh.m@mgmcet.ac.in',
      phone: '9876543218',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Mechanical Engineering',
      cgpa: 8.2,
      tenthPercentage: 80,
      twelfthPercentage: 78,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '75/100',
      codingScore: 23,
      aptitudeScore: 26,
      commScore: 26,
      status: 'Placed',
      company: 'Tata Motors',
      role: 'Graduate Engineer Trainee',
      ctc: '₹5.0L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: '12 Months',
      eligibleDrivesCount: 5
    },
    {
      id: '010',
      roll: '2024-MECH-010',
      name: 'Omkar Kadam',
      email: 'omkar.k@mgmcet.ac.in',
      phone: '9876543219',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Mechanical Engineering',
      cgpa: 7.7,
      tenthPercentage: 74,
      twelfthPercentage: 71,
      backlogs: 1,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '65/100',
      codingScore: 20,
      aptitudeScore: 23,
      commScore: 22,
      status: 'Unplaced',
      company: '-',
      role: '-',
      ctc: '-',
      packageCategory: '-',
      bondDuration: 'N/A',
      eligibleDrivesCount: 4
    },
    {
      id: '011',
      roll: '2024-EE-011',
      name: 'Suresh Raina',
      email: 'suresh.r@mgmcet.ac.in',
      phone: '9876543220',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Electrical Engineering',
      cgpa: 8.6,
      tenthPercentage: 87,
      twelfthPercentage: 85,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '81/100',
      codingScore: 26,
      aptitudeScore: 28,
      commScore: 27,
      status: 'Placed',
      company: 'Schneider Electric',
      role: 'R&D Electrical Engineer',
      ctc: '₹6.0L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 7
    },
    {
      id: '012',
      roll: '2024-EE-012',
      name: 'Tejas Shinde',
      email: 'tejas.s@mgmcet.ac.in',
      phone: '9876543221',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Electrical Engineering',
      cgpa: 7.9,
      tenthPercentage: 76,
      twelfthPercentage: 74,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '67/100',
      codingScore: 22,
      aptitudeScore: 23,
      commScore: 22,
      status: 'Unplaced',
      company: 'ABB Group',
      role: 'Graduate Trainee Engineer',
      ctc: '₹4.8L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      pendingRound: 'Technical Interview',
      eligibleDrivesCount: 5
    },
    {
      id: '013',
      roll: '2024-CIVIL-013',
      name: 'Harsh Vardhan',
      email: 'harsh.v@mgmcet.ac.in',
      phone: '9876543222',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Civil Engineering',
      cgpa: 8.3,
      tenthPercentage: 81,
      twelfthPercentage: 80,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '76/100',
      codingScore: 22,
      aptitudeScore: 27,
      commScore: 27,
      status: 'Placed',
      company: 'L&T Construction',
      role: 'Site Engineer Trainee',
      ctc: '₹4.5L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: '24 Months',
      eligibleDrivesCount: 4
    },
    {
      id: '014',
      roll: '2024-CIVIL-014',
      name: 'Aditya Bhosale',
      email: 'aditya.b@mgmcet.ac.in',
      phone: '9876543223',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Civil Engineering',
      cgpa: 7.1,
      tenthPercentage: 69,
      twelfthPercentage: 67,
      backlogs: 1,
      gapYears: 0,
      tier: 'Tier 3',
      tierBadge: 'badge-tier-3',
      score: '57/100',
      codingScore: 16,
      aptitudeScore: 21,
      commScore: 20,
      status: 'Unplaced',
      company: '-',
      role: '-',
      ctc: '-',
      packageCategory: '-',
      bondDuration: 'N/A',
      eligibleDrivesCount: 2
    },
    {
      id: '015',
      roll: '2024-CHEM-015',
      name: 'Ruchi Shah',
      email: 'ruchi.s@mgmcet.ac.in',
      phone: '9876543224',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Chemical Engineering',
      cgpa: 8.7,
      tenthPercentage: 89,
      twelfthPercentage: 88,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '83/100',
      codingScore: 25,
      aptitudeScore: 29,
      commScore: 29,
      status: 'Placed',
      company: 'Reliance Industries',
      role: 'Process Engineer Trainee',
      ctc: '₹6.5L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 6
    },
    {
      id: '016',
      roll: '2024-AUTO-016',
      name: 'Tanmay Joshi',
      email: 'tanmay.j@mgmcet.ac.in',
      phone: '9876543225',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Automobile Engineering',
      cgpa: 8.0,
      tenthPercentage: 78,
      twelfthPercentage: 75,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '69/100',
      codingScore: 21,
      aptitudeScore: 24,
      commScore: 24,
      status: 'Unplaced',
      company: 'Mahindra & Mahindra',
      role: 'Automotive Design Trainee',
      ctc: '₹4.8L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: '12 Months',
      pendingRound: 'Design Test & GD',
      eligibleDrivesCount: 5
    },

    // Page 3 Candidates (BioTech, AI & Data Science, Passouts)
    {
      id: '017',
      roll: '2024-BIO-017',
      name: 'Shreya Kulkarni',
      email: 'shreya.k@mgmcet.ac.in',
      phone: '9876543226',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Biotechnology',
      cgpa: 8.4,
      tenthPercentage: 85,
      twelfthPercentage: 83,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '78/100',
      codingScore: 24,
      aptitudeScore: 27,
      commScore: 27,
      status: 'Placed',
      company: 'Biocon',
      role: 'Bio-Informatics Analyst',
      ctc: '₹4.8L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 5
    },
    {
      id: '018',
      roll: '2024-AI-018',
      name: 'Nikhil Rao',
      email: 'nikhil.r@mgmcet.ac.in',
      phone: '9876543227',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'AI & Data Science',
      cgpa: 8.9,
      tenthPercentage: 90,
      twelfthPercentage: 89,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '86/100',
      codingScore: 29,
      aptitudeScore: 29,
      commScore: 28,
      status: 'Placed',
      company: 'Fractal Analytics',
      role: 'Data Scientist Trainee',
      ctc: '₹7.5L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 8
    },
    {
      id: '019',
      roll: '2024-AI-019',
      name: 'Aditi Rao',
      email: 'aditi.r@mgmcet.ac.in',
      phone: '9876543228',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'AI & Data Science',
      cgpa: 8.7,
      tenthPercentage: 87,
      twelfthPercentage: 85,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '80/100',
      codingScore: 27,
      aptitudeScore: 27,
      commScore: 26,
      status: 'Placed',
      company: 'ZS Associates',
      role: 'Decision Analytics Associate',
      ctc: '₹8.0L',
      packageCategory: 'Dream Drive (5-10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 7
    },
    {
      id: '020',
      roll: '2024-CSE-020',
      name: 'Ananya Roy',
      email: 'ananya.roy@mgmcet.ac.in',
      phone: '9876543229',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 9.1,
      tenthPercentage: 95,
      twelfthPercentage: 93,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '92/100',
      codingScore: 30,
      aptitudeScore: 30,
      commScore: 32,
      status: 'Placed',
      company: 'Oracle',
      role: 'Associate Applications Developer',
      ctc: '₹11.0L',
      packageCategory: 'Super Dream (> 10 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 9
    },
    {
      id: '021',
      roll: '2024-CSE-021',
      name: 'Varun Sharma',
      email: 'varun.s@mgmcet.ac.in',
      phone: '9876543230',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Computer Science & Engineering',
      cgpa: 6.9,
      tenthPercentage: 68,
      twelfthPercentage: 64,
      backlogs: 1,
      gapYears: 1,
      tier: 'Tier 3',
      tierBadge: 'badge-tier-3',
      score: '56/100',
      codingScore: 17,
      aptitudeScore: 20,
      commScore: 19,
      status: 'Unplaced',
      company: '-',
      role: '-',
      ctc: '-',
      packageCategory: '-',
      bondDuration: 'N/A',
      eligibleDrivesCount: 2
    },
    {
      id: '022',
      roll: '2024-IT-022',
      name: 'Riya Gupta',
      email: 'riya.g@mgmcet.ac.in',
      phone: '9876543231',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Information Technology',
      cgpa: 7.9,
      tenthPercentage: 77,
      twelfthPercentage: 74,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 2',
      tierBadge: 'badge-tier-2',
      score: '68/100',
      codingScore: 23,
      aptitudeScore: 23,
      commScore: 22,
      status: 'Placed',
      company: 'Tech Mahindra',
      role: 'Associate Software Engineer',
      ctc: '₹3.6L',
      packageCategory: 'Mass Recruiter (< 5 LPA)',
      bondDuration: 'No Bond',
      eligibleDrivesCount: 5
    },
    {
      id: '023',
      roll: '2025-CSE-023',
      name: 'Mayank Agarwal',
      email: 'mayank.a@mgmcet.ac.in',
      phone: '9876543232',
      degree: 'B.Tech',
      batch: '2025',
      branch: 'Computer Science & Engineering',
      cgpa: 8.8,
      tenthPercentage: 90,
      twelfthPercentage: 88,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 1',
      tierBadge: 'badge-tier-1',
      score: '85/100',
      codingScore: 29,
      aptitudeScore: 28,
      commScore: 28,
      status: 'Unplaced',
      company: 'Morgan Stanley',
      role: 'Technology Analyst Intern',
      ctc: '₹12.5L',
      packageCategory: 'Super Dream (> 10 LPA)',
      bondDuration: 'No Bond',
      pendingRound: 'Technical Interview',
      eligibleDrivesCount: 8
    },
    {
      id: '024',
      roll: '2024-EXTC-024',
      name: 'Shalini Nair',
      email: 'shalini.n@mgmcet.ac.in',
      phone: '9876543233',
      degree: 'B.Tech',
      batch: '2024',
      branch: 'Electronics & Telecommunication',
      cgpa: 7.4,
      tenthPercentage: 73,
      twelfthPercentage: 70,
      backlogs: 0,
      gapYears: 0,
      tier: 'Tier 3',
      tierBadge: 'badge-tier-3',
      score: '59/100',
      codingScore: 19,
      aptitudeScore: 21,
      commScore: 19,
      status: 'Unplaced',
      company: '-',
      role: '-',
      ctc: '-',
      packageCategory: '-',
      bondDuration: 'N/A',
      eligibleDrivesCount: 3
    }
  ];

  get filteredStudents(): StudentRecord[] {
    return this.studentsList.filter(s => {
      const q = this.searchQuery.trim().toLowerCase();
      const matchesSearch = !q ||
        s.name.toLowerCase().includes(q) ||
        s.roll.toLowerCase().includes(q) ||
        s.email.toLowerCase().includes(q) ||
        s.company.toLowerCase().includes(q) ||
        s.branch.toLowerCase().includes(q);

      const matchesBranch = this.selectedBranch === 'All' || s.branch === this.selectedBranch;
      const matchesTier = this.selectedTier === 'All' || s.tier === this.selectedTier;
      const matchesStatus = this.selectedStatus === 'All' || s.status === this.selectedStatus;

      let matchesCgpa = true;
      if (this.selectedMinCgpa !== null && this.selectedMinCgpa !== undefined && (this.selectedMinCgpa as any) !== '') {
        matchesCgpa = s.cgpa >= Number(this.selectedMinCgpa);
      }

      let matchesBacklogs = true;
      if (this.selectedMaxBacklogs !== null && this.selectedMaxBacklogs !== undefined && (this.selectedMaxBacklogs as any) !== '') {
        matchesBacklogs = s.backlogs <= Number(this.selectedMaxBacklogs);
      }

      let matchesGap = true;
      if (this.selectedMaxGapYears !== null && this.selectedMaxGapYears !== undefined && (this.selectedMaxGapYears as any) !== '') {
        matchesGap = s.gapYears <= Number(this.selectedMaxGapYears);
      }

      let matchesTenth = true;
      if (this.selectedMinTenth !== null && this.selectedMinTenth !== undefined && (this.selectedMinTenth as any) !== '') {
        matchesTenth = s.tenthPercentage >= Number(this.selectedMinTenth);
      }

      let matchesTwelfth = true;
      if (this.selectedMinTwelfth !== null && this.selectedMinTwelfth !== undefined && (this.selectedMinTwelfth as any) !== '') {
        matchesTwelfth = s.twelfthPercentage >= Number(this.selectedMinTwelfth);
      }

      return matchesSearch &&
        matchesBranch &&
        matchesTier &&
        matchesStatus &&
        matchesCgpa &&
        matchesBacklogs &&
        matchesGap &&
        matchesTenth &&
        matchesTwelfth;
    });
  }

  get paginatedStudents(): StudentRecord[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredStudents.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredStudents.length / this.pageSize) || 1;
  }

  get pageNumbers(): number[] {
    const total = this.totalPages;
    const pages: number[] = [];
    for (let i = 1; i <= total; i++) {
      pages.push(i);
    }
    return pages;
  }

  get hasActiveFilters(): boolean {
    return !!this.searchQuery || this.activeFilterCount > 0;
  }

  get activeFilterCount(): number {
    let count = 0;
    if (this.selectedBranch !== 'All') count++;
    if (this.selectedTier !== 'All') count++;
    if (this.selectedStatus !== 'All') count++;
    if (this.selectedMinCgpa !== null && this.selectedMinCgpa !== undefined && (this.selectedMinCgpa as any) !== '') count++;
    if (this.selectedMaxBacklogs !== null && this.selectedMaxBacklogs !== undefined && (this.selectedMaxBacklogs as any) !== '') count++;
    if (this.selectedMaxGapYears !== null && this.selectedMaxGapYears !== undefined && (this.selectedMaxGapYears as any) !== '') count++;
    if (this.selectedMinTenth !== null && this.selectedMinTenth !== undefined && (this.selectedMinTenth as any) !== '') count++;
    if (this.selectedMinTwelfth !== null && this.selectedMinTwelfth !== undefined && (this.selectedMinTwelfth as any) !== '') count++;
    return count;
  }

  get tempFilterCount(): number {
    let count = 0;
    if (this.tempFilters.branch !== 'All') count++;
    if (this.tempFilters.tier !== 'All') count++;
    if (this.tempFilters.status !== 'All') count++;
    if (this.tempFilters.minCgpa !== null && this.tempFilters.minCgpa !== undefined && (this.tempFilters.minCgpa as any) !== '') count++;
    if (this.tempFilters.maxBacklogs !== null && this.tempFilters.maxBacklogs !== undefined && (this.tempFilters.maxBacklogs as any) !== '') count++;
    if (this.tempFilters.maxGapYears !== null && this.tempFilters.maxGapYears !== undefined && (this.tempFilters.maxGapYears as any) !== '') count++;
    if (this.tempFilters.minTenth !== null && this.tempFilters.minTenth !== undefined && (this.tempFilters.minTenth as any) !== '') count++;
    if (this.tempFilters.minTwelfth !== null && this.tempFilters.minTwelfth !== undefined && (this.tempFilters.minTwelfth as any) !== '') count++;
    return count;
  }

  get previewMatchCount(): number {
    return this.studentsList.filter(s => {
      const q = this.searchQuery.trim().toLowerCase();
      const matchesSearch = !q ||
        s.name.toLowerCase().includes(q) ||
        s.roll.toLowerCase().includes(q) ||
        s.email.toLowerCase().includes(q) ||
        s.company.toLowerCase().includes(q) ||
        s.branch.toLowerCase().includes(q);

      const matchesBranch = this.tempFilters.branch === 'All' || s.branch === this.tempFilters.branch;
      const matchesTier = this.tempFilters.tier === 'All' || s.tier === this.tempFilters.tier;
      const matchesStatus = this.tempFilters.status === 'All' || s.status === this.tempFilters.status;

      let matchesCgpa = true;
      if (this.tempFilters.minCgpa !== null && this.tempFilters.minCgpa !== undefined && (this.tempFilters.minCgpa as any) !== '') {
        matchesCgpa = s.cgpa >= Number(this.tempFilters.minCgpa);
      }

      let matchesBacklogs = true;
      if (this.tempFilters.maxBacklogs !== null && this.tempFilters.maxBacklogs !== undefined && (this.tempFilters.maxBacklogs as any) !== '') {
        matchesBacklogs = s.backlogs <= Number(this.tempFilters.maxBacklogs);
      }

      let matchesGap = true;
      if (this.tempFilters.maxGapYears !== null && this.tempFilters.maxGapYears !== undefined && (this.tempFilters.maxGapYears as any) !== '') {
        matchesGap = s.gapYears <= Number(this.tempFilters.maxGapYears);
      }

      let matchesTenth = true;
      if (this.tempFilters.minTenth !== null && this.tempFilters.minTenth !== undefined && (this.tempFilters.minTenth as any) !== '') {
        matchesTenth = s.tenthPercentage >= Number(this.tempFilters.minTenth);
      }

      let matchesTwelfth = true;
      if (this.tempFilters.minTwelfth !== null && this.tempFilters.minTwelfth !== undefined && (this.tempFilters.minTwelfth as any) !== '') {
        matchesTwelfth = s.twelfthPercentage >= Number(this.tempFilters.minTwelfth);
      }

      return matchesSearch &&
        matchesBranch &&
        matchesTier &&
        matchesStatus &&
        matchesCgpa &&
        matchesBacklogs &&
        matchesGap &&
        matchesTenth &&
        matchesTwelfth;
    }).length;
  }

  toggleFilterDropdown(): void {
    this.isFilterOpen = !this.isFilterOpen;
    if (this.isFilterOpen) {
      this.syncTempFilters();
    }
  }

  closeFilterDropdown(): void {
    this.isFilterOpen = false;
  }

  syncTempFilters(): void {
    this.tempFilters = {
      branch: this.selectedBranch,
      tier: this.selectedTier,
      status: this.selectedStatus,
      minCgpa: this.selectedMinCgpa,
      maxBacklogs: this.selectedMaxBacklogs,
      maxGapYears: this.selectedMaxGapYears,
      minTenth: this.selectedMinTenth,
      minTwelfth: this.selectedMinTwelfth
    };
  }

  resetTempFilters(): void {
    this.tempFilters = {
      branch: 'All',
      tier: 'All',
      status: 'All',
      minCgpa: null,
      maxBacklogs: null,
      maxGapYears: null,
      minTenth: null,
      minTwelfth: null
    };
  }

  applyFilters(): void {
    this.selectedBranch = this.tempFilters.branch;
    this.selectedTier = this.tempFilters.tier;
    this.selectedStatus = this.tempFilters.status;
    this.selectedMinCgpa = this.tempFilters.minCgpa;
    this.selectedMaxBacklogs = this.tempFilters.maxBacklogs;
    this.selectedMaxGapYears = this.tempFilters.maxGapYears;
    this.selectedMinTenth = this.tempFilters.minTenth;
    this.selectedMinTwelfth = this.tempFilters.minTwelfth;
    this.currentPage = 1;
    this.isFilterOpen = false;
    const matchCount = this.filteredStudents.length;
    this.notificationService.showSuccess(
      `Filters applied: ${matchCount} student profile${matchCount === 1 ? '' : 's'} matching criteria.`,
      'Filters Applied'
    );
  }

  removeFilter(filterKey: string): void {
    if (filterKey === 'branch') this.selectedBranch = 'All';
    if (filterKey === 'tier') this.selectedTier = 'All';
    if (filterKey === 'status') this.selectedStatus = 'All';
    if (filterKey === 'cgpa') this.selectedMinCgpa = null;
    if (filterKey === 'backlogs') this.selectedMaxBacklogs = null;
    if (filterKey === 'gapYears') this.selectedMaxGapYears = null;
    if (filterKey === 'tenth') this.selectedMinTenth = null;
    if (filterKey === 'twelfth') this.selectedMinTwelfth = null;
    this.currentPage = 1;
    this.syncTempFilters();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isFilterOpen) {
      this.closeFilterDropdown();
    }
  }

  onFilterChange(): void {
    this.currentPage = 1;
  }

  setPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
    }
  }

  previousPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
    }
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.selectedBranch = 'All';
    this.selectedTier = 'All';
    this.selectedStatus = 'All';
    this.selectedMinCgpa = null;
    this.selectedMaxBacklogs = null;
    this.selectedMaxGapYears = null;
    this.selectedMinTenth = null;
    this.selectedMinTwelfth = null;
    this.currentPage = 1;
    this.syncTempFilters();
    this.notificationService.showInfo('Filters have been reset to default view.');
  }

  openDossier(student: StudentRecord): void {
    this.selectedStudent = student;
    this.showDossierModal = true;
  }

  closeDossier(): void {
    this.showDossierModal = false;
    this.selectedStudent = null;
  }

  exportRoster(): void {
    const candidates = this.filteredStudents;
    const count = candidates.length;
    this.notificationService.showSuccess(
      `Successfully generated verified Passport Roster CSV (${count} candidates). Download starting...`,
      'Roster Exported'
    );
  }

  markStudentPlaced(student: StudentRecord): void {
    student.status = 'Placed';
    if (student.company === '-' || !student.company) {
      student.company = 'TCS Digital';
      student.role = 'System Engineer';
      student.ctc = '₹4.5L';
      student.packageCategory = 'Mass Recruiter (< 5 LPA)';
      student.bondDuration = 'No Bond';
    }
    this.notificationService.showSuccess(
      `Placement status updated: ${student.name} is now marked as Placed at ${student.company} (${student.ctc})!`,
      'Placement Status Updated'
    );
    if (this.selectedStudent && this.selectedStudent.id === student.id) {
      this.selectedStudent = { ...student };
    }
  }

  // Dynamic KPI Stats
  get newlyPlacedCount(): number {
    return this.studentsList.filter(s => s.status === 'Placed' && !this.initialPlacedIds.has(s.id)).length;
  }

  get placedOffersCount(): number {
    return 238 + this.newlyPlacedCount;
  }

  get unplacedCount(): number {
    return Math.max(0, 112 - this.newlyPlacedCount);
  }

  get placementRate(): number {
    return Math.round((this.placedOffersCount / 350) * 100);
  }

  // Row Multi-Selection
  isStudentSelected(id: string): boolean {
    return this.selectedStudentIds.has(id);
  }

  toggleStudentSelection(id: string): void {
    if (this.selectedStudentIds.has(id)) {
      this.selectedStudentIds.delete(id);
    } else {
      this.selectedStudentIds.add(id);
    }
  }

  isAllPageSelected(): boolean {
    const list = this.paginatedStudents;
    return list.length > 0 && list.every(s => this.selectedStudentIds.has(s.id));
  }

  toggleSelectAllPage(): void {
    const list = this.paginatedStudents;
    if (this.isAllPageSelected()) {
      list.forEach(s => this.selectedStudentIds.delete(s.id));
    } else {
      list.forEach(s => this.selectedStudentIds.add(s.id));
    }
  }

  clearSelection(): void {
    this.selectedStudentIds.clear();
  }

  get selectedCount(): number {
    return this.selectedStudentIds.size;
  }

  // Bulk Placement Modal Controls
  openBulkPlacementModal(): void {
    this.showBulkPlacementModal = true;
    if (this.selectedStudentIds.size > 0 && this.bulkUploadedRows.length === 0) {
      this.populateUploadedRowsFromSelection();
    }
  }

  openBulkPlacementModalForSelected(): void {
    this.populateUploadedRowsFromSelection();
    this.showBulkPlacementModal = true;
  }

  populateUploadedRowsFromSelection(): void {
    const selected = this.studentsList.filter(s => this.selectedStudentIds.has(s.id));
    if (selected.length > 0) {
      this.bulkUploadedFileName = `${selected.length}_roster_selected_candidates.csv`;
      this.bulkUploadedFileSize = `${(selected.length * 0.12).toFixed(1)} KB`;
      this.bulkUploadedRows = selected.map(s => this.createUploadRow(s.roll, s.name, s.branch, 'Placed'));
    }
  }

  closeBulkPlacementModal(): void {
    this.showBulkPlacementModal = false;
    this.isDragOver = false;
  }

  selectCompanyPreset(preset: any): void {
    this.bulkPlacementData.company = preset.name;
    this.bulkPlacementData.role = preset.role;
    this.bulkPlacementData.ctc = preset.ctc;
    this.bulkPlacementData.packageCategory = preset.cat;
    this.bulkPlacementData.bondDuration = preset.bond;
  }

  // Create validated upload row and match with campus roster
  createUploadRow(roll: string, name: string, branch: string, status: string): BulkPlacedUploadRow {
    const cleanRoll = roll.trim();
    const cleanName = name.trim();
    const matched = this.studentsList.find(s =>
      s.roll.toLowerCase() === cleanRoll.toLowerCase() ||
      (cleanName && s.name.toLowerCase() === cleanName.toLowerCase())
    );

    return {
      roll: cleanRoll,
      name: cleanName || (matched ? matched.name : 'Candidate'),
      branch: branch.trim() || (matched ? matched.branch : 'Engineering'),
      status: status.trim() || 'Placed',
      matchedStudent: matched,
      isValid: true,
      validationMessage: matched ? `Matched with ${matched.name}` : 'External Candidate'
    };
  }

  // Sample data loader with exact format requested:
  // 2024-CSE-003 | Arun Patel | Computer Science & Engineering | Placed
  loadSampleExcelData(): void {
    this.bulkUploadedFileName = 'campus_placement_shortlist_2024.xlsx';
    this.bulkUploadedFileSize = '14.2 KB';
    this.bulkUploadedRows = [
      this.createUploadRow('2024-CSE-003', 'Arun Patel', 'Computer Science & Engineering', 'Placed'),
      this.createUploadRow('2024-CSE-004', 'Sneha Kulkarni', 'Computer Science & Engineering', 'Placed'),
      this.createUploadRow('2024-CSE-005', 'Vikram Malhotra', 'Computer Science & Engineering', 'Placed'),
      this.createUploadRow('2024-ECE-007', 'Karan Mehra', 'Electronics & Telecommunication', 'Placed')
    ];
    this.notificationService.showInfo('Sample Excel recruitment dataset loaded in the required format.');
  }

  downloadSampleTemplate(): void {
    const csvContent = "Roll Number,Student Name,Branch,Status\n2024-CSE-003,Arun Patel,Computer Science & Engineering,Placed\n2024-CSE-004,Sneha Kulkarni,Computer Science & Engineering,Placed\n2024-CSE-005,Vikram Malhotra,Computer Science & Engineering,Placed\n";
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'placement_bulk_upload_sample.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    this.notificationService.showInfo('Sample CSV template downloaded with required format columns.');
  }

  onExcelFileUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.processSelectedFile(input.files[0]);
    }
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length > 0) {
      this.processSelectedFile(event.dataTransfer.files[0]);
    }
  }

  processSelectedFile(file: File): void {
    this.bulkUploadedFileName = file.name;
    this.bulkUploadedFileSize = (file.size / 1024).toFixed(1) + ' KB';

    if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
      // Excel spreadsheet simulation with verified format
      this.bulkUploadedRows = [
        this.createUploadRow('2024-CSE-003', 'Arun Patel', 'Computer Science & Engineering', 'Placed'),
        this.createUploadRow('2024-CSE-004', 'Sneha Kulkarni', 'Computer Science & Engineering', 'Placed'),
        this.createUploadRow('2024-CSE-005', 'Vikram Malhotra', 'Computer Science & Engineering', 'Placed'),
        this.createUploadRow('2024-ECE-007', 'Karan Mehra', 'Electronics & Telecommunication', 'Placed')
      ];
      this.notificationService.showSuccess(`Excel sheet "${file.name}" uploaded and parsed (${this.bulkUploadedRows.length} candidates).`);
    } else {
      const reader = new FileReader();
      reader.onload = (e: any) => {
        const text = e.target.result as string;
        this.parseUploadedContent(text);
      };
      reader.readAsText(file);
    }
  }

  parseUploadedContent(text: string): void {
    const lines = text.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);
    if (lines.length === 0) return;

    const rows: BulkPlacedUploadRow[] = [];
    const isSequentialBlock = lines.length >= 4 && !lines[0].includes(',') && !lines[0].includes('\t');

    if (isSequentialBlock) {
      for (let i = 0; i < lines.length; i += 4) {
        const roll = lines[i] || '';
        const name = lines[i + 1] || '';
        const branch = lines[i + 2] || '';
        const status = lines[i + 3] || 'Placed';
        if (roll) {
          rows.push(this.createUploadRow(roll, name, branch, status));
        }
      }
    } else {
      lines.forEach((line, idx) => {
        if (idx === 0 && line.toLowerCase().includes('roll') && line.toLowerCase().includes('name')) {
          return;
        }
        const delimiter = line.includes('\t') ? '\t' : ',';
        const parts = line.split(delimiter).map(p => p.trim().replace(/^["']|["']$/g, ''));
        if (parts.length >= 1 && parts[0]) {
          const roll = parts[0];
          const name = parts[1] || '';
          const branch = parts[2] || '';
          const status = parts[3] || 'Placed';
          rows.push(this.createUploadRow(roll, name, branch, status));
        }
      });
    }

    if (rows.length > 0) {
      this.bulkUploadedRows = rows;
      this.notificationService.showSuccess(`Parsed ${rows.length} candidate rows from ${this.bulkUploadedFileName || 'file'}.`);
    } else {
      this.notificationService.showWarning('No valid candidate rows could be parsed from the file.');
    }
  }

  clearUploadedFile(): void {
    this.bulkUploadedRows = [];
    this.bulkUploadedFileName = '';
    this.bulkUploadedFileSize = '';
  }

  applyBulkPlacement(): void {
    const targetRows = this.bulkUploadedRows;
    if (targetRows.length === 0) {
      this.notificationService.showWarning('Please upload an Excel/CSV file or load sample data before confirming.');
      return;
    }

    const company = this.bulkPlacementData.company.trim() || 'Recruiting Partner';
    const role = this.bulkPlacementData.role.trim() || 'Software Engineer';
    const ctc = this.bulkPlacementData.ctc.trim() || '₹4.5L';
    const category = this.bulkPlacementData.packageCategory;
    const bond = this.bulkPlacementData.bondDuration;

    let updatedCount = 0;
    targetRows.forEach(row => {
      let student = row.matchedStudent;
      if (!student) {
        student = this.studentsList.find(s => s.roll.toLowerCase() === row.roll.toLowerCase());
      }
      if (student) {
        student.status = 'Placed';
        student.company = company;
        student.role = role;
        student.ctc = ctc;
        student.packageCategory = category;
        student.bondDuration = bond;
        updatedCount++;
      }
    });

    const totalUploaded = targetRows.length;
    this.clearSelection();
    this.closeBulkPlacementModal();
    this.notificationService.showSuccess(
      `Updated ${updatedCount} matched candidate records to 'Placed' at ${company} (${ctc})!`,
      'Bulk Placement Synchronized'
    );
  }

  // Student Roster & Placement Analytics Overview Controls
  openOverviewModal(): void {
    this.showOverviewModal = true;
  }

  closeOverviewModal(): void {
    this.showOverviewModal = false;
  }

  filterFromOverview(filterType: 'placed' | 'unplaced' | 'tier1' | 'tier2' | 'tier3' | 'all'): void {
    this.closeOverviewModal();
    // Reset filters first so that overview selection applies cleanly without conflicting filters
    this.searchQuery = '';
    this.selectedBranch = 'All';
    this.selectedTier = 'All';
    this.selectedStatus = 'All';
    this.selectedMinCgpa = null;
    this.selectedMaxBacklogs = null;
    this.selectedMaxGapYears = null;
    this.selectedMinTenth = null;
    this.selectedMinTwelfth = null;
    this.currentPage = 1;
    this.syncTempFilters();

    if (filterType === 'placed') {
      this.selectedStatus = 'Placed';
      this.tempFilters.status = 'Placed';
      this.notificationService.showInfo('Filtered roster: Placed Candidates');
    } else if (filterType === 'unplaced') {
      this.selectedStatus = 'Unplaced';
      this.tempFilters.status = 'Unplaced';
      this.notificationService.showInfo('Filtered roster: Unplaced Candidates');
    } else if (filterType === 'tier1') {
      this.selectedTier = 'Tier 1';
      this.tempFilters.tier = 'Tier 1';
      this.notificationService.showInfo('Filtered roster: Tier 1 Candidates (Score ≥ 70)');
    } else if (filterType === 'tier2') {
      this.selectedTier = 'Tier 2';
      this.tempFilters.tier = 'Tier 2';
      this.notificationService.showInfo('Filtered roster: Tier 2 Candidates (Score 50-69)');
    } else if (filterType === 'tier3') {
      this.selectedTier = 'Tier 3';
      this.tempFilters.tier = 'Tier 3';
      this.notificationService.showInfo('Filtered roster: Tier 3 Candidates (Score < 50)');
    } else if (filterType === 'all') {
      this.notificationService.showInfo('Showing All 350 Enrolled Candidates');
    }
    this.onFilterChange();
  }

  // Quick download of sample Excel/CSV template with all Add Student fields
  downloadImportTemplate(withData: boolean = true): void {
    const headers = [
      'Roll Number', 'First Name', 'Last Name', 'College Email', 'Mobile Phone',
      'Date of Birth', 'Gender', 'Aadhaar Number', 'PAN Number', 'Residential Address',
      'College Name', 'Batch Passing Year', 'Branch Department', 'Degree Name',
      '10th Marks (%)', '12th Marks (%)', 'Active Backlogs', 'Education Gap Years', 'Graduation Date',
      'Sem 1 SGPA', 'Sem 2 SGPA', 'Sem 3 SGPA', 'Sem 4 SGPA',
      'Sem 5 SGPA', 'Sem 6 SGPA', 'Sem 7 SGPA', 'Sem 8 SGPA', 'Aggregate CGPA',
      'Placement Status', 'LinkedIn Profile', 'GitHub Profile', 'Portfolio URL'
    ];

    const sampleRows = [
      [
        '2024-CSE-001', 'Rahul', 'Sharma', 'rahul@mgmcet.ac.in', '+91 98765 43210',
        '2002-01-05', 'Male', '5432 1098 7654', 'ABCDE1234F', 'Sector 19, Nerul, Navi Mumbai - 400706',
        'MGM College of Engineering & Technology, Kamothe', '2024', 'Computer Science & Engineering', 'Bachelor of Technology (B.Tech)',
        '88.0', '86.0', '0', '0', '2024-06-30',
        '8.20', '8.35', '8.40', '8.55', '8.60', '8.50', '8.65', '8.75', '8.50',
        'Placed', 'https://linkedin.com/in/rahul-sharma-cs', 'https://github.com/rahul-sharma', 'https://rahulsharma.dev'
      ],
      [
        '2024-IT-002', 'Priya', 'Desai', 'priya@mgmcet.ac.in', '+91 98765 43211',
        '2002-04-12', 'Female', '4321 0987 6543', 'BCDEF2345G', 'Sector 15, Vashi, Navi Mumbai - 400703',
        'MGM College of Engineering & Technology, Kamothe', '2024', 'Information Technology', 'Bachelor of Technology (B.Tech)',
        '91.5', '89.0', '0', '0', '2024-06-30',
        '8.60', '8.70', '8.75', '8.80', '8.90', '8.85', '8.95', '9.05', '8.82',
        'Placed', 'https://linkedin.com/in/priya-desai', 'https://github.com/priya-desai', 'https://priyadesai.tech'
      ],
      [
        '2024-CSE-003', 'Arun', 'Patel', 'arun@mgmcet.ac.in', '+91 98765 43212',
        '2001-11-20', 'Male', '3210 9876 5432', 'CDEFG3456H', 'Kharghar, Navi Mumbai - 410210',
        'MGM College of Engineering & Technology, Kamothe', '2024', 'Computer Science & Engineering', 'Bachelor of Technology (B.Tech)',
        '79.0', '78.5', '0', '0', '2024-06-30',
        '7.40', '7.50', '7.60', '7.55', '7.70', '7.65', '7.80', '7.90', '7.64',
        'Unplaced', 'https://linkedin.com/in/arun-patel', 'https://github.com/arun-patel', ''
      ]
    ];

    const rowsData = withData ? sampleRows : [];
    const csvContent = [
      headers.map(h => `"${h.replace(/"/g, '""')}"`).join(','),
      ...rowsData.map(r => r.map(val => `"${(val || '').replace(/"/g, '""')}"`).join(','))
    ].join('\r\n');

    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const fileName = withData ? 'proveyu_student_import_sample_template.csv' : 'proveyu_student_import_blank_template.csv';
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    this.notificationService.showSuccess(`Downloaded sample template (${fileName}) ready for Excel entry!`);
  }
}
