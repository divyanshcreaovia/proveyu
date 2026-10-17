import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface Student {
  id: string;
  roll: string;
  name: string;
  email: string;
  phone: string;
  cgpa: number;
  tier: 'Tier 1' | 'Tier 2' | 'Tier 3';
  proveYuScore: number;
  branch: string;
  batch: string;
  placementStatus: 'Unplaced' | 'Placed' | 'Pending';
  companyPlaced?: string;
  ctcOffered?: string;
  offerDate?: string;
}

export interface CompanyDrive {
  id: string;
  companyName: string;
  logo: string;
  industry: string;
  packageLpa: number;
  roles: string;
  driveDate: string;
  driveMode: 'On-Campus' | 'Virtual' | 'Hybrid';
  venue: string;
  minCgpa: number;
  tierCutoffs: string[]; // e.g. ['Tier 1', 'Tier 2']
  branchFilter: string;
  batchFilter: string;
  degreeFilter?: string;
  minProveYuScore?: number;
  status: 'Scheduled' | 'Ongoing' | 'Completed' | 'Awaiting Schedule';
  isExpanded: boolean;
  mailSent: boolean;
  mailSentDate?: string;
  mailSentCount?: number;
  attended: number;
  offers: number;
  placedStudentIds: string[];
  // Rich Requirement Details from add-requirement form
  hrContactName?: string;
  hrContactEmail?: string;
  hrContactPhone?: string;
  employmentType?: string;
  stipendMonthly?: string;
  bondDurationMonths?: number;
  driveCategory?: string;
  registrationDeadline?: string;
  selectedBranches?: string[];
  selectionRounds?: string[];
  placementEligibility?: string;
  allowPlacedStudents?: boolean;
  showDetailsDrawer?: boolean;
}

@Component({
  selector: 'app-college-placements',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './placements.html',
  styleUrl: './placements.scss',
})
export class Placements implements OnInit {

  constructor(private notificationService: NotificationService) { }

  ngOnInit() {
    this.loadCompaniesFromStorage();
  }

  loadCompaniesFromStorage() {
    try {
      const stored = localStorage.getItem('proveyu_college_companies');
      if (stored) {
        const storedCompanies: CompanyDrive[] = JSON.parse(stored);
        if (Array.isArray(storedCompanies) && storedCompanies.length > 0) {
          const storedMap = new Map(storedCompanies.map(c => [c.id, c]));
          // Merge updated data for existing default drives
          this.companies = this.companies.map(c => storedMap.has(c.id) ? { ...c, ...storedMap.get(c.id) } : c);
          // Prepend newly posted requirements that aren't in default list
          const existingIds = new Set(this.companies.map(c => c.id));
          const newOnes = storedCompanies.filter((c: any) => !existingIds.has(c.id));
          this.companies = [...newOnes, ...this.companies];
        }

        // Ensure every company has at most ONE HR round and no duplicate rounds
        this.companies.forEach(comp => {
          if (comp.selectionRounds && comp.selectionRounds.length > 0) {
            const unique: string[] = [];
            let hrSeen = false;
            for (const r of comp.selectionRounds) {
              const norm = r.includes('HR') ? 'HR Round' : r;
              if (norm === 'HR Round') {
                if (!hrSeen) {
                  unique.push(norm);
                  hrSeen = true;
                }
              } else if (!unique.includes(norm)) {
                unique.push(norm);
              }
            }
            comp.selectionRounds = unique;
          }
        });
      }
    } catch (e) {
      console.error('Error loading stored companies:', e);
    }

    try {
      const storedRounds = localStorage.getItem('proveyu_student_round_status');
      if (storedRounds) {
        this.studentRoundStatusMap = JSON.parse(storedRounds);
      }
      const storedStudents = localStorage.getItem('proveyu_master_students');
      if (storedStudents) {
        const parsed = JSON.parse(storedStudents);
        if (Array.isArray(parsed) && parsed.length > 0) {
          this.masterStudentPool = parsed;
        }
      }
    } catch (e) {
      console.error('Error loading stored rounds/students:', e);
    }

    // Initialize mock round progression for c1 (TCS) if empty to provide immediate live pipeline
    if (Object.keys(this.studentRoundStatusMap).length === 0 || !this.studentRoundStatusMap['c1']) {
      this.studentRoundStatusMap['c1'] = {
        '3': 'HR Round',
        '4': 'Technical Interview Round 1',
        '8': 'Coding Assessment',
        '10': 'Online Aptitude Test',
        '13': 'HR Round',
        '15': 'Technical Interview Round 1',
        '16': 'Coding Assessment',
        '17': 'Online Aptitude Test',
        '18': 'Online Aptitude Test',
        '23': 'Technical Interview Round 1'
      };
    }

    // Ensure no table row is expanded initially on page load
    this.companies.forEach(c => c.isExpanded = false);
  }

  saveCompaniesToStorage() {
    try {
      localStorage.setItem('proveyu_college_companies', JSON.stringify(this.companies));
    } catch (e) {
      console.error('Error saving companies to storage:', e);
    }
  }

  saveAllDataToStorage() {
    this.saveCompaniesToStorage();
    try {
      localStorage.setItem('proveyu_student_round_status', JSON.stringify(this.studentRoundStatusMap));
      localStorage.setItem('proveyu_master_students', JSON.stringify(this.masterStudentPool));
    } catch (e) {
      console.error('Error saving round status / students:', e);
    }
  }

  // Navigation Tab State
  activeTab: 'requirements' | 'drives_tracker' = 'requirements';

  // Search & Filter State
  searchCompanyQuery: string = '';
  selectedModeFilter: string = 'All';
  selectedStatusFilter: string = 'All';
  placeModalSearchQuery: string = '';

  searchDriveQuery: string = '';
  selectedDriveStatusFilter: string = 'All';

  // Modal Display States
  showAddModal: boolean = false;
  showAddDriveModal: boolean = false;
  showEditDriveModal: boolean = false;
  showMailModal: boolean = false;
  showSelectedCandidatesModal: boolean = false;

  toastMessage: string | null = null;

  // Pagination State Variables (10 ROWS PER PAGE EXPLICIT)
  companyCurrentPage: { [companyId: string]: number } = {};
  companyPageSize: number = 10;

  drivesCurrentPage: number = 1;
  drivesPageSize: number = 10;

  selectedModalCurrentPage: number = 1;
  selectedModalPageSize: number = 10;

  // Selected Candidates Modal Context
  activeModalDrive: CompanyDrive | null = null;
  activeModalTitle: string = '';
  activeModalSubtitle: string = '';
  selectedCandidatesForModal: Student[] = [];
  selectedStudentToPlaceId: string = '';

  // Active Drive for Editing
  editingDrive: CompanyDrive | null = null;

  // Candidate Round Tracking Map
  studentRoundStatusMap: { [companyId: string]: { [studentId: string]: string } } = {};

  // Master Student Pool
  masterStudentPool: Student[] = [
    { id: '1', roll: '2024-CSE-001', name: 'Rahul Sharma', email: 'rahul.s@mgmcet.ac.in', phone: '+91-9876543210', cgpa: 8.5, tier: 'Tier 1', proveYuScore: 82, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 5, 2026' },
    { id: '2', roll: '2024-IT-002', name: 'Priya Desai', email: 'priya.d@mgmcet.ac.in', phone: '+91-9876543211', cgpa: 8.8, tier: 'Tier 1', proveYuScore: 78, branch: 'Information Tech', batch: '2024', placementStatus: 'Placed', companyPlaced: 'Wipro Turbo Hiring', ctcOffered: '6.5 LPA', offerDate: 'Oct 8, 2026' },
    { id: '3', roll: '2024-CSE-003', name: 'Arun Patel', email: 'arun.patel@mgmcet.ac.in', phone: '+91-9876543212', cgpa: 8.4, tier: 'Tier 1', proveYuScore: 78, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '4', roll: '2024-CSE-004', name: 'Neha Deshmukh', email: 'neha.d@mgmcet.ac.in', phone: '+91-9876543213', cgpa: 8.2, tier: 'Tier 2', proveYuScore: 72, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '5', roll: '2024-EXTC-005', name: 'Amit Verma', email: 'amit.v@mgmcet.ac.in', phone: '+91-9876543214', cgpa: 7.8, tier: 'Tier 2', proveYuScore: 64, branch: 'Electronics', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 12, 2026' },
    { id: '6', roll: '2024-CSE-006', name: 'Divya Nair', email: 'divya.n@mgmcet.ac.in', phone: '+91-9876543215', cgpa: 8.9, tier: 'Tier 1', proveYuScore: 88, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 14, 2026' },
    { id: '7', roll: '2024-IT-007', name: 'Sanjay Dutt', email: 'sanjay.d@mgmcet.ac.in', phone: '+91-9876543216', cgpa: 8.1, tier: 'Tier 2', proveYuScore: 69, branch: 'Information Tech', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 15, 2026' },
    { id: '8', roll: '2024-IT-008', name: 'Sandhya Kulkarni', email: 'sandhya.k@mgmcet.ac.in', phone: '+91-9876543217', cgpa: 8.6, tier: 'Tier 1', proveYuScore: 85, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '9', roll: '2024-EXTC-009', name: 'Meera Iyer', email: 'meera.i@mgmcet.ac.in', phone: '+91-9876543218', cgpa: 8.3, tier: 'Tier 1', proveYuScore: 76, branch: 'Electronics', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 16, 2026' },
    { id: '10', roll: '2024-IT-011', name: 'Vikram Reddy', email: 'vikram.r@mgmcet.ac.in', phone: '+91-9876543219', cgpa: 8.1, tier: 'Tier 2', proveYuScore: 68, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '11', roll: '2024-CSE-012', name: 'Aniket Solanki', email: 'aniket.s@mgmcet.ac.in', phone: '+91-9876543220', cgpa: 8.7, tier: 'Tier 1', proveYuScore: 84, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 18, 2026' },
    { id: '12', roll: '2024-CSE-013', name: 'Pooja Hegde', email: 'pooja.h@mgmcet.ac.in', phone: '+91-9876543221', cgpa: 8.4, tier: 'Tier 1', proveYuScore: 79, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed', companyPlaced: 'TCS Digital Drive', ctcOffered: '7.5 LPA', offerDate: 'Oct 19, 2026' },
    { id: '13', roll: '2024-CSE-015', name: 'Ananya Roy', email: 'ananya.roy@mgmcet.ac.in', phone: '+91-9876543222', cgpa: 9.1, tier: 'Tier 1', proveYuScore: 92, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '14', roll: '2024-IT-016', name: 'Karan Johar', email: 'karan.j@mgmcet.ac.in', phone: '+91-9876543223', cgpa: 8.5, tier: 'Tier 1', proveYuScore: 81, branch: 'Information Tech', batch: '2024', placementStatus: 'Placed', companyPlaced: 'Wipro Turbo Hiring', ctcOffered: '6.5 LPA', offerDate: 'Oct 20, 2026' },
    { id: '15', roll: '2024-EXTC-018', name: 'Siddharth Roy', email: 'siddharth.r@mgmcet.ac.in', phone: '+91-9876543224', cgpa: 8.2, tier: 'Tier 2', proveYuScore: 70, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' },
    { id: '16', roll: '2024-EXTC-020', name: 'Rohan Gupta', email: 'rohan.g@mgmcet.ac.in', phone: '+91-9876543225', cgpa: 8.0, tier: 'Tier 2', proveYuScore: 66, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' },
    { id: '17', roll: '2024-CSE-021', name: 'Kavita Joshi', email: 'kavita.j@mgmcet.ac.in', phone: '+91-9876543226', cgpa: 8.6, tier: 'Tier 1', proveYuScore: 83, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '18', roll: '2024-CSE-022', name: 'Pooja Verma', email: 'pooja.v@mgmcet.ac.in', phone: '+91-9876543228', cgpa: 8.7, tier: 'Tier 1', proveYuScore: 81, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '19', roll: '2024-IT-023', name: 'Manish Pandey', email: 'manish.p@mgmcet.ac.in', phone: '+91-9876543229', cgpa: 8.3, tier: 'Tier 2', proveYuScore: 73, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '20', roll: '2024-IT-025', name: 'Karan Malhotra', email: 'karan.m@mgmcet.ac.in', phone: '+91-9876543230', cgpa: 8.3, tier: 'Tier 2', proveYuScore: 74, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '21', roll: '2024-CSE-026', name: 'Swati Kulkarni', email: 'swati.k@mgmcet.ac.in', phone: '+91-9876543231', cgpa: 8.8, tier: 'Tier 1', proveYuScore: 87, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed', companyPlaced: 'Amdocs Product Innovation', ctcOffered: '8.2 LPA', offerDate: 'Oct 24, 2026' },
    { id: '22', roll: '2024-EXTC-027', name: 'Tushar Shinde', email: 'tushar.s@mgmcet.ac.in', phone: '+91-9876543232', cgpa: 8.1, tier: 'Tier 2', proveYuScore: 68, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' },
    { id: '23', roll: '2024-CSE-028', name: 'Shruti Saxena', email: 'shruti.s@mgmcet.ac.in', phone: '+91-9876543233', cgpa: 8.4, tier: 'Tier 1', proveYuScore: 80, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '24', roll: '2024-IT-029', name: 'Abhishek Naik', email: 'abhishek.n@mgmcet.ac.in', phone: '+91-9876543234', cgpa: 8.5, tier: 'Tier 1', proveYuScore: 82, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '25', roll: '2024-EXTC-030', name: 'Gaurav Sen', email: 'gaurav.s@mgmcet.ac.in', phone: '+91-9876543235', cgpa: 7.9, tier: 'Tier 2', proveYuScore: 65, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' }
  ];

  // Master Company Drives List
  companies: CompanyDrive[] = [
    {
      id: 'c1',
      companyName: 'TCS Digital Drive',
      logo: 'https://logo.clearbit.com/tcs.com',
      industry: 'IT Services & Consulting',
      packageLpa: 7.5,
      roles: 'Digital Software Engineer & Cloud Developer',
      driveDate: '2026-10-15',
      registrationDeadline: '2026-10-10',
      driveMode: 'On-Campus',
      venue: 'Main Auditorium & Computer Lab A',
      minCgpa: 7.8,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'All Branches',
      batchFilter: '2024 Batch',
      minProveYuScore: 60,
      status: 'Completed',
      isExpanded: false,
      mailSent: true,
      mailSentDate: 'Sep 27, 2026',
      mailSentCount: 15,
      attended: 30,
      offers: 7,
      placedStudentIds: ['1', '5', '6', '7', '9', '11', '12'],
      hrContactName: 'Shruti Varma (Campus Lead)',
      hrContactEmail: 'campus.hiring@tcs.com',
      hrContactPhone: '+91-9876501234',
      employmentType: 'Full-Time Direct Placement',
      stipendMonthly: '₹25,000 / month',
      bondDurationMonths: 0,
      driveCategory: 'Dream Drive (5-10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    },
    {
      id: 'c2',
      companyName: 'Wipro Turbo Hiring',
      logo: 'https://logo.clearbit.com/wipro.com',
      industry: 'IT Services & Automation',
      packageLpa: 6.5,
      roles: 'Project Engineer & QA Specialist',
      driveDate: '2026-10-22',
      registrationDeadline: '2026-10-18',
      driveMode: 'Virtual',
      venue: 'Online Proctor Portal',
      minCgpa: 8.0,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'All Branches',
      batchFilter: '2024 Batch',
      minProveYuScore: 65,
      status: 'Completed',
      isExpanded: false,
      mailSent: true,
      mailSentDate: 'Oct 10, 2026',
      mailSentCount: 18,
      attended: 25,
      offers: 2,
      placedStudentIds: ['2', '14'],
      hrContactName: 'Anil Kumar (HR Lead)',
      hrContactEmail: 'campus@wipro.com',
      hrContactPhone: '+91-9876505678',
      employmentType: 'Full-Time Direct Placement',
      driveCategory: 'Dream Drive (5-10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    },
    {
      id: 'c3',
      companyName: 'Amdocs Product Innovation',
      logo: 'https://logo.clearbit.com/amdocs.com',
      industry: 'Product SaaS & Telecom',
      packageLpa: 8.2,
      roles: 'Software Developer (C++ / Java)',
      driveDate: '2026-11-05',
      registrationDeadline: '2026-11-01',
      driveMode: 'Hybrid',
      venue: 'Computer Lab B & Virtual Round',
      minCgpa: 8.5,
      tierCutoffs: ['Tier 1'],
      branchFilter: 'CS / IT Only',
      batchFilter: '2024 Batch',
      minProveYuScore: 75,
      status: 'Ongoing',
      isExpanded: false,
      mailSent: true,
      mailSentDate: 'Oct 18, 2026',
      mailSentCount: 10,
      attended: 22,
      offers: 1,
      placedStudentIds: ['21'],
      hrContactName: 'Kavita Patel (Talent Acquisition)',
      hrContactEmail: 'careers@amdocs.com',
      hrContactPhone: '+91-9876509988',
      employmentType: 'Internship + Full-Time PPO',
      stipendMonthly: '₹35,000 / month',
      driveCategory: 'Dream Drive (5-10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    },
    {
      id: 'c4',
      companyName: 'Accenture Innovation Drive',
      logo: 'https://logo.clearbit.com/accenture.com',
      industry: 'Global Consulting',
      packageLpa: 5.5,
      roles: 'Associate Software Engineer',
      driveDate: '2026-11-12',
      registrationDeadline: '2026-11-08',
      driveMode: 'On-Campus',
      venue: 'Main Auditorium',
      minCgpa: 7.5,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'All Branches',
      batchFilter: '2024 Batch',
      minProveYuScore: 60,
      status: 'Scheduled',
      isExpanded: false,
      mailSent: false,
      attended: 28,
      offers: 0,
      placedStudentIds: [],
      hrContactName: 'Rohan Mehta (Campus Manager)',
      hrContactEmail: 'india.hiring@accenture.com',
      hrContactPhone: '+91-9876511223',
      employmentType: 'Full-Time Direct Placement',
      driveCategory: 'Dream Drive (5-10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    },
    {
      id: 'c5',
      companyName: 'Cognizant GenC Next',
      logo: 'https://logo.clearbit.com/cognizant.com',
      industry: 'IT Services & Digital',
      packageLpa: 6.8,
      roles: 'Full Stack & AI Engineer',
      driveDate: '2026-11-18',
      registrationDeadline: '2026-11-14',
      driveMode: 'Virtual',
      venue: 'Online Assessment Portal',
      minCgpa: 8.0,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'CS / IT Only',
      batchFilter: '2024 Batch',
      minProveYuScore: 70,
      status: 'Scheduled',
      isExpanded: false,
      mailSent: false,
      attended: 20,
      offers: 0,
      placedStudentIds: [],
      driveCategory: 'Dream Drive (5-10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    },
    {
      id: 'c6',
      companyName: 'Infosys Specialist Programmer',
      logo: 'https://logo.clearbit.com/infosys.com',
      industry: 'Product & Cloud Solutions',
      packageLpa: 9.5,
      roles: 'Specialist Programmer',
      driveDate: '2026-11-25',
      registrationDeadline: '2026-11-20',
      driveMode: 'On-Campus',
      venue: 'Main Computer Lab 1',
      minCgpa: 8.5,
      tierCutoffs: ['Tier 1'],
      branchFilter: 'CS / IT Only',
      batchFilter: '2024 Batch',
      minProveYuScore: 80,
      status: 'Scheduled',
      isExpanded: false,
      mailSent: false,
      attended: 18,
      offers: 0,
      placedStudentIds: [],
      driveCategory: 'Super Dream (> 10 LPA)',
      selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round']
    }
  ];

  // Checkbox selection map for manual overrides
  selectedCandidatesMap: { [companyId: string]: { [studentId: string]: boolean } } = {
    'c1': { '3': true, '4': true, '8': true, '10': true, '13': true, '16': true, '18': true, '20': true },
    'c2': { '3': true, '8': true, '13': true, '17': true, '18': true },
    'c3': { '3': true, '8': true, '13': true }
  };

  // Modal Form Models
  newForm = {
    companyName: '',
    industry: 'IT Services & Consulting',
    packageLpa: 6.0,
    roles: 'Software Engineer / Analyst',
    driveDate: '2026-11-10',
    driveMode: 'On-Campus' as 'On-Campus' | 'Virtual' | 'Hybrid',
    venue: 'Computer Lab A',
    minCgpa: 8.0,
    tier1: true,
    tier2: true,
    tier3: false,
    branchFilter: 'CS / IT Only',
    batchFilter: '2024 Batch',
    minProveYuScore: 70
  };

  newDriveForm = {
    company: '',
    industry: 'IT Services & Consulting',
    date: new Date().toISOString().split('T')[0],
    status: 'Scheduled' as 'Completed' | 'Ongoing' | 'Scheduled' | 'Awaiting Schedule',
    attended: 20,
    offers: 0,
    venue: 'Main Seminar Hall',
    roles: 'Software Engineer',
    ctcOffered: '7.0 LPA',
    minCgpa: 7.5,
    minProveYuScore: 60,
    driveMode: 'On-Campus' as 'On-Campus' | 'Virtual' | 'Hybrid'
  };

  editDriveForm = {
    id: '',
    company: '',
    industry: '',
    date: '',
    status: 'Scheduled' as 'Completed' | 'Ongoing' | 'Scheduled' | 'Awaiting Schedule',
    attended: 0,
    offers: 0,
    venue: '',
    roles: '',
    ctcOffered: '',
    packageLpa: 7.0
  };

  mailForm = {
    subject: '',
    body: '',
    includeAdmitCard: true,
    includeDriveNotice: true
  };
  selectedMailTemplate: string = 'invite';
  activeMailTab: 'compose' | 'preview' = 'compose';
  isMailFullScreen: boolean = false;
  activeCompanyForMail: CompanyDrive | null = null;

  toggleMailFullScreen() {
    this.isMailFullScreen = !this.isMailFullScreen;
  }

  toggleAttachment(type: 'admitCard' | 'notice') {
    if (type === 'admitCard') {
      this.mailForm.includeAdmitCard = !this.mailForm.includeAdmitCard;
      this.showToast(this.mailForm.includeAdmitCard ? 'Attached ProveYu Candidate Admit Card' : 'Removed Admit Card Attachment');
    } else {
      this.mailForm.includeDriveNotice = !this.mailForm.includeDriveNotice;
      this.showToast(this.mailForm.includeDriveNotice ? 'Attached Company Drive Notice PDF' : 'Removed Drive Notice Attachment');
    }
  }

  get sampleEmailPreview(): string {
    if (!this.mailForm.body) return '';
    let preview = this.mailForm.body;
    preview = preview.replace(/{Student Name}/g, 'Rahul Sharma');
    preview = preview.replace(/{Roll Number}/g, '2024-CSE-001');
    preview = preview.replace(/{Branch}/g, 'Computer Science');
    return preview;
  }

  // ----------------------------------------------------
  // COMPUTED DYNAMIC METRICS FOR TOP KPI CARDS
  // ----------------------------------------------------
  get totalActiveDrivesCount(): number {
    return this.companies.length;
  }

  get unplacedStudentsCount(): number {
    return this.masterStudentPool.filter(s => s.placementStatus === 'Unplaced').length;
  }

  get placedStudentsCount(): number {
    return this.masterStudentPool.filter(s => s.placementStatus === 'Placed').length;
  }

  get placedPercentage(): number {
    if (this.masterStudentPool.length === 0) return 0;
    return Math.round((this.placedStudentsCount / this.masterStudentPool.length) * 100);
  }

  get avgPackageOffered(): string {
    const packages: number[] = this.companies.map(c => c.packageLpa);
    if (packages.length === 0) return '7.4 LPA';
    const sum = packages.reduce((acc, curr) => acc + curr, 0);
    return (sum / packages.length).toFixed(1) + ' LPA';
  }

  // ----------------------------------------------------
  // TAB NAVIGATION
  // ----------------------------------------------------
  setTab(tab: 'requirements' | 'drives_tracker') {
    this.activeTab = tab;
  }

  // ----------------------------------------------------
  // AUTO-MATCHING ENGINE LOGIC
  // ----------------------------------------------------
  getMatchedStudents(company: CompanyDrive): Student[] {
    return this.masterStudentPool.filter(student => {
      if (company.placementEligibility === 'Unplaced Only' || !company.allowPlacedStudents) {
        if (student.placementStatus !== 'Unplaced') return false;
      }
      if (student.cgpa < company.minCgpa) return false;
      if (company.tierCutoffs && company.tierCutoffs.length > 0 && !company.tierCutoffs.includes(student.tier)) return false;
      if (company.minProveYuScore && student.proveYuScore < company.minProveYuScore) return false;

      if (company.selectedBranches && company.selectedBranches.length > 0) {
        const isAllSelected = company.selectedBranches.includes('All Students') || company.selectedBranches.includes('All Branches');
        if (!isAllSelected) {
          const studentBranchLower = student.branch.toLowerCase();
          const matchFound = company.selectedBranches.some(b => {
            const bLower = b.toLowerCase();
            if (bLower.includes('cs') || bLower.includes('computer')) return studentBranchLower.includes('computer');
            if (bLower.includes('it') || bLower.includes('information')) return studentBranchLower.includes('information') || studentBranchLower.includes('tech');
            if (bLower.includes('extc') || bLower.includes('electronics')) return studentBranchLower.includes('electronics') || studentBranchLower.includes('extc');
            if (bLower.includes('ai') || bLower.includes('data')) return studentBranchLower.includes('ai') || studentBranchLower.includes('data');
            if (bLower.includes('mechanical')) return studentBranchLower.includes('mechanical');
            if (bLower.includes('civil')) return studentBranchLower.includes('civil');
            if (bLower.includes('electrical')) return studentBranchLower.includes('electrical');
            return studentBranchLower.includes(bLower);
          });
          if (!matchFound) return false;
        }
      } else if (company.branchFilter && company.branchFilter !== 'All Branches') {
        if (company.branchFilter === 'CS / IT Only') {
          const b = student.branch.toLowerCase();
          if (!b.includes('computer') && !b.includes('tech') && !b.includes('information')) return false;
        }
      }
      return true;
    });
  }

  getFilteredCompanies(): CompanyDrive[] {
    return this.companies.filter(c => {
      const matchesSearch = c.companyName.toLowerCase().includes(this.searchCompanyQuery.toLowerCase()) ||
        c.roles.toLowerCase().includes(this.searchCompanyQuery.toLowerCase()) ||
        c.industry.toLowerCase().includes(this.searchCompanyQuery.toLowerCase());
      const matchesMode = this.selectedModeFilter === 'All' || c.driveMode === this.selectedModeFilter;
      const matchesStatus = this.selectedStatusFilter === 'All' || c.status === this.selectedStatusFilter;
      return matchesSearch && matchesMode && matchesStatus;
    });
  }

  getFilteredDriveRecords(): CompanyDrive[] {
    return this.companies.filter(d => {
      const matchesSearch = d.companyName.toLowerCase().includes(this.searchDriveQuery.toLowerCase()) ||
        (d.roles && d.roles.toLowerCase().includes(this.searchDriveQuery.toLowerCase())) ||
        (d.venue && d.venue.toLowerCase().includes(this.searchDriveQuery.toLowerCase()));
      const matchesStatus = this.selectedDriveStatusFilter === 'All' || d.status === this.selectedDriveStatusFilter;
      return matchesSearch && matchesStatus;
    });
  }

  get hasExpandedCompany(): boolean {
    return this.companies.some(c => c.isExpanded);
  }

  toggleExpand(company: CompanyDrive) {
    const currentState = company.isExpanded;
    // Collapse all companies to ensure only 1 drive can be expanded at a time
    this.companies.forEach(c => c.isExpanded = false);

    company.isExpanded = !currentState;
    if (company.isExpanded && !this.selectedCandidatesMap[company.id]) {
      this.selectAllForCompany(company, true);
    }
  }

  toggleDriveDetails(company: CompanyDrive) {
    company.showDetailsDrawer = !company.showDetailsDrawer;
  }

  // ----------------------------------------------------
  // SELECTION ROUNDS PIPELINE METHODS
  // ----------------------------------------------------
  readonly DEFAULT_SELECTION_ROUNDS: string[] = [
    'Online Aptitude Test',
    'Coding Assessment',
    'Technical Interview Round 1',
    'HR Round'
  ];

  activeModalRoundFilter: string = 'All';
  modalSelectedCandidateIds: string[] = [];

  getSelectionRounds(company: CompanyDrive): string[] {
    if (company && company.selectionRounds && company.selectionRounds.length > 0) {
      const unique: string[] = [];
      let hrSeen = false;
      for (const r of company.selectionRounds) {
        const norm = r.includes('HR') ? 'HR Round' : r;
        if (norm === 'HR Round') {
          if (!hrSeen) {
            unique.push(norm);
            hrSeen = true;
          }
        } else if (!unique.includes(norm)) {
          unique.push(norm);
        }
      }
      return unique;
    }
    return [...this.DEFAULT_SELECTION_ROUNDS];
  }

  getRoundCardMeta(round: string, idx: number = 0): { icon: string; shortTitle: string; subtitle: string } {
    const rLower = (round || '').toLowerCase();
    const prefix = `${idx + 1}. `;
    if (rLower.includes('aptitude')) {
      return {
        icon: 'bi-pencil-square',
        shortTitle: prefix + 'Aptitude Test',
        subtitle: 'Online screening'
      };
    }
    if (rLower.includes('coding')) {
      return {
        icon: 'bi-code-slash',
        shortTitle: prefix + 'Coding Test',
        subtitle: 'Algorithm & logic'
      };
    }
    if (rLower.includes('gd') || rLower.includes('discussion')) {
      return {
        icon: 'bi-chat-dots',
        shortTitle: prefix + 'Group Discussion',
        subtitle: 'Communication & GD'
      };
    }
    if (rLower.includes('tech') && (rLower.includes('2') || rLower.includes('two'))) {
      return {
        icon: 'bi-laptop',
        shortTitle: prefix + 'Technical R2',
        subtitle: 'Deep technical round'
      };
    }
    if (rLower.includes('technical') || rLower.includes('tech')) {
      return {
        icon: 'bi-laptop',
        shortTitle: prefix + 'Technical R1',
        subtitle: 'Technical evaluation'
      };
    }
    if (rLower.includes('hr')) {
      return {
        icon: 'bi-person-badge',
        shortTitle: prefix + 'HR Round',
        subtitle: 'Culture fit & terms'
      };
    }
    return {
      icon: 'bi-check2-circle',
      shortTitle: prefix + round,
      subtitle: 'Evaluation stage'
    };
  }

  getRoundShortLabel(round: string): string {
    const rLower = (round || '').toLowerCase();
    if (rLower.includes('aptitude')) return 'Aptitude';
    if (rLower.includes('coding')) return 'Coding';
    if (rLower.includes('gd') || rLower.includes('discussion')) return 'GD';
    if (rLower.includes('tech') && (rLower.includes('2') || rLower.includes('two'))) return 'Tech R2';
    if (rLower.includes('technical') || rLower.includes('tech')) return 'Tech R1';
    if (rLower.includes('hr')) return 'HR Round';
    return round.length > 12 ? round.substring(0, 10) + '..' : round;
  }

  failedLogoIds: Set<string> = new Set<string>();

  onDriveLogoError(id: string) {
    if (id) {
      this.failedLogoIds.add(id);
    }
  }

  hasValidCustomLogo(drive?: CompanyDrive | null): boolean {
    if (!drive || !drive.logo) return false;
    if (drive.logo.includes('clearbit.com')) return false;
    if (this.failedLogoIds.has(drive.id)) return false;
    return true;
  }

  getCompanyInitials(name?: string): string {
    if (!name) return 'CO';
    const trimmed = name.trim();
    if (!trimmed) return 'CO';
    return trimmed.substring(0, 2).toUpperCase();
  }

  onImageError(event: any) {
    if (event && event.target) {
      event.target.style.display = 'none';
    }
  }

  quickOfferCandidate(student: Student, drive: CompanyDrive) {
    this.markCandidatePlaced(student, drive);
  }

  changeDriveStatus(company: CompanyDrive, newStatus: 'Scheduled' | 'Ongoing' | 'Completed' | 'Awaiting Schedule') {
    company.status = newStatus;
    this.saveAllDataToStorage();
    this.showToast(`Updated drive status for "${company.companyName}" to ${newStatus}!`);
  }

  getDriveCandidates(company: CompanyDrive): Student[] {
    return this.masterStudentPool.filter(student => {
      // If student is placed with this company, always include in pipeline
      if (company.placedStudentIds && company.placedStudentIds.includes(student.id)) {
        return true;
      }
      if (student.companyPlaced && student.companyPlaced.toLowerCase() === company.companyName.toLowerCase()) {
        return true;
      }
      // If student is placed with another company and drive doesn't allow placed students
      if (student.placementStatus === 'Placed' && !company.allowPlacedStudents) {
        return false;
      }
      // Eligibility criteria
      if (student.cgpa < company.minCgpa) return false;
      if (company.tierCutoffs && company.tierCutoffs.length > 0 && !company.tierCutoffs.includes(student.tier)) return false;
      if (company.minProveYuScore && student.proveYuScore < company.minProveYuScore) return false;

      if (company.selectedBranches && company.selectedBranches.length > 0) {
        const isAllSelected = company.selectedBranches.includes('All Students') || company.selectedBranches.includes('All Branches');
        if (!isAllSelected) {
          const studentBranchLower = student.branch.toLowerCase();
          const matchFound = company.selectedBranches.some(b => {
            const bLower = b.toLowerCase();
            if (bLower.includes('cs') || bLower.includes('computer')) return studentBranchLower.includes('computer');
            if (bLower.includes('it') || bLower.includes('information')) return studentBranchLower.includes('information') || studentBranchLower.includes('tech');
            if (bLower.includes('extc') || bLower.includes('electronics')) return studentBranchLower.includes('electronics') || studentBranchLower.includes('extc');
            if (bLower.includes('ai') || bLower.includes('data')) return studentBranchLower.includes('ai') || studentBranchLower.includes('data');
            if (bLower.includes('mechanical')) return studentBranchLower.includes('mechanical');
            if (bLower.includes('civil')) return studentBranchLower.includes('civil');
            if (bLower.includes('electrical')) return studentBranchLower.includes('electrical');
            return studentBranchLower.includes(bLower);
          });
          if (!matchFound) return false;
        }
      } else if (company.branchFilter && company.branchFilter !== 'All Branches') {
        if (company.branchFilter === 'CS / IT Only') {
          const b = student.branch.toLowerCase();
          if (!b.includes('computer') && !b.includes('tech') && !b.includes('information')) return false;
        }
      }
      return true;
    });
  }

  getCandidateRound(companyId: string, student: Student): string {
    if (this.studentRoundStatusMap[companyId] && this.studentRoundStatusMap[companyId][student.id] === 'Rejected') {
      return 'Rejected';
    }
    const company = this.companies.find(c => c.id === companyId) || this.activeModalDrive;
    if (company && (company.placedStudentIds?.includes(student.id) || (student.placementStatus === 'Placed' && student.companyPlaced?.toLowerCase() === company.companyName.toLowerCase()))) {
      return 'Placed';
    }
    if (this.studentRoundStatusMap[companyId] && this.studentRoundStatusMap[companyId][student.id]) {
      return this.studentRoundStatusMap[companyId][student.id];
    }
    const rounds = company ? this.getSelectionRounds(company) : this.DEFAULT_SELECTION_ROUNDS;
    return rounds[0] || 'Online Aptitude Test';
  }

  getCandidateRoundIndex(company: CompanyDrive, student: Student): number {
    const rounds = this.getSelectionRounds(company);
    const round = this.getCandidateRound(company.id, student);
    if (round === 'Placed') return rounds.length;
    if (round === 'Rejected') return -1;
    const idx = rounds.indexOf(round);
    return idx >= 0 ? idx : 0;
  }

  setCandidateRound(companyId: string, studentId: string, roundName: string) {
    if (!this.studentRoundStatusMap[companyId]) {
      this.studentRoundStatusMap[companyId] = {};
    }
    this.studentRoundStatusMap[companyId][studentId] = roundName;
  }

  promoteCandidateRound(student: Student, company: CompanyDrive) {
    const rounds = this.getSelectionRounds(company);
    const currentRound = this.getCandidateRound(company.id, student);

    if (currentRound === 'Placed') {
      this.showToast(`${student.name} is already Placed & Offered at ${company.companyName}.`);
      return;
    }
    if (currentRound === 'Rejected') {
      this.showToast(`${student.name} is currently Rejected. Use Reconsider to restore.`);
      return;
    }

    const currentIndex = rounds.indexOf(currentRound);
    if (currentIndex === -1) {
      this.setCandidateRound(company.id, student.id, rounds[0]);
      this.showToast(`Assigned ${student.name} to Round 1: ${rounds[0]}.`);
    } else if (currentIndex < rounds.length - 1) {
      const nextRound = rounds[currentIndex + 1];
      this.setCandidateRound(company.id, student.id, nextRound);
      this.showToast(`Advanced ${student.name} to Round ${currentIndex + 2}: ${nextRound}!`);
    } else {
      // Reached HR Round! Promoting from here marks candidate as placed
      this.markStudentPlaced(student, company);
      this.setCandidateRound(company.id, student.id, 'Placed');
    }
    this.saveAllDataToStorage();
  }

  demoteCandidateRound(student: Student, company: CompanyDrive) {
    const rounds = this.getSelectionRounds(company);
    const currentRound = this.getCandidateRound(company.id, student);

    if (currentRound === 'Placed') {
      this.unplaceStudent(student, company);
      const lastRound = rounds[rounds.length - 1];
      this.setCandidateRound(company.id, student.id, lastRound);
      this.showToast(`Reverted ${student.name} from Placed back to Round ${rounds.length}: ${lastRound}.`);
      this.saveAllDataToStorage();
      return;
    }
    if (currentRound === 'Rejected') {
      this.reconsiderCandidate(student, company);
      return;
    }

    const currentIndex = rounds.indexOf(currentRound);
    if (currentIndex > 0) {
      const prevRound = rounds[currentIndex - 1];
      this.setCandidateRound(company.id, student.id, prevRound);
      this.showToast(`Moved ${student.name} back to Round ${currentIndex}: ${prevRound}.`);
      this.saveAllDataToStorage();
    } else {
      this.showToast(`${student.name} is already at the first round (${rounds[0]}).`);
    }
  }

  markCandidatePlaced(student: Student, company: CompanyDrive) {
    this.markStudentPlaced(student, company);
    this.setCandidateRound(company.id, student.id, 'Placed');
    this.saveAllDataToStorage();
  }

  rejectCandidate(student: Student, company: CompanyDrive) {
    if (this.getCandidateRound(company.id, student) === 'Placed') {
      this.unplaceStudent(student, company);
    }
    this.setCandidateRound(company.id, student.id, 'Rejected');
    this.saveAllDataToStorage();
    this.showToast(`Marked ${student.name} as Rejected for ${company.companyName}.`);
  }

  reconsiderCandidate(student: Student, company: CompanyDrive) {
    const rounds = this.getSelectionRounds(company);
    this.setCandidateRound(company.id, student.id, rounds[0]);
    this.saveAllDataToStorage();
    this.showToast(`Restored ${student.name} back to Round 1: ${rounds[0]}.`);
  }

  getStudentRoundStatus(companyId: string, studentId: string): string {
    const student = this.masterStudentPool.find(s => s.id === studentId);
    if (student) {
      return this.getCandidateRound(companyId, student);
    }
    return this.studentRoundStatusMap[companyId]?.[studentId] || 'Online Aptitude Test';
  }

  advanceStudentRound(student: Student, company: CompanyDrive) {
    this.promoteCandidateRound(student, company);
  }

  // Modal Candidate Multi-Select
  isModalCandidateSelected(id: string): boolean {
    return this.modalSelectedCandidateIds.includes(id);
  }

  toggleModalCandidateSelection(id: string) {
    if (this.isModalCandidateSelected(id)) {
      this.modalSelectedCandidateIds = this.modalSelectedCandidateIds.filter(x => x !== id);
    } else {
      this.modalSelectedCandidateIds.push(id);
    }
  }

  toggleSelectAllModalCandidates() {
    const currentList = this.getFilteredModalPipelineCandidates();
    const allSelected = currentList.length > 0 && currentList.every(c => this.isModalCandidateSelected(c.id));
    if (allSelected) {
      const idsToRemove = new Set(currentList.map(c => c.id));
      this.modalSelectedCandidateIds = this.modalSelectedCandidateIds.filter(id => !idsToRemove.has(id));
    } else {
      currentList.forEach(c => {
        if (!this.isModalCandidateSelected(c.id)) {
          this.modalSelectedCandidateIds.push(c.id);
        }
      });
    }
  }

  isAllModalCandidatesSelected(): boolean {
    const currentList = this.getFilteredModalPipelineCandidates();
    return currentList.length > 0 && currentList.every(c => this.isModalCandidateSelected(c.id));
  }

  bulkAdvanceSelectedCandidates() {
    if (!this.activeModalDrive || this.modalSelectedCandidateIds.length === 0) return;
    const drive = this.activeModalDrive;
    let advancedCount = 0;
    this.modalSelectedCandidateIds.forEach(id => {
      const student = this.masterStudentPool.find(s => s.id === id);
      if (student) {
        this.promoteCandidateRound(student, drive);
        advancedCount++;
      }
    });
    this.modalSelectedCandidateIds = [];
    this.showToast(`Advanced ${advancedCount} candidate(s) to their next selection round!`);
  }

  bulkDemoteSelectedCandidates() {
    if (!this.activeModalDrive || this.modalSelectedCandidateIds.length === 0) return;
    const drive = this.activeModalDrive;
    let demotedCount = 0;
    this.modalSelectedCandidateIds.forEach(id => {
      const student = this.masterStudentPool.find(s => s.id === id);
      if (student) {
        this.demoteCandidateRound(student, drive);
        demotedCount++;
      }
    });
    this.modalSelectedCandidateIds = [];
    this.showToast(`Moved ${demotedCount} candidate(s) back to their previous round.`);
  }

  bulkMarkSelectedCandidatesPlaced() {
    if (!this.activeModalDrive || this.modalSelectedCandidateIds.length === 0) return;
    const drive = this.activeModalDrive;
    let placedCount = 0;
    this.modalSelectedCandidateIds.forEach(id => {
      const student = this.masterStudentPool.find(s => s.id === id);
      if (student) {
        this.markCandidatePlaced(student, drive);
        placedCount++;
      }
    });
    this.modalSelectedCandidateIds = [];
    this.showToast(`🎉 Successfully marked ${placedCount} candidate(s) as Placed & Issued Offers!`);
  }

  bulkRejectSelectedCandidates() {
    if (!this.activeModalDrive || this.modalSelectedCandidateIds.length === 0) return;
    const drive = this.activeModalDrive;
    let rejectedCount = 0;
    this.modalSelectedCandidateIds.forEach(id => {
      const student = this.masterStudentPool.find(s => s.id === id);
      if (student) {
        this.rejectCandidate(student, drive);
        rejectedCount++;
      }
    });
    this.modalSelectedCandidateIds = [];
    this.showToast(`Marked ${rejectedCount} candidate(s) as Rejected.`);
  }

  getRoundCandidatesCount(company: CompanyDrive, roundName: string): number {
    const pool = this.getDriveCandidates(company);
    if (roundName === 'All') return pool.length;
    if (roundName === 'Placed') {
      return pool.filter(s => this.getCandidateRound(company.id, s) === 'Placed').length;
    }
    if (roundName === 'Rejected') {
      return pool.filter(s => this.getCandidateRound(company.id, s) === 'Rejected').length;
    }
    return pool.filter(s => this.getCandidateRound(company.id, s) === roundName).length;
  }

  setActiveModalRoundFilter(round: string) {
    this.activeModalRoundFilter = round;
    this.selectedModalCurrentPage = 1;
  }

  getFilteredModalPipelineCandidates(): Student[] {
    if (!this.activeModalDrive) return [];
    let list = this.getDriveCandidates(this.activeModalDrive);

    if (this.activeModalRoundFilter !== 'All') {
      if (this.activeModalRoundFilter === 'Placed') {
        list = list.filter(s => this.getCandidateRound(this.activeModalDrive!.id, s) === 'Placed');
      } else if (this.activeModalRoundFilter === 'Rejected') {
        list = list.filter(s => this.getCandidateRound(this.activeModalDrive!.id, s) === 'Rejected');
      } else {
        list = list.filter(s => this.getCandidateRound(this.activeModalDrive!.id, s) === this.activeModalRoundFilter);
      }
    }

    if (this.placeModalSearchQuery && this.placeModalSearchQuery.trim()) {
      const q = this.placeModalSearchQuery.toLowerCase().trim();
      list = list.filter(s =>
        s.name.toLowerCase().includes(q) ||
        s.roll.toLowerCase().includes(q) ||
        s.branch.toLowerCase().includes(q)
      );
    }

    return list;
  }

  getPaginatedModalPipelineCandidates(): Student[] {
    const list = this.getFilteredModalPipelineCandidates();
    const startIndex = (this.selectedModalCurrentPage - 1) * this.selectedModalPageSize;
    return list.slice(startIndex, startIndex + this.selectedModalPageSize);
  }

  get modalPipelineTotalPages(): number {
    return Math.ceil(this.getFilteredModalPipelineCandidates().length / this.selectedModalPageSize) || 1;
  }

  setModalPipelinePage(page: number) {
    if (page >= 1 && page <= this.modalPipelineTotalPages) {
      this.selectedModalCurrentPage = page;
    }
  }

  // ----------------------------------------------------
  // PAGINATION HELPER METHODS
  // ----------------------------------------------------
  getCompanyPage(companyId: string): number {
    return this.companyCurrentPage[companyId] || 1;
  }

  setCompanyPage(companyId: string, page: number) {
    this.companyCurrentPage[companyId] = page;
  }

  getPaginatedMatchedStudents(company: CompanyDrive): Student[] {
    const matched = this.getMatchedStudents(company);
    const currentPage = this.getCompanyPage(company.id);
    const startIndex = (currentPage - 1) * this.companyPageSize;
    return matched.slice(startIndex, startIndex + this.companyPageSize);
  }

  getCompanyTotalPages(company: CompanyDrive): number {
    const count = this.getMatchedStudents(company).length;
    return Math.ceil(count / this.companyPageSize) || 1;
  }

  getPaginatedDriveRecords(): CompanyDrive[] {
    const filtered = this.getFilteredDriveRecords();
    const startIndex = (this.drivesCurrentPage - 1) * this.drivesPageSize;
    return filtered.slice(startIndex, startIndex + this.drivesPageSize);
  }

  get drivesTotalPages(): number {
    const count = this.getFilteredDriveRecords().length;
    return Math.ceil(count / this.drivesPageSize) || 1;
  }

  setDrivesPage(page: number) {
    if (page >= 1 && page <= this.drivesTotalPages) {
      this.drivesCurrentPage = page;
    }
  }

  getFilteredModalCandidates(): Student[] {
    if (!this.placeModalSearchQuery) {
      return this.selectedCandidatesForModal;
    }
    const q = this.placeModalSearchQuery.toLowerCase();
    return this.selectedCandidatesForModal.filter(s =>
      s.name.toLowerCase().includes(q) ||
      s.roll.toLowerCase().includes(q) ||
      s.branch.toLowerCase().includes(q)
    );
  }

  getPaginatedModalCandidates(): Student[] {
    const filtered = this.getFilteredModalCandidates();
    const startIndex = (this.selectedModalCurrentPage - 1) * this.selectedModalPageSize;
    return filtered.slice(startIndex, startIndex + this.selectedModalPageSize);
  }

  get modalCandidatesTotalPages(): number {
    return Math.ceil(this.getFilteredModalCandidates().length / this.selectedModalPageSize) || 1;
  }

  setModalCandidatesPage(page: number) {
    if (page >= 1 && page <= this.modalCandidatesTotalPages) {
      this.selectedModalCurrentPage = page;
    }
  }

  // ----------------------------------------------------
  // CHECKBOX SELECTION LOGIC
  // ----------------------------------------------------
  isStudentSelected(companyId: string, studentId: string): boolean {
    return !!(this.selectedCandidatesMap[companyId] && this.selectedCandidatesMap[companyId][studentId]);
  }

  toggleStudentSelection(companyId: string, studentId: string) {
    if (!this.selectedCandidatesMap[companyId]) {
      this.selectedCandidatesMap[companyId] = {};
    }
    this.selectedCandidatesMap[companyId][studentId] = !this.selectedCandidatesMap[companyId][studentId];
  }

  getSelectedCount(company: CompanyDrive): number {
    const matched = this.getMatchedStudents(company);
    if (!this.selectedCandidatesMap[company.id]) return 0;
    return matched.filter(s => this.selectedCandidatesMap[company.id][s.id]).length;
  }

  isAllSelected(company: CompanyDrive): boolean {
    const matched = this.getMatchedStudents(company);
    if (matched.length === 0) return false;
    return this.getSelectedCount(company) === matched.length;
  }

  selectAllForCompany(company: CompanyDrive, select: boolean) {
    const matched = this.getMatchedStudents(company);
    if (!this.selectedCandidatesMap[company.id]) {
      this.selectedCandidatesMap[company.id] = {};
    }
    matched.forEach(s => {
      this.selectedCandidatesMap[company.id][s.id] = select;
    });
  }

  toggleSelectAll(company: CompanyDrive, event: any) {
    const checked = event.target.checked;
    this.selectAllForCompany(company, checked);
  }

  // ----------------------------------------------------
  // PLACEMENT PROCESS & DATA CONSISTENCY LOGIC
  // ----------------------------------------------------
  getPlacedStudentsForDrive(company: CompanyDrive): Student[] {
    return this.masterStudentPool.filter(s =>
      s.placementStatus === 'Placed' &&
      (s.companyPlaced?.toLowerCase() === company.companyName.toLowerCase() || company.placedStudentIds.includes(s.id))
    );
  }

  getEligibleUnplacedCandidatesForDrive(company: CompanyDrive): Student[] {
    return this.getMatchedStudents(company);
  }

  markStudentPlaced(student: Student, company: CompanyDrive) {
    student.placementStatus = 'Placed';
    student.companyPlaced = company.companyName;
    student.ctcOffered = `${company.packageLpa} LPA`;
    student.offerDate = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

    if (!company.placedStudentIds.includes(student.id)) {
      company.placedStudentIds.push(student.id);
    }
    company.offers = company.placedStudentIds.length;

    if (this.showSelectedCandidatesModal && this.activeModalDrive?.id === company.id) {
      this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    }

    this.saveAllDataToStorage();
    this.showToast(`🎉 ${student.name} marked as PLACED at ${company.companyName} (${company.packageLpa} LPA)!`);
  }

  unplaceStudent(student: Student, company: CompanyDrive) {
    student.placementStatus = 'Unplaced';
    student.companyPlaced = undefined;
    student.ctcOffered = undefined;
    student.offerDate = undefined;

    company.placedStudentIds = company.placedStudentIds.filter(id => id !== student.id);
    company.offers = company.placedStudentIds.length;

    if (this.showSelectedCandidatesModal && this.activeModalDrive?.id === company.id) {
      this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    }

    this.saveAllDataToStorage();
    this.showToast(`Reverted placement status for ${student.name}. Candidate is now available in unplaced pool.`);
  }

  selectedStudentToPlaceIds: string[] = [];
  quickPlaceSearchQuery: string = '';
  isQuickPlaceDropdownOpen: boolean = false;

  toggleQuickPlaceDropdown() {
    this.isQuickPlaceDropdownOpen = !this.isQuickPlaceDropdownOpen;
  }

  getFilteredUnplacedCandidatesForDrive(company: CompanyDrive): Student[] {
    const pool = this.getEligibleUnplacedCandidatesForDrive(company);
    if (!this.quickPlaceSearchQuery) return pool;
    const q = this.quickPlaceSearchQuery.toLowerCase().trim();
    return pool.filter(s =>
      s.name.toLowerCase().includes(q) ||
      s.roll.toLowerCase().includes(q) ||
      s.branch.toLowerCase().includes(q)
    );
  }

  isStudentSelectedForPlacement(id: string): boolean {
    return this.selectedStudentToPlaceIds.includes(id);
  }

  toggleStudentForPlacement(id: string) {
    if (this.isStudentSelectedForPlacement(id)) {
      this.selectedStudentToPlaceIds = this.selectedStudentToPlaceIds.filter(x => x !== id);
    } else {
      this.selectedStudentToPlaceIds.push(id);
    }
  }

  toggleSelectAllUnplacedForPlacement(company: CompanyDrive) {
    const candidates = this.getFilteredUnplacedCandidatesForDrive(company);
    const allSelected = candidates.length > 0 && candidates.every(c => this.isStudentSelectedForPlacement(c.id));
    if (allSelected) {
      const candidateIds = candidates.map(c => c.id);
      this.selectedStudentToPlaceIds = this.selectedStudentToPlaceIds.filter(id => !candidateIds.includes(id));
    } else {
      candidates.forEach(c => {
        if (!this.isStudentSelectedForPlacement(c.id)) {
          this.selectedStudentToPlaceIds.push(c.id);
        }
      });
    }
  }

  placeSelectedStudentsFromModal() {
    if (this.selectedStudentToPlaceIds.length === 0 || !this.activeModalDrive) return;
    const count = this.selectedStudentToPlaceIds.length;
    this.selectedStudentToPlaceIds.forEach(id => {
      const student = this.masterStudentPool.find(s => s.id === id);
      if (student && this.activeModalDrive) {
        this.markStudentPlaced(student, this.activeModalDrive);
      }
    });
    this.selectedStudentToPlaceIds = [];
    this.isQuickPlaceDropdownOpen = false;
    this.quickPlaceSearchQuery = '';
    this.showToast(`🎉 Successfully marked ${count} candidate(s) as Placed & Issued Offers!`);
  }

  openAddModal() {
    this.showAddModal = true;
  }

  closeAddModal() {
    this.showAddModal = false;
  }

  submitNewRequirement() {
    if (!this.newForm.companyName) return;

    const tiers: string[] = [];
    if (this.newForm.tier1) tiers.push('Tier 1');
    if (this.newForm.tier2) tiers.push('Tier 2');
    if (this.newForm.tier3) tiers.push('Tier 3');

    const newCompany: CompanyDrive = {
      id: 'c_' + Date.now(),
      companyName: this.newForm.companyName,
      logo: 'https://logo.clearbit.com/' + this.newForm.companyName.toLowerCase().replace(/\s+/g, '') + '.com',
      industry: this.newForm.industry,
      packageLpa: this.newForm.packageLpa,
      roles: this.newForm.roles,
      driveDate: this.newForm.driveDate,
      driveMode: this.newForm.driveMode,
      venue: this.newForm.venue,
      minCgpa: this.newForm.minCgpa,
      tierCutoffs: tiers,
      branchFilter: this.newForm.branchFilter,
      batchFilter: this.newForm.batchFilter,
      minProveYuScore: this.newForm.minProveYuScore,
      status: 'Scheduled',
      isExpanded: true,
      mailSent: false,
      attended: 20,
      offers: 0,
      placedStudentIds: []
    };

    this.companies.forEach(c => c.isExpanded = false);
    this.companies.unshift(newCompany);
    this.selectAllForCompany(newCompany, true);
    this.closeAddModal();

    const matchedCount = this.getMatchedStudents(newCompany).length;
    this.showToast(`Drive "${newCompany.companyName}" added! Auto-matched ${matchedCount} eligible candidates.`);
  }

  openAddDriveModal() {
    this.showAddDriveModal = true;
  }

  closeAddDriveModal() {
    this.showAddDriveModal = false;
  }

  submitNewDrive() {
    if (!this.newDriveForm.company) return;

    let packageVal = 7.0;
    if (this.newDriveForm.ctcOffered) {
      const parsed = parseFloat(this.newDriveForm.ctcOffered.replace(/[^\d.]/g, ''));
      if (!isNaN(parsed)) packageVal = parsed;
    }

    const newDrive: CompanyDrive = {
      id: 'c_' + Date.now(),
      companyName: this.newDriveForm.company,
      logo: 'https://logo.clearbit.com/' + this.newDriveForm.company.toLowerCase().replace(/\s+/g, '') + '.com',
      industry: this.newDriveForm.industry,
      packageLpa: packageVal,
      roles: this.newDriveForm.roles,
      driveDate: this.newDriveForm.date,
      driveMode: this.newDriveForm.driveMode,
      venue: this.newDriveForm.venue,
      minCgpa: this.newDriveForm.minCgpa,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'All Branches',
      batchFilter: '2024 Batch',
      minProveYuScore: this.newDriveForm.minProveYuScore,
      status: this.newDriveForm.status,
      isExpanded: false,
      mailSent: false,
      attended: this.newDriveForm.attended,
      offers: this.newDriveForm.offers,
      placedStudentIds: []
    };

    this.companies.unshift(newDrive);
    this.saveCompaniesToStorage();
    this.closeAddDriveModal();
    this.showToast(`Logged corporate drive for "${newDrive.companyName}" successfully!`);
  }

  openEditDriveModal(drive: CompanyDrive) {
    this.editingDrive = drive;
    this.editDriveForm = {
      id: drive.id,
      company: drive.companyName,
      industry: drive.industry,
      date: drive.driveDate,
      status: drive.status,
      attended: drive.attended,
      offers: drive.offers,
      venue: drive.venue,
      roles: drive.roles,
      ctcOffered: `${drive.packageLpa} LPA`,
      packageLpa: drive.packageLpa
    };
    this.showEditDriveModal = true;
  }

  closeEditDriveModal() {
    this.showEditDriveModal = false;
    this.editingDrive = null;
  }

  submitEditDrive() {
    if (!this.editingDrive) return;

    this.editingDrive.companyName = this.editDriveForm.company;
    this.editingDrive.industry = this.editDriveForm.industry || this.editingDrive.industry;
    this.editingDrive.driveDate = this.editDriveForm.date;
    this.editingDrive.status = this.editDriveForm.status;
    this.editingDrive.attended = this.editDriveForm.attended;
    this.editingDrive.venue = this.editDriveForm.venue;
    this.editingDrive.roles = this.editDriveForm.roles;

    if (this.editDriveForm.ctcOffered) {
      const parsed = parseFloat(this.editDriveForm.ctcOffered.replace(/[^\d.]/g, ''));
      if (!isNaN(parsed)) {
        this.editingDrive.packageLpa = parsed;
      }
    }

    this.saveCompaniesToStorage();
    this.closeEditDriveModal();
    this.showToast(`Updated drive metrics for "${this.editDriveForm.company}" successfully!`);
  }

  openSelectedCandidatesModal(title: string, company: CompanyDrive) {
    if (company && company.selectionRounds && company.selectionRounds.length > 0) {
      const unique: string[] = [];
      let hrSeen = false;
      for (const r of company.selectionRounds) {
        const norm = r.includes('HR') ? 'HR Round' : r;
        if (norm === 'HR Round') {
          if (!hrSeen) {
            unique.push(norm);
            hrSeen = true;
          }
        } else if (!unique.includes(norm)) {
          unique.push(norm);
        }
      }
      company.selectionRounds = unique;
    }
    this.activeModalDrive = company;
    this.activeModalTitle = title || 'Candidate Selection & Placement Pipeline';
    this.activeModalSubtitle = `Selection rounds & offer management for ${company.companyName} (${company.packageLpa} LPA)`;
    this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    this.activeModalRoundFilter = 'All';
    this.selectedModalCurrentPage = 1;
    this.selectedStudentToPlaceId = '';
    this.selectedStudentToPlaceIds = [];
    this.modalSelectedCandidateIds = [];
    this.quickPlaceSearchQuery = '';
    this.isQuickPlaceDropdownOpen = false;
    this.placeModalSearchQuery = '';
    this.showSelectedCandidatesModal = true;
  }

  isModalFullscreen = false;

  toggleModalFullscreen() {
    this.isModalFullscreen = !this.isModalFullscreen;
  }

  closeSelectedCandidatesModal() {
    this.showSelectedCandidatesModal = false;
    this.isModalFullscreen = false;
    this.activeModalDrive = null;
    this.selectedStudentToPlaceIds = [];
    this.modalSelectedCandidateIds = [];
    this.quickPlaceSearchQuery = '';
    this.isQuickPlaceDropdownOpen = false;
  }

  exportSelectedCsv(company: CompanyDrive) {
    const matched = this.getMatchedStudents(company);
    const selected = matched.filter(s => this.isStudentSelected(company.id, s.id));

    if (selected.length === 0) {
      this.showToast('Please select at least one candidate to export.');
      return;
    }

    const headers = ['Roll Number', 'Name', 'Email', 'Phone', 'CGPA', 'Tier', 'ProveYu Score', 'Branch', 'Placement Status'];
    const rows = selected.map(s => [s.roll, s.name, s.email, s.phone, s.cgpa, s.tier, s.proveYuScore, s.branch, s.placementStatus]);

    const csvContent = 'data:text/csv;charset=utf-8,'
      + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `${company.companyName.replace(/\s+/g, '_')}_Eligible_Candidates.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    this.showToast(`Exported CSV for ${selected.length} candidates!`);
  }

  openMailModal(company: CompanyDrive) {
    const selectedCount = this.getSelectedCount(company);
    if (selectedCount === 0) {
      this.showToast('Please select at least one student to send email.');
      return;
    }

    this.activeCompanyForMail = company;
    this.selectedMailTemplate = 'invite';
    this.activeMailTab = 'compose';
    this.mailForm.subject = `[Campus Drive Alert] ${company.companyName} Recruitment Drive - ${company.packageLpa} LPA`;
    this.mailForm.body = `Dear {Student Name},\n\nCongratulations! You have been auto-matched and shortlisted for the upcoming placement drive with ${company.companyName}.\n\nDrive Details:\n- Offered Package: ${company.packageLpa} LPA\n- Roles Offered: ${company.roles}\n- Drive Date & Time: ${company.driveDate}\n- Mode & Venue: ${company.driveMode} (${company.venue})\n\nEligibility Criteria Met:\n- Minimum CGPA: ${company.minCgpa}+\n- Eligible ProveYu Tiers: ${company.tierCutoffs.join(', ')}\n\nPlease report to the designated venue on time with your updated Resume and ProveYu Admit Card.\n\nBest Regards,\nTraining & Placement Office (TPO)\nMGM CET, Kamothe`;
    this.showMailModal = true;
  }

  closeMailModal() {
    this.showMailModal = false;
    this.activeCompanyForMail = null;
  }

  insertMergeTag(tag: string) {
    this.mailForm.body += ` ${tag}`;
  }

  onMailTemplateChange(templateKey: string) {
    this.selectedMailTemplate = templateKey;
    if (!this.activeCompanyForMail) return;
    const company = this.activeCompanyForMail;

    if (templateKey === 'invite') {
      this.mailForm.subject = `[Campus Drive Alert] ${company.companyName} Recruitment Drive - ${company.packageLpa} LPA`;
      this.mailForm.body = `Dear {Student Name},\n\nCongratulations! You have been auto-matched and shortlisted for the upcoming placement drive with ${company.companyName}.\n\nDrive Details:\n- Offered Package: ${company.packageLpa} LPA\n- Roles Offered: ${company.roles}\n- Drive Date & Time: ${company.driveDate}\n- Mode & Venue: ${company.driveMode} (${company.venue})\n\nEligibility Criteria Met:\n- Minimum CGPA: ${company.minCgpa}+\n- Eligible ProveYu Tiers: ${company.tierCutoffs.join(', ')}\n\nPlease report to the designated venue on time with your updated Resume and ProveYu Admit Card.\n\nBest Regards,\nTraining & Placement Office (TPO)\nMGM CET, Kamothe`;
    } else if (templateKey === 'round') {
      this.mailForm.subject = `[Selection Process Update] ${company.companyName} Next Round Schedule`;
      this.mailForm.body = `Dear {Student Name},\n\nWe are pleased to inform you that you have cleared the initial evaluations for ${company.companyName}.\n\nNext Round Details:\n- Target Designation: ${company.roles}\n- Package CTC: ${company.packageLpa} LPA\n- Schedule Date: ${company.driveDate}\n- Venue / Link: ${company.venue}\n\nPlease be prepared for the upcoming interview round.\n\nBest Regards,\nPlacement Cell`;
    } else if (templateKey === 'offer') {
      this.mailForm.subject = `[Congratulations!] Official Placement Offer Letter from ${company.companyName}`;
      this.mailForm.body = `Dear {Student Name},\n\nHeartiest Congratulations! On behalf of ${company.companyName} and MGM CET, we are thrilled to announce that you have been selected for the position of ${company.roles}.\n\nOffer Highlights:\n- Fixed CTC Package: ${company.packageLpa} LPA\n- Joining Date: To be communicated\n\nPlease find your formal offer details in your ProveYu Candidate Portal.\n\nWarm Regards,\nHead of TPO`;
    }
  }

  sendBulkMails() {
    if (!this.activeCompanyForMail) return;

    const company = this.activeCompanyForMail;
    const count = this.getSelectedCount(company);

    company.mailSent = true;
    company.mailSentDate = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    company.mailSentCount = count;
    if (company.status === 'Scheduled') {
      company.status = 'Ongoing';
    }

    this.saveCompaniesToStorage();
    this.closeMailModal();
    this.showToast(`🚀 Invitation emails sent successfully to ${count} candidates for ${company.companyName}!`);
  }

  showToast(msg: string) {
    if (this.showSelectedCandidatesModal) {
      return;
    }
    this.notificationService.showSuccess(msg);
  }
}

