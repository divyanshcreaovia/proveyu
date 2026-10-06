import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

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
  minProveYuScore: number;
  status: 'Scheduled' | 'Ongoing' | 'Completed' | 'Awaiting Schedule';
  isExpanded: boolean;
  mailSent: boolean;
  mailSentDate?: string;
  mailSentCount?: number;
  attended: number;
  offers: number;
  placedStudentIds: string[];
}

@Component({
  selector: 'app-college-placements',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './placements.html',
  styleUrl: './placements.scss',
})
export class Placements {
  // Navigation Tab State (Removed Student Placement Status Roster tab as requested)
  activeTab: 'requirements' | 'drives_tracker' = 'requirements';

  // Search & Filter State
  searchCompanyQuery: string = '';
  selectedModeFilter: string = 'All';

  searchDriveQuery: string = '';
  selectedDriveStatusFilter: string = 'All';

  // Modal Display States
  showAddModal: boolean = false;           // Modal 1: Add Company Requirement
  showAddDriveModal: boolean = false;      // Modal 2: Log Corporate Drive & Interview
  showEditDriveModal: boolean = false;     // Edit Corporate Drive Record
  showMailModal: boolean = false;          // Bulk Mail Composer
  showSelectedCandidatesModal: boolean = false; // View & Manage Selected Candidates

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

  // Master Student Pool (25 Rich Candidates with exact tier & CGPA matching)
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

  // Master Company Drives List (Synchronized across both Requirement Drives and Corporate Drives & Interview Tracker)
  companies: CompanyDrive[] = [
    {
      id: 'c1',
      companyName: 'TCS Digital Drive',
      logo: 'https://logo.clearbit.com/tcs.com',
      industry: 'IT Services & Consulting',
      packageLpa: 7.5,
      roles: 'Digital Software Engineer & Cloud Developer',
      driveDate: '2026-10-15',
      driveMode: 'On-Campus',
      venue: 'Main Auditorium & Computer Lab A',
      minCgpa: 7.8,
      tierCutoffs: ['Tier 1', 'Tier 2'],
      branchFilter: 'All Branches',
      batchFilter: '2024 Batch',
      minProveYuScore: 60,
      status: 'Completed',
      isExpanded: true,
      mailSent: true,
      mailSentDate: 'Sep 27, 2026',
      mailSentCount: 15,
      attended: 30,
      offers: 7,
      placedStudentIds: ['1', '5', '6', '7', '9', '11', '12']
    },
    {
      id: 'c2',
      companyName: 'Wipro Turbo Hiring',
      logo: 'https://logo.clearbit.com/wipro.com',
      industry: 'IT Services & Automation',
      packageLpa: 6.5,
      roles: 'Project Engineer & QA Specialist',
      driveDate: '2026-10-22',
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
      placedStudentIds: ['2', '14']
    },
    {
      id: 'c3',
      companyName: 'Amdocs Product Innovation',
      logo: 'https://logo.clearbit.com/amdocs.com',
      industry: 'Product SaaS & Telecom',
      packageLpa: 8.2,
      roles: 'Software Developer (C++ / Java)',
      driveDate: '2026-11-05',
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
      placedStudentIds: ['21']
    },
    {
      id: 'c4',
      companyName: 'Accenture Innovation Drive',
      logo: 'https://logo.clearbit.com/accenture.com',
      industry: 'Global Consulting',
      packageLpa: 5.5,
      roles: 'Associate Software Engineer',
      driveDate: '2026-11-12',
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
      placedStudentIds: []
    },
    {
      id: 'c5',
      companyName: 'Cognizant GenC Next',
      logo: 'https://logo.clearbit.com/cognizant.com',
      industry: 'IT Services & Digital',
      packageLpa: 6.8,
      roles: 'Full Stack & AI Engineer',
      driveDate: '2026-11-18',
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
      placedStudentIds: []
    },
    {
      id: 'c6',
      companyName: 'Infosys Specialist Programmer',
      logo: 'https://logo.clearbit.com/infosys.com',
      industry: 'Product & Cloud Solutions',
      packageLpa: 9.5,
      roles: 'Specialist Programmer',
      driveDate: '2026-11-25',
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
      placedStudentIds: []
    }
  ];

  // Checkbox selection map for manual overrides
  selectedCandidatesMap: { [companyId: string]: { [studentId: string]: boolean } } = {
    'c1': { '3': true, '4': true, '8': true, '10': true, '13': true, '16': true, '18': true, '20': true },
    'c2': { '3': true, '8': true, '13': true, '17': true, '18': true },
    'c3': { '3': true, '8': true, '13': true }
  };

  // Modal 1 Form Model: Add Company Requirement
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

  // Modal 2 Form Model: Log Corporate Drive & Interview
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

  // Edit Drive Form Model
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

  // Mail Composer Model
  mailForm = {
    subject: '',
    body: ''
  };
  activeCompanyForMail: CompanyDrive | null = null;

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
      if (student.placementStatus !== 'Unplaced') return false;
      if (student.cgpa < company.minCgpa) return false;
      if (!company.tierCutoffs.includes(student.tier)) return false;
      if (company.minProveYuScore && student.proveYuScore < company.minProveYuScore) return false;
      if (company.branchFilter && company.branchFilter !== 'All Branches') {
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
      return matchesSearch && matchesMode;
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

  toggleExpand(company: CompanyDrive) {
    company.isExpanded = !company.isExpanded;
    if (company.isExpanded && !this.selectedCandidatesMap[company.id]) {
      this.selectAllForCompany(company, true);
    }
  }

  // ----------------------------------------------------
  // PAGINATION HELPER METHODS (10 ROWS PER PAGE EXPLICIT)
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

  // Drives Pagination (10 rows per page)
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

  // Modal Candidates Pagination (10 rows per page)
  getPaginatedModalCandidates(): Student[] {
    const startIndex = (this.selectedModalCurrentPage - 1) * this.selectedModalPageSize;
    return this.selectedCandidatesForModal.slice(startIndex, startIndex + this.selectedModalPageSize);
  }

  get modalCandidatesTotalPages(): number {
    return Math.ceil(this.selectedCandidatesForModal.length / this.selectedModalPageSize) || 1;
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
  // Get placed students for a specific company drive
  getPlacedStudentsForDrive(company: CompanyDrive): Student[] {
    return this.masterStudentPool.filter(s =>
      s.placementStatus === 'Placed' &&
      (s.companyPlaced?.toLowerCase() === company.companyName.toLowerCase() || company.placedStudentIds.includes(s.id))
    );
  }

  // Get eligible unplaced candidates that can be placed in modal
  getEligibleUnplacedCandidatesForDrive(company: CompanyDrive): Student[] {
    return this.getMatchedStudents(company);
  }

  // Mark student as PLACED directly from matched roster or modal
  markStudentPlaced(student: Student, company: CompanyDrive) {
    student.placementStatus = 'Placed';
    student.companyPlaced = company.companyName;
    student.ctcOffered = `${company.packageLpa} LPA`;
    student.offerDate = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

    if (!company.placedStudentIds.includes(student.id)) {
      company.placedStudentIds.push(student.id);
    }
    company.offers = company.placedStudentIds.length;

    // Refresh modal list if open
    if (this.showSelectedCandidatesModal && this.activeModalDrive?.id === company.id) {
      this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    }

    this.showToast(`🎉 ${student.name} marked as PLACED at ${company.companyName} (${company.packageLpa} LPA)!`);
  }

  // Revoke/revert student placement status back to Unplaced
  unplaceStudent(student: Student, company: CompanyDrive) {
    student.placementStatus = 'Unplaced';
    student.companyPlaced = undefined;
    student.ctcOffered = undefined;
    student.offerDate = undefined;

    company.placedStudentIds = company.placedStudentIds.filter(id => id !== student.id);
    company.offers = company.placedStudentIds.length;

    // Refresh modal list if open
    if (this.showSelectedCandidatesModal && this.activeModalDrive?.id === company.id) {
      this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    }

    this.showToast(`Reverted placement status for ${student.name}. Candidate is now available in unplaced pool.`);
  }

  // Quick place selected candidate from modal dropdown
  placeSelectedStudentFromModal() {
    if (!this.selectedStudentToPlaceId || !this.activeModalDrive) return;
    const student = this.masterStudentPool.find(s => s.id === this.selectedStudentToPlaceId);
    if (student) {
      this.markStudentPlaced(student, this.activeModalDrive);
      this.selectedStudentToPlaceId = '';
    }
  }

  // ----------------------------------------------------
  // MODAL 1: ADD COMPANY REQUIREMENT (TAB 1)
  // ----------------------------------------------------
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

    this.companies.unshift(newCompany);
    this.selectAllForCompany(newCompany, true);
    this.closeAddModal();

    const matchedCount = this.getMatchedStudents(newCompany).length;
    this.showToast(`Drive "${newCompany.companyName}" added! Auto-matched ${matchedCount} eligible candidates.`);
  }

  // ----------------------------------------------------
  // MODAL 2: LOG CORPORATE DRIVE & INTERVIEW (TAB 2)
  // ----------------------------------------------------
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
    this.closeAddDriveModal();
    this.showToast(`Logged corporate drive for "${newDrive.companyName}" successfully!`);
  }

  // ----------------------------------------------------
  // EDIT CORPORATE DRIVE RECORD MODAL
  // ----------------------------------------------------
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

    this.closeEditDriveModal();
    this.showToast(`Updated drive metrics for "${this.editDriveForm.company}" successfully!`);
  }

  // ----------------------------------------------------
  // VIEW & MANAGE SELECTED CANDIDATES DETAILS MODAL
  // ----------------------------------------------------
  openSelectedCandidatesModal(title: string, company: CompanyDrive) {
    this.activeModalDrive = company;
    this.activeModalTitle = title;
    this.activeModalSubtitle = `Candidates placed / offered by ${company.companyName} (${company.packageLpa} LPA)`;
    this.selectedCandidatesForModal = this.getPlacedStudentsForDrive(company);
    this.selectedModalCurrentPage = 1;
    this.selectedStudentToPlaceId = '';
    this.showSelectedCandidatesModal = true;
  }

  closeSelectedCandidatesModal() {
    this.showSelectedCandidatesModal = false;
    this.activeModalDrive = null;
  }

  // ----------------------------------------------------
  // CSV EXPORT ACTION
  // ----------------------------------------------------
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

  // ----------------------------------------------------
  // MAIL COMPOSER LOGIC
  // ----------------------------------------------------
  openMailModal(company: CompanyDrive) {
    const selectedCount = this.getSelectedCount(company);
    if (selectedCount === 0) {
      this.showToast('Please select at least one student to send email.');
      return;
    }

    this.activeCompanyForMail = company;
    this.mailForm.subject = `[Campus Drive Alert] ${company.companyName} Recruitment Drive - ${company.packageLpa} LPA`;
    this.mailForm.body = `Dear {Student Name},\n\nCongratulations! You have been auto-matched and shortlisted for the upcoming placement drive with ${company.companyName}.\n\n📌 Drive Details:\n• Offered Package: ${company.packageLpa} LPA\n• Roles Offered: ${company.roles}\n• Drive Date & Time: ${company.driveDate}\n• Mode & Venue: ${company.driveMode} (${company.venue})\n\n✅ Eligibility Criteria Met:\n• Minimum CGPA: ${company.minCgpa}+\n• Eligible ProveYu Tiers: ${company.tierCutoffs.join(', ')}\n• Minimum ProveYu Score: ${company.minProveYuScore}+\n\nPlease report to the designated venue on time with your updated Resume and ProveYu Admit Card.\n\nBest Regards,\nTraining & Placement Office (TPO)\nMGM CET, Kamothe`;
    this.showMailModal = true;
  }

  closeMailModal() {
    this.showMailModal = false;
    this.activeCompanyForMail = null;
  }

  insertMergeTag(tag: string) {
    this.mailForm.body += ` ${tag}`;
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

    this.closeMailModal();
    this.showToast(`🚀 Invitation emails sent successfully to ${count} candidates for ${company.companyName}!`);
  }

  showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }
}

