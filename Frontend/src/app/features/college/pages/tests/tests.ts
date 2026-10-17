import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface TestSection {
  name: string;              // 'Coding' | 'Aptitude' | 'Communication'
  maxScore: number;
}

export interface Test {
  id: string;
  testKey: string;           // Admin-issued key
  title: string;             // Admin paper title, read-only
  sections: TestSection[];   // Section breakdown, read-only
  tier1Cutoff: number;       // Cutoff for Tier 1
  tier2Cutoff: number;       // Cutoff for Tier 2
  date: string;
  startTime: string;
  durationMinutes: number;   // Duration in minutes
  venue: string;
  eligibleBatches: string[];
  invigilators: string[];
  status: 'Completed';       // All tests published to TPO are completed evaluations

  totalRegistered: number;
  totalAttended: number;
  totalAbsent: number;
}

export interface TestResult {
  studentId: string;
  rollNumber: string;
  name: string;
  email: string;
  branch: string;
  overallScore: number;
  sectionScores: Record<string, number>;  // { Coding: 28, Aptitude: 26, Communication: 28 }
  tier: 'Tier 1' | 'Tier 2' | 'Tier 3';
  passFail: 'Pass' | 'Fail';

  // Cross-module signals — computed live
  listedOnPlatform: boolean;       // Has public ProveYu Passport published
  shortlistedByCompanies: number;  // Count of active Drive requirements matched
  placementStatus: 'Placed' | 'Unplaced';
  needsTraining: boolean;          // True if any sectionScore < section tier2 cutoff
  trainingReason?: string;         // e.g. "Coding 12/30 — below Tier 2 threshold"
}

@Component({
  selector: 'app-college-tests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tests.html',
  styleUrl: './tests.scss',
})
export class Tests {
  constructor(private notificationService: NotificationService) {}
  // Screen View State: 'list' (Overview) | 'detail' (Drill-Down)
  currentView: 'list' | 'detail' = 'list';
  activeTest: Test | null = null;

  // List View Filter & Search
  searchQuery: string = '';

  // Detail View Result Table Filters
  resultSearchQuery: string = '';
  selectedPassFailFilter: string = 'All';
  selectedResultTierFilter: string = 'All';
  onlyNeedsTrainingFilter: boolean = false;
  selectedPlacementStatusFilter: string = 'All';
  selectedListedFilter: string = 'All';

  // Modal Display Controls
  showSendFacultyModal: boolean = false;
  toastMessage: string | null = null;

  facultyForm = {
    email: 'tpo.training@mgmcet.ac.in',
    subject: '[Action Required] Remedial Batch List from Test Evaluation',
    notes: 'Please assign remedial training modules to candidates flagged for weak section scores.'
  };

  // ----------------------------------------------------
  // MASTER COMPLETED TEST DATASETS (PUBLISHED BY ADMIN)
  // ----------------------------------------------------
  tests: Test[] = [
    {
      id: 't4',
      testKey: 'PROVEYU-2026-TEST4',
      title: 'ProveYu Grand Assessment 2026 (Test 4)',
      sections: [
        { name: 'Coding', maxScore: 30 },
        { name: 'Aptitude', maxScore: 30 },
        { name: 'Communication', maxScore: 30 }
      ],
      tier1Cutoff: 70,
      tier2Cutoff: 60,
      date: '2026-09-25',
      startTime: '10:00 AM',
      durationMinutes: 90,
      venue: 'Main Computer Labs 1-4',
      eligibleBatches: ['2024 Batch'],
      invigilators: ['Dr. V. K. Patil', 'Prof. Ramesh K.'],
      status: 'Completed',
      totalRegistered: 250,
      totalAttended: 235,
      totalAbsent: 15
    },
    {
      id: 't5',
      testKey: 'PROVEYU-2026-TEST5',
      title: 'Pre-Placement Technical Evaluation (Test 5)',
      sections: [
        { name: 'Coding', maxScore: 30 },
        { name: 'Aptitude', maxScore: 30 },
        { name: 'Communication', maxScore: 30 }
      ],
      tier1Cutoff: 70,
      tier2Cutoff: 60,
      date: '2026-09-22',
      startTime: '02:00 PM',
      durationMinutes: 90,
      venue: 'Online Proctoring Portal & Lab B',
      eligibleBatches: ['2024 Batch'],
      invigilators: ['Prof. Sneha Deshmukh'],
      status: 'Completed',
      totalRegistered: 180,
      totalAttended: 172,
      totalAbsent: 8
    },
    {
      id: 't6',
      testKey: 'PROVEYU-2026-TEST6',
      title: 'TCS & Wipro Corporate Mock Test 2026',
      sections: [
        { name: 'Coding', maxScore: 30 },
        { name: 'Aptitude', maxScore: 30 },
        { name: 'Communication', maxScore: 30 }
      ],
      tier1Cutoff: 70,
      tier2Cutoff: 60,
      date: '2026-09-18',
      startTime: '11:00 AM',
      durationMinutes: 90,
      venue: 'Main Auditorium & Computer Lab 3',
      eligibleBatches: ['2024 Batch', '2023 Batch'],
      invigilators: ['Dr. Arun K.', 'Prof. Priya V.'],
      status: 'Completed',
      totalRegistered: 210,
      totalAttended: 201,
      totalAbsent: 9
    }
  ];

  // Master Test Results Store (Cross-Module Connected Data)
  masterResultsMap: { [testId: string]: TestResult[] } = {
    't4': [
      {
        studentId: '1',
        rollNumber: '2024-CSE-001',
        name: 'Rahul Sharma',
        email: 'rahul.s@mgmcet.ac.in',
        branch: 'Computer Science',
        overallScore: 82,
        sectionScores: { Coding: 28, Aptitude: 26, Communication: 28 },
        tier: 'Tier 1',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 3,
        placementStatus: 'Placed',
        needsTraining: false
      },
      {
        studentId: '2',
        rollNumber: '2024-CSE-002',
        name: 'Priya Desai',
        email: 'priya.d@mgmcet.ac.in',
        branch: 'Computer Science',
        overallScore: 78,
        sectionScores: { Coding: 26, Aptitude: 26, Communication: 26 },
        tier: 'Tier 1',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 2,
        placementStatus: 'Placed',
        needsTraining: false
      },
      {
        studentId: '3',
        rollNumber: '2024-IT-008',
        name: 'Sandhya Kulkarni',
        email: 'sandhya.k@mgmcet.ac.in',
        branch: 'Information Tech',
        overallScore: 86,
        sectionScores: { Coding: 29, Aptitude: 27, Communication: 30 },
        tier: 'Tier 1',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 4,
        placementStatus: 'Unplaced',
        needsTraining: false
      },
      {
        studentId: '4',
        rollNumber: '2024-IT-011',
        name: 'Vikram Reddy',
        email: 'vikram.r@mgmcet.ac.in',
        branch: 'Information Tech',
        overallScore: 68,
        sectionScores: { Coding: 22, Aptitude: 23, Communication: 23 },
        tier: 'Tier 2',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 1,
        placementStatus: 'Unplaced',
        needsTraining: false
      },
      {
        studentId: '5',
        rollNumber: '2024-EXTC-005',
        name: 'Amit Verma',
        email: 'amit.v@mgmcet.ac.in',
        branch: 'Electronics',
        overallScore: 65,
        sectionScores: { Coding: 21, Aptitude: 22, Communication: 22 },
        tier: 'Tier 2',
        passFail: 'Pass',
        listedOnPlatform: false,
        shortlistedByCompanies: 1,
        placementStatus: 'Placed',
        needsTraining: false
      },
      {
        studentId: '6',
        rollNumber: '2024-CSE-006',
        name: 'Divya Nair',
        email: 'divya.n@mgmcet.ac.in',
        branch: 'Computer Science',
        overallScore: 54,
        sectionScores: { Coding: 14, Aptitude: 20, Communication: 20 },
        tier: 'Tier 3',
        passFail: 'Fail',
        listedOnPlatform: false,
        shortlistedByCompanies: 0,
        placementStatus: 'Unplaced',
        needsTraining: true,
        trainingReason: 'Coding score (14/30) is below Tier 2 threshold (18/30)'
      },
      {
        studentId: '7',
        rollNumber: '2024-EXTC-012',
        name: 'Sanjay Dutt',
        email: 'sanjay.d@mgmcet.ac.in',
        branch: 'Electronics',
        overallScore: 52,
        sectionScores: { Coding: 18, Aptitude: 14, Communication: 20 },
        tier: 'Tier 3',
        passFail: 'Fail',
        listedOnPlatform: false,
        shortlistedByCompanies: 0,
        placementStatus: 'Unplaced',
        needsTraining: true,
        trainingReason: 'Aptitude score (14/30) is below Tier 2 threshold (18/30)'
      },
      {
        studentId: '8',
        rollNumber: '2024-IT-018',
        name: 'Meera Iyer',
        email: 'meera.i@mgmcet.ac.in',
        branch: 'Information Tech',
        overallScore: 48,
        sectionScores: { Coding: 15, Aptitude: 15, Communication: 18 },
        tier: 'Tier 3',
        passFail: 'Fail',
        listedOnPlatform: false,
        shortlistedByCompanies: 0,
        placementStatus: 'Unplaced',
        needsTraining: true,
        trainingReason: 'Coding (15/30) & Aptitude (15/30) below Tier 2 cutoff'
      }
    ],
    't5': [
      {
        studentId: '9',
        rollNumber: '2024-CSE-019',
        name: 'Aniket Solanki',
        email: 'aniket.s@mgmcet.ac.in',
        branch: 'Computer Science',
        overallScore: 76,
        sectionScores: { Coding: 27, Aptitude: 24, Communication: 25 },
        tier: 'Tier 1',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 2,
        placementStatus: 'Unplaced',
        needsTraining: false
      },
      {
        studentId: '10',
        rollNumber: '2024-CSE-020',
        name: 'Pooja Hegde',
        email: 'pooja.h@mgmcet.ac.in',
        branch: 'Computer Science',
        overallScore: 50,
        sectionScores: { Coding: 13, Aptitude: 18, Communication: 19 },
        tier: 'Tier 3',
        passFail: 'Fail',
        listedOnPlatform: false,
        shortlistedByCompanies: 0,
        placementStatus: 'Unplaced',
        needsTraining: true,
        trainingReason: 'Coding score (13/30) below minimum cutoff'
      }
    ],
    't6': [
      {
        studentId: '11',
        rollNumber: '2024-IT-025',
        name: 'Karan Johar',
        email: 'karan.j@mgmcet.ac.in',
        branch: 'Information Tech',
        overallScore: 81,
        sectionScores: { Coding: 28, Aptitude: 25, Communication: 28 },
        tier: 'Tier 1',
        passFail: 'Pass',
        listedOnPlatform: true,
        shortlistedByCompanies: 3,
        placementStatus: 'Placed',
        needsTraining: false
      }
    ]
  };

  // ----------------------------------------------------
  // LIST VIEW COMPUTATIONS & FILTERS
  // ----------------------------------------------------
  getFilteredTests(): Test[] {
    return this.tests.filter(t => {
      const q = this.searchQuery.toLowerCase();
      return t.title.toLowerCase().includes(q) ||
        t.testKey.toLowerCase().includes(q) ||
        t.venue.toLowerCase().includes(q);
    });
  }

  // Calculate pass rate for test card
  getTestPassRate(testId: string): number {
    const results = this.masterResultsMap[testId] || [];
    if (results.length === 0) return 85;
    const pass = results.filter(r => r.passFail === 'Pass').length;
    return Math.round((pass / results.length) * 100);
  }

  // Calculate average score for test card
  getTestAvgScore(testId: string): number {
    const results = this.masterResultsMap[testId] || [];
    if (results.length === 0) return 72.4;
    const sum = results.reduce((acc, r) => acc + r.overallScore, 0);
    return parseFloat((sum / results.length).toFixed(1));
  }

  // ----------------------------------------------------
  // VIEW SWITCHING: LIST <-> DETAIL DRILL-DOWN
  // ----------------------------------------------------
  viewTestDetail(test: Test) {
    this.activeTest = test;
    this.resultSearchQuery = '';
    this.selectedPassFailFilter = 'All';
    this.selectedResultTierFilter = 'All';
    this.onlyNeedsTrainingFilter = false;
    this.selectedPlacementStatusFilter = 'All';
    this.selectedListedFilter = 'All';
    this.currentView = 'detail';
  }

  backToList() {
    this.currentView = 'list';
    this.activeTest = null;
  }

  // ----------------------------------------------------
  // SCREEN 2 DETAIL VIEW COMPUTATIONS
  // ----------------------------------------------------
  get activeResults(): TestResult[] {
    if (!this.activeTest) return [];
    return this.masterResultsMap[this.activeTest.id] || this.masterResultsMap['t4'] || [];
  }

  get filteredActiveResults(): TestResult[] {
    return this.activeResults.filter(r => {
      const q = this.resultSearchQuery.toLowerCase();
      const matchesSearch = r.name.toLowerCase().includes(q) ||
        r.rollNumber.toLowerCase().includes(q) ||
        r.branch.toLowerCase().includes(q);

      const matchesPassFail = this.selectedPassFailFilter === 'All' || r.passFail === this.selectedPassFailFilter;
      const matchesTier = this.selectedResultTierFilter === 'All' || r.tier === this.selectedResultTierFilter;
      const matchesTraining = !this.onlyNeedsTrainingFilter || r.needsTraining;
      const matchesPlacement = this.selectedPlacementStatusFilter === 'All' || r.placementStatus === this.selectedPlacementStatusFilter;
      const matchesListed = this.selectedListedFilter === 'All' || (this.selectedListedFilter === 'Yes' ? r.listedOnPlatform : !r.listedOnPlatform);

      return matchesSearch && matchesPassFail && matchesTier && matchesTraining && matchesPlacement && matchesListed;
    });
  }

  // Metric Header Computations
  get totalRegisteredCount(): number {
    return this.activeTest?.totalRegistered || this.activeResults.length;
  }

  get totalAttendedCount(): number {
    return this.activeTest?.totalAttended || this.activeResults.length;
  }

  get totalAbsentCount(): number {
    return this.activeTest?.totalAbsent || 0;
  }

  get detailPassRate(): number {
    if (this.activeResults.length === 0) return 0;
    const pass = this.activeResults.filter(r => r.passFail === 'Pass').length;
    return Math.round((pass / this.activeResults.length) * 100);
  }

  get detailAvgScore(): number {
    if (this.activeResults.length === 0) return 0;
    const sum = this.activeResults.reduce((acc, r) => acc + r.overallScore, 0);
    return parseFloat((sum / this.activeResults.length).toFixed(1));
  }

  get detailMedianScore(): number {
    if (this.activeResults.length === 0) return 0;
    const sorted = [...this.activeResults].map(r => r.overallScore).sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 !== 0 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2);
  }

  get detailHighestScore(): number {
    if (this.activeResults.length === 0) return 0;
    return Math.max(...this.activeResults.map(r => r.overallScore));
  }

  get detailLowestScore(): number {
    if (this.activeResults.length === 0) return 0;
    return Math.min(...this.activeResults.map(r => r.overallScore));
  }

  // Tier Distribution Tile Computations
  get tier1Count(): number {
    return this.activeResults.filter(r => r.tier === 'Tier 1').length;
  }

  get tier1Percentage(): number {
    if (this.activeResults.length === 0) return 0;
    return Math.round((this.tier1Count / this.activeResults.length) * 100);
  }

  get tier2Count(): number {
    return this.activeResults.filter(r => r.tier === 'Tier 2').length;
  }

  get tier2Percentage(): number {
    if (this.activeResults.length === 0) return 0;
    return Math.round((this.tier2Count / this.activeResults.length) * 100);
  }

  get tier3Count(): number {
    return this.activeResults.filter(r => r.tier === 'Tier 3').length;
  }

  get tier3Percentage(): number {
    if (this.activeResults.length === 0) return 0;
    return Math.round((this.tier3Count / this.activeResults.length) * 100);
  }

  get needsTrainingCount(): number {
    return this.activeResults.filter(r => r.needsTraining).length;
  }

  // Section-Wise Averages for Bar Visual
  get sectionAvgCoding(): number {
    if (this.activeResults.length === 0) return 25.2;
    const sum = this.activeResults.reduce((acc, r) => acc + (r.sectionScores['Coding'] || 0), 0);
    return parseFloat((sum / this.activeResults.length).toFixed(1));
  }

  get sectionAvgAptitude(): number {
    if (this.activeResults.length === 0) return 21.4;
    const sum = this.activeResults.reduce((acc, r) => acc + (r.sectionScores['Aptitude'] || 0), 0);
    return parseFloat((sum / this.activeResults.length).toFixed(1));
  }

  get sectionAvgComm(): number {
    if (this.activeResults.length === 0) return 23.8;
    const sum = this.activeResults.reduce((acc, r) => acc + (r.sectionScores['Communication'] || 0), 0);
    return parseFloat((sum / this.activeResults.length).toFixed(1));
  }

  // Filter Quick Toggles
  filterByTier(tier: string) {
    if (this.selectedResultTierFilter === tier) {
      this.selectedResultTierFilter = 'All';
    } else {
      this.selectedResultTierFilter = tier;
    }
  }

  toggleNeedsTrainingFilter() {
    this.onlyNeedsTrainingFilter = !this.onlyNeedsTrainingFilter;
  }

  // ----------------------------------------------------
  // EXPORT & FACULTY ACTIONS
  // ----------------------------------------------------
  exportFilteredResults() {
    const count = this.filteredActiveResults.length;
    this.showToast(`📄 Exporting PDF/Excel report for ${count} candidate results...`);
  }

  openSendFacultyModal() {
    this.showSendFacultyModal = true;
  }

  closeSendFacultyModal() {
    this.showSendFacultyModal = false;
  }

  sendFacultyAlert() {
    const count = this.filteredActiveResults.length;
    this.closeSendFacultyModal();
    this.showToast(`🚀 Dispatched training alert with ${count} candidate records to ${this.facultyForm.email}!`);
  }

  showToast(msg: string) {
    this.notificationService.showSuccess(msg);
  }
}
