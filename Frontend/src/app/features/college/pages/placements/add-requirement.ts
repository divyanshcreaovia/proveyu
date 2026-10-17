import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface Student {
  id: string;
  roll: string;
  name: string;
  email: string;
  phone: string;
  cgpa: number;
  backlogs?: number;
  gapYears?: number;
  tenthPercentage?: number;
  twelfthPercentage?: number;
  tier: 'Tier 1' | 'Tier 2' | 'Tier 3';
  proveYuScore: number;
  branch: string;
  batch: string;
  placementStatus: 'Unplaced' | 'Placed' | 'Pending';
}

@Component({
  selector: 'app-college-add-requirement',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './add-requirement.html',
  styleUrl: './add-requirement.scss',
})
export class AddRequirement implements OnInit {
  isSubmitted = false;

  newForm = {
    companyName: '',
    companyWebsite: '',
    industry: 'IT Services & Consulting',
    roles: 'Software Development Engineer',
    jobLocation: 'Pune / Mumbai / Hybrid',
    employmentType: 'Full-Time Direct',
    packageLpa: 7.5,
    stipendMonthly: '₹25,000 / month',
    bondDurationMonths: 0,
    driveCategory: 'Dream Drive (5-10 LPA)',
    hrContactName: 'Shruti Varma (Campus Lead)',
    hrContactEmail: 'campus.hiring@company.com',
    hrContactPhone: '+91-9876501234',
    minCgpa: 7.5,
    maxBacklogs: 0,
    maxGapYears: 0,
    min10thPercentage: 60,
    min12thPercentage: 60,
    batchFilter: '2024 Batch',
    degreeFilter: 'B.Tech / B.E. Only',
    minProveYuScore: 65,
    tier1: true,
    tier2: true,
    tier3: false,
    placementEligibility: 'Unplaced Only' as 'Unplaced Only' | 'Allow Placed (Upgrade Offer)' | 'All Candidates',
    selectedBranches: ['Computer Science', 'Information Tech'] as string[],
    branchFilter: 'CS / IT Only',
    driveDate: '2026-11-15',
    registrationDeadline: '2026-11-10',
    driveMode: 'On-Campus' as 'On-Campus' | 'Virtual' | 'Hybrid',
    venue: 'Main Auditorium & Computer Lab 3',
    selectionRounds: ['Online Aptitude Test', 'Coding Assessment', 'Technical Interview Round 1', 'HR Round'] as string[],
    allowPlacedStudents: false
  };

  availableBranches: string[] = [
    'All Students',
    'Computer Science',
    'Information Tech',
    'AI & Data Science',
    'Electronics',
    'Mechanical',
    'Civil',
    'Electrical'
  ];
  branchDropdownOpen: boolean = false;

  availableRounds: string[] = [
    'Online Aptitude Test',
    'Coding Assessment',
    'Group Discussion (GD)',
    'Technical Interview Round 1',
    'Technical Interview Round 2',
    'HR Round'
  ];

  masterStudentPool: Student[] = [
    { id: '1', roll: '2024-CSE-001', name: 'Rahul Sharma', email: 'rahul.s@mgmcet.ac.in', phone: '+91-9876543210', cgpa: 8.5, backlogs: 0, gapYears: 0, tenthPercentage: 88, twelfthPercentage: 86, tier: 'Tier 1', proveYuScore: 82, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '2', roll: '2024-IT-002', name: 'Priya Desai', email: 'priya.d@mgmcet.ac.in', phone: '+91-9876543211', cgpa: 8.8, backlogs: 0, gapYears: 0, tenthPercentage: 91, twelfthPercentage: 89, tier: 'Tier 1', proveYuScore: 78, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '3', roll: '2024-CSE-003', name: 'Arun Patel', email: 'arun.patel@mgmcet.ac.in', phone: '+91-9876543212', cgpa: 8.4, backlogs: 1, gapYears: 0, tenthPercentage: 82, twelfthPercentage: 78, tier: 'Tier 1', proveYuScore: 78, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '4', roll: '2024-CSE-004', name: 'Neha Deshmukh', email: 'neha.d@mgmcet.ac.in', phone: '+91-9876543213', cgpa: 8.2, backlogs: 0, gapYears: 1, tenthPercentage: 79, twelfthPercentage: 75, tier: 'Tier 2', proveYuScore: 72, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '5', roll: '2024-EXTC-005', name: 'Amit Verma', email: 'amit.v@mgmcet.ac.in', phone: '+91-9876543214', cgpa: 7.8, backlogs: 0, gapYears: 0, tenthPercentage: 74, twelfthPercentage: 71, tier: 'Tier 2', proveYuScore: 64, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' },
    { id: '6', roll: '2024-CSE-006', name: 'Divya Nair', email: 'divya.n@mgmcet.ac.in', phone: '+91-9876543215', cgpa: 8.9, backlogs: 0, gapYears: 0, tenthPercentage: 92, twelfthPercentage: 90, tier: 'Tier 1', proveYuScore: 88, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed' },
    { id: '7', roll: '2024-IT-007', name: 'Sanjay Dutt', email: 'sanjay.d@mgmcet.ac.in', phone: '+91-9876543216', cgpa: 8.1, backlogs: 0, gapYears: 0, tenthPercentage: 76, twelfthPercentage: 74, tier: 'Tier 2', proveYuScore: 69, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '8', roll: '2024-IT-008', name: 'Sandhya Kulkarni', email: 'sandhya.k@mgmcet.ac.in', phone: '+91-9876543217', cgpa: 8.6, backlogs: 0, gapYears: 0, tenthPercentage: 85, twelfthPercentage: 83, tier: 'Tier 1', proveYuScore: 85, branch: 'Information Tech', batch: '2024', placementStatus: 'Placed' },
    { id: '9', roll: '2024-EXTC-009', name: 'Meera Iyer', email: 'meera.i@mgmcet.ac.in', phone: '+91-9876543218', cgpa: 8.3, backlogs: 0, gapYears: 0, tenthPercentage: 81, twelfthPercentage: 80, tier: 'Tier 1', proveYuScore: 76, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' },
    { id: '10', roll: '2024-IT-011', name: 'Vikram Reddy', email: 'vikram.r@mgmcet.ac.in', phone: '+91-9876543219', cgpa: 8.1, backlogs: 2, gapYears: 0, tenthPercentage: 72, twelfthPercentage: 68, tier: 'Tier 2', proveYuScore: 68, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '11', roll: '2024-CSE-012', name: 'Aniket Solanki', email: 'aniket.s@mgmcet.ac.in', phone: '+91-9876543220', cgpa: 8.7, backlogs: 0, gapYears: 0, tenthPercentage: 89, twelfthPercentage: 87, tier: 'Tier 1', proveYuScore: 84, branch: 'Computer Science', batch: '2024', placementStatus: 'Placed' },
    { id: '12', roll: '2024-CSE-013', name: 'Pooja Hegde', email: 'pooja.h@mgmcet.ac.in', phone: '+91-9876543221', cgpa: 8.4, backlogs: 0, gapYears: 0, tenthPercentage: 84, twelfthPercentage: 82, tier: 'Tier 1', proveYuScore: 79, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '13', roll: '2024-CSE-015', name: 'Ananya Roy', email: 'ananya.roy@mgmcet.ac.in', phone: '+91-9876543222', cgpa: 9.1, backlogs: 0, gapYears: 0, tenthPercentage: 95, twelfthPercentage: 93, tier: 'Tier 1', proveYuScore: 92, branch: 'Computer Science', batch: '2024', placementStatus: 'Unplaced' },
    { id: '14', roll: '2024-IT-016', name: 'Karan Johar', email: 'karan.j@mgmcet.ac.in', phone: '+91-9876543223', cgpa: 8.5, backlogs: 0, gapYears: 0, tenthPercentage: 86, twelfthPercentage: 84, tier: 'Tier 1', proveYuScore: 81, branch: 'Information Tech', batch: '2024', placementStatus: 'Unplaced' },
    { id: '15', roll: '2024-EXTC-018', name: 'Siddharth Roy', email: 'siddharth.r@mgmcet.ac.in', phone: '+91-9876543224', cgpa: 8.2, backlogs: 0, gapYears: 0, tenthPercentage: 78, twelfthPercentage: 76, tier: 'Tier 2', proveYuScore: 70, branch: 'Electronics', batch: '2024', placementStatus: 'Unplaced' }
  ];

  constructor(private router: Router, private notificationService: NotificationService) {}

  ngOnInit() {
    this.updateBranchFilterSummary();
  }

  toggleBranchDropdown() {
    this.branchDropdownOpen = !this.branchDropdownOpen;
  }

  isBranchSelected(branch: string): boolean {
    if (branch === 'All Students') {
      return this.newForm.selectedBranches.includes('All Students') ||
        this.newForm.selectedBranches.length >= (this.availableBranches.length - 1);
    }
    return this.newForm.selectedBranches.includes(branch);
  }

  toggleBranchSelection(branch: string) {
    if (branch === 'All Students') {
      if (this.isBranchSelected('All Students')) {
        this.newForm.selectedBranches = [];
      } else {
        this.newForm.selectedBranches = [...this.availableBranches];
      }
    } else {
      this.newForm.selectedBranches = this.newForm.selectedBranches.filter(b => b !== 'All Students');
      const index = this.newForm.selectedBranches.indexOf(branch);
      if (index > -1) {
        this.newForm.selectedBranches.splice(index, 1);
      } else {
        this.newForm.selectedBranches.push(branch);
      }
      const nonAllBranches = this.availableBranches.filter(b => b !== 'All Students');
      const allSelected = nonAllBranches.every(b => this.newForm.selectedBranches.includes(b));
      if (allSelected) {
        this.newForm.selectedBranches.push('All Students');
      }
    }
    this.updateBranchFilterSummary();
  }

  selectCsItOnly() {
    this.newForm.selectedBranches = ['Computer Science', 'Information Tech'];
    this.updateBranchFilterSummary();
  }

  selectCoreBranches() {
    this.newForm.selectedBranches = ['Electronics', 'Mechanical', 'Civil', 'Electrical'];
    this.updateBranchFilterSummary();
  }

  selectAllBranches() {
    this.newForm.selectedBranches = [...this.availableBranches];
    this.updateBranchFilterSummary();
  }

  clearBranchSelection() {
    this.newForm.selectedBranches = [];
    this.updateBranchFilterSummary();
  }

  updateBranchFilterSummary() {
    if (this.newForm.selectedBranches.includes('All Students') || this.newForm.selectedBranches.length >= 7) {
      this.newForm.branchFilter = 'All Students / All Branches';
    } else if (this.newForm.selectedBranches.length === 0) {
      this.newForm.branchFilter = 'None Selected';
    } else if (
      this.newForm.selectedBranches.length === 2 &&
      this.newForm.selectedBranches.includes('Computer Science') &&
      this.newForm.selectedBranches.includes('Information Tech')
    ) {
      this.newForm.branchFilter = 'CS / IT Only';
    } else {
      this.newForm.branchFilter = this.newForm.selectedBranches.join(', ');
    }
  }

  toggleRoundSelection(round: string) {
    const isHr = round.includes('HR');
    if (isHr) {
      const existingHrIndex = this.newForm.selectionRounds.findIndex(r => r.includes('HR'));
      if (existingHrIndex > -1) {
        this.newForm.selectionRounds.splice(existingHrIndex, 1);
        return;
      } else {
        this.newForm.selectionRounds.push('HR Round');
        return;
      }
    }
    const index = this.newForm.selectionRounds.indexOf(round);
    if (index > -1) {
      this.newForm.selectionRounds.splice(index, 1);
    } else {
      this.newForm.selectionRounds.push(round);
    }
  }

  isRoundSelected(round: string): boolean {
    if (round.includes('HR')) {
      return this.newForm.selectionRounds.some(r => r.includes('HR'));
    }
    return this.newForm.selectionRounds.includes(round);
  }

  getFormTiers(): string[] {
    const tiers: string[] = [];
    if (this.newForm.tier1) tiers.push('Tier 1');
    if (this.newForm.tier2) tiers.push('Tier 2');
    if (this.newForm.tier3) tiers.push('Tier 3');
    return tiers;
  }

  get placementEligibilitySubtext(): string {
    if (this.newForm.placementEligibility === 'Allow Placed (Upgrade Offer)') {
      return 'Placed & Unplaced Candidates Matching Criteria';
    } else if (this.newForm.placementEligibility === 'All Candidates') {
      return 'All Registered Candidates Matching Criteria';
    }
    return 'Unplaced Candidates Matching Criteria';
  }

  get dynamicMatchedCount(): number {
    return this.masterStudentPool.filter(student => {
      // Filter by placement status eligibility
      if (this.newForm.placementEligibility === 'Unplaced Only' && student.placementStatus !== 'Unplaced') {
        return false;
      }
      
      if (student.cgpa < this.newForm.minCgpa) return false;
      if ((student.backlogs ?? 0) > this.newForm.maxBacklogs) return false;
      if ((student.gapYears ?? 0) > this.newForm.maxGapYears) return false;
      if ((student.tenthPercentage ?? 100) < this.newForm.min10thPercentage) return false;
      if ((student.twelfthPercentage ?? 100) < this.newForm.min12thPercentage) return false;
      if (!this.getFormTiers().includes(student.tier)) return false;
      if (this.newForm.minProveYuScore && student.proveYuScore < this.newForm.minProveYuScore) return false;

      if (this.newForm.selectedBranches.length > 0) {
        const isAllSelected = this.newForm.selectedBranches.includes('All Students') || this.newForm.selectedBranches.includes('All Branches');
        if (!isAllSelected) {
          const studentBranchLower = student.branch.toLowerCase();
          const matchFound = this.newForm.selectedBranches.some(b => {
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
      }
      return true;
    }).length;
  }

  submitRequirement() {
    if (!this.newForm.companyName) return;

    this.updateBranchFilterSummary();

    const newDrive = {
      id: 'c_' + Date.now(),
      companyName: this.newForm.companyName,
      logo: 'https://logo.clearbit.com/' + (this.newForm.companyWebsite || this.newForm.companyName).toLowerCase().replace(/https?:\/\//, '').replace(/\s+/g, '') + '.com',
      industry: this.newForm.industry,
      packageLpa: this.newForm.packageLpa,
      roles: this.newForm.roles,
      hrContactName: this.newForm.hrContactName,
      hrContactEmail: this.newForm.hrContactEmail,
      hrContactPhone: this.newForm.hrContactPhone,
      jobLocation: this.newForm.jobLocation,
      employmentType: this.newForm.employmentType,
      stipendMonthly: this.newForm.stipendMonthly,
      bondDurationMonths: this.newForm.bondDurationMonths,
      driveCategory: this.newForm.driveCategory,
      driveDate: this.newForm.driveDate,
      registrationDeadline: this.newForm.registrationDeadline,
      driveMode: this.newForm.driveMode,
      venue: this.newForm.venue,
      minCgpa: this.newForm.minCgpa,
      maxBacklogs: this.newForm.maxBacklogs,
      maxGapYears: this.newForm.maxGapYears,
      min10thPercentage: this.newForm.min10thPercentage,
      min12thPercentage: this.newForm.min12thPercentage,
      selectedBranches: [...this.newForm.selectedBranches],
      tierCutoffs: this.getFormTiers(),
      placementEligibility: this.newForm.placementEligibility,
      branchFilter: this.newForm.branchFilter,
      batchFilter: this.newForm.batchFilter,
      degreeFilter: this.newForm.degreeFilter,
      minProveYuScore: this.newForm.minProveYuScore,
      selectionRounds: (() => {
        const unique: string[] = [];
        let hrSeen = false;
        for (const r of this.newForm.selectionRounds) {
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
      })(),
      allowPlacedStudents: this.newForm.allowPlacedStudents,
      status: 'Scheduled',
      isExpanded: false,
      mailSent: false,
      attended: 20,
      offers: 0,
      placedStudentIds: []
    };

    // Save to localStorage for persistence across pages
    try {
      const stored = localStorage.getItem('proveyu_college_companies');
      const existing = stored ? JSON.parse(stored) : [];
      existing.unshift(newDrive);
      localStorage.setItem('proveyu_college_companies', JSON.stringify(existing));
    } catch (e) {
      console.error('Error storing drive:', e);
    }

    this.isSubmitted = true;
    this.notificationService.showSuccess(`Placement Requirement Published Successfully! Auto-matched ${this.dynamicMatchedCount} eligible candidates.`);
    setTimeout(() => {
      this.router.navigate(['/college/placements']);
    }, 1200);
  }
}
