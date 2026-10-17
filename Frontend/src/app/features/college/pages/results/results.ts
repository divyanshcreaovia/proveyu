import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface StudentScoreRecord {
  id: string;
  rollNumber: string;
  name: string;
  email: string;
  branch: string;
  batch: string;
  testName: string;
  overallScore: number;
  tier: 'Tier 1' | 'Tier 2' | 'Tier 3';
  codingScore: number;       // Max 30
  aptitudeScore: number;     // Max 30
  communicationScore: number;// Max 30
  domainScore: number;       // Max 10
  weakestSection: string;
  status: 'Pass' | 'Fail';
  feedbackSent: boolean;
  remedialAssigned?: boolean;
}

export interface SectionBreakdown {
  name: string;
  key: 'codingScore' | 'aptitudeScore' | 'communicationScore' | 'domainScore';
  maxScore: number;
  avgScore: number;
  avgPercentage: number;
  statusTag: 'Strongest' | 'Improving' | 'Needs Focus' | 'Stable';
  tagClass: string;
  barColor: string;
}

export interface TierMigration {
  from: string;
  to: string;
  studentCount: number;
  label: string;
  type: 'upgrade' | 'retained' | 'at_risk';
}

@Component({
  selector: 'app-college-results',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './results.html',
  styleUrl: './results.scss',
})
export class Results {
  constructor(private notificationService: NotificationService) {}
  // Top-Level Filter Bar States
  selectedBatch: string = 'All';      // 'All' | '2024' | '2023'
  selectedBranch: string = 'All';     // 'All' | 'Computer Science' | 'Information Tech' | 'Electronics'
  selectedTest: string = 'All';       // 'All' | 'Test 1' | 'Test 2' | 'Test 3' | 'Test 4'
  selectedTier: string = 'All';       // 'All' | 'Tier 1' | 'Tier 2' | 'Tier 3'

  showAlertFacultyModal: boolean = false;

  // Faculty Notification Form
  facultyForm = {
    facultyEmail: 'tpo.training@mgmcet.ac.in',
    subject: '[Action Required] Remedial Batch Assignment for Weak Section Candidates',
    customNotes: 'Please schedule extra practice sessions for candidates scoring <60% in Quantitative Aptitude.'
  };

  // Progression Chart Branch Comparator Filter
  progressionBranchView: string = 'All';

  // Master Evaluation Dataset
  masterEvaluationRecords: StudentScoreRecord[] = [
    { id: '1', rollNumber: '2024-CSE-001', name: 'Rahul Sharma', email: 'rahul.s@mgmcet.ac.in', branch: 'Computer Science', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 86, tier: 'Tier 1', codingScore: 28, aptitudeScore: 26, communicationScore: 24, domainScore: 8, weakestSection: 'Communication', status: 'Pass', feedbackSent: true },
    { id: '2', rollNumber: '2024-CSE-002', name: 'Priya Desai', email: 'priya.d@mgmcet.ac.in', branch: 'Computer Science', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 82, tier: 'Tier 1', codingScore: 27, aptitudeScore: 25, communicationScore: 22, domainScore: 8, weakestSection: 'Communication', status: 'Pass', feedbackSent: true },
    { id: '3', rollNumber: '2024-IT-008', name: 'Sandhya Kulkarni', email: 'sandhya.k@mgmcet.ac.in', branch: 'Information Tech', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 88, tier: 'Tier 1', codingScore: 29, aptitudeScore: 27, communicationScore: 24, domainScore: 8, weakestSection: 'Communication', status: 'Pass', feedbackSent: true },
    { id: '4', rollNumber: '2024-CSE-015', name: 'Ananya Roy', email: 'ananya.r@mgmcet.ac.in', branch: 'Computer Science', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 92, tier: 'Tier 1', codingScore: 30, aptitudeScore: 28, communicationScore: 25, domainScore: 9, weakestSection: 'Communication', status: 'Pass', feedbackSent: true },
    { id: '5', rollNumber: '2024-IT-025', name: 'Karan Malhotra', email: 'karan.m@mgmcet.ac.in', branch: 'Information Tech', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 78, tier: 'Tier 1', codingScore: 26, aptitudeScore: 23, communicationScore: 22, domainScore: 7, weakestSection: 'Aptitude', status: 'Pass', feedbackSent: true },
    { id: '6', rollNumber: '2024-EXTC-005', name: 'Amit Verma', email: 'amit.v@mgmcet.ac.in', branch: 'Electronics', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 65, tier: 'Tier 2', codingScore: 20, aptitudeScore: 16, communicationScore: 22, domainScore: 7, weakestSection: 'Aptitude', status: 'Pass', feedbackSent: true },
    { id: '7', rollNumber: '2024-IT-011', name: 'Vikram Reddy', email: 'vikram.r@mgmcet.ac.in', branch: 'Information Tech', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 68, tier: 'Tier 2', codingScore: 22, aptitudeScore: 18, communicationScore: 21, domainScore: 7, weakestSection: 'Aptitude', status: 'Pass', feedbackSent: false },
    { id: '8', rollNumber: '2024-EXTC-020', name: 'Rohan Gupta', email: 'rohan.g@mgmcet.ac.in', branch: 'Electronics', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 62, tier: 'Tier 2', codingScore: 19, aptitudeScore: 17, communicationScore: 20, domainScore: 6, weakestSection: 'Aptitude', status: 'Pass', feedbackSent: false },
    { id: '9', rollNumber: '2024-CSE-006', name: 'Divya Nair', email: 'divya.n@mgmcet.ac.in', branch: 'Computer Science', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 54, tier: 'Tier 3', codingScore: 16, aptitudeScore: 14, communicationScore: 18, domainScore: 6, weakestSection: 'Aptitude', status: 'Fail', feedbackSent: true, remedialAssigned: true },
    { id: '10', rollNumber: '2024-EXTC-012', name: 'Sanjay Dutt', email: 'sanjay.d@mgmcet.ac.in', branch: 'Electronics', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 51, tier: 'Tier 3', codingScore: 14, aptitudeScore: 13, communicationScore: 19, domainScore: 5, weakestSection: 'Aptitude', status: 'Fail', feedbackSent: false },
    { id: '11', rollNumber: '2024-IT-018', name: 'Meera Iyer', email: 'meera.i@mgmcet.ac.in', branch: 'Information Tech', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 49, tier: 'Tier 3', codingScore: 15, aptitudeScore: 12, communicationScore: 17, domainScore: 5, weakestSection: 'Aptitude', status: 'Fail', feedbackSent: false },
    { id: '12', rollNumber: '2024-EXTC-025', name: 'Tushar Shinde', email: 'tushar.s@mgmcet.ac.in', branch: 'Electronics', batch: '2024', testName: 'Test 4 (Final Assessment)', overallScore: 46, tier: 'Tier 3', codingScore: 13, aptitudeScore: 11, communicationScore: 17, domainScore: 5, weakestSection: 'Aptitude', status: 'Fail', feedbackSent: false }
  ];

  // Tier Migration Historical Matrix (Test 1 -> Test 4)
  tierMigrations: TierMigration[] = [
    { from: 'Tier 3 (<60)', to: 'Tier 2 (60-70)', studentCount: 18, label: 'Upgraded from At-Risk to Solid Tier 2', type: 'upgrade' },
    { from: 'Tier 2 (60-70)', to: 'Tier 1 (70+)', studentCount: 14, label: 'Upgraded from Tier 2 to High-Performing Tier 1', type: 'upgrade' },
    { from: 'Tier 1 (70+)', to: 'Tier 1 (70+)', studentCount: 42, label: 'Consistently Maintained Top Tier Standards', type: 'retained' },
    { from: 'Tier 3 (<60)', to: 'Tier 3 (<60)', studentCount: 6, label: 'Stagnant in Tier 3 — Requires Faculty Remedial Training', type: 'at_risk' }
  ];

  get filteredRecords(): StudentScoreRecord[] {
    return this.masterEvaluationRecords.filter(r => {
      const matchesBatch = this.selectedBatch === 'All' || r.batch === this.selectedBatch;
      const matchesBranch = this.selectedBranch === 'All' || r.branch === this.selectedBranch;
      const matchesTier = this.selectedTier === 'All' || r.tier === this.selectedTier;
      return matchesBatch && matchesBranch && matchesTier;
    });
  }

  // ----------------------------------------------------
  // COMPUTED TOP-LEVEL OVERVIEW DIAGNOSTIC KPIS
  // ----------------------------------------------------
  get totalTestsConducted(): number {
    return 4; // Test 1, Test 2, Test 3, Test 4
  }

  get totalAssessmentsTaken(): number {
    return this.filteredRecords.length * 21; // Scaled total assessments across tests
  }

  get passRatePercentage(): number {
    if (this.filteredRecords.length === 0) return 0;
    const passCount = this.filteredRecords.filter(r => r.overallScore >= 60).length;
    return Math.round((passCount / this.filteredRecords.length) * 100);
  }

  get avgProveYuScore(): string {
    if (this.filteredRecords.length === 0) return '0.0';
    const sum = this.filteredRecords.reduce((acc, r) => acc + r.overallScore, 0);
    return (sum / this.filteredRecords.length).toFixed(1);
  }

  get scoreImprovementVsLastTest(): string {
    return '+3.4 pts'; // Trajectory improvement calculation
  }

  // ----------------------------------------------------
  // SECTION-WISE STRENGTH & WEAKNESS BREAKDOWN
  // ----------------------------------------------------
  get sectionBreakdowns(): SectionBreakdown[] {
    const records = this.filteredRecords;
    if (records.length === 0) return [];

    const codingAvg = records.reduce((acc, r) => acc + r.codingScore, 0) / records.length;
    const aptiAvg = records.reduce((acc, r) => acc + r.aptitudeScore, 0) / records.length;
    const commAvg = records.reduce((acc, r) => acc + r.communicationScore, 0) / records.length;
    const domainAvg = records.reduce((acc, r) => acc + r.domainScore, 0) / records.length;

    return [
      {
        name: 'Coding & Data Structures',
        key: 'codingScore',
        maxScore: 30,
        avgScore: parseFloat(codingAvg.toFixed(1)),
        avgPercentage: Math.round((codingAvg / 30) * 100),
        statusTag: 'Strongest',
        tagClass: 'bg-success text-white',
        barColor: '#00875a'
      },
      {
        name: 'Quantitative & Logical Aptitude',
        key: 'aptitudeScore',
        maxScore: 30,
        avgScore: parseFloat(aptiAvg.toFixed(1)),
        avgPercentage: Math.round((aptiAvg / 30) * 100),
        statusTag: 'Needs Focus',
        tagClass: 'bg-danger text-white',
        barColor: '#dc2626'
      },
      {
        name: 'Business Communication & Verbal',
        key: 'communicationScore',
        maxScore: 30,
        avgScore: parseFloat(commAvg.toFixed(1)),
        avgPercentage: Math.round((commAvg / 30) * 100),
        statusTag: 'Improving',
        tagClass: 'bg-warning text-dark',
        barColor: '#d97706'
      },
      {
        name: 'Core Domain Engineering',
        key: 'domainScore',
        maxScore: 10,
        avgScore: parseFloat(domainAvg.toFixed(1)),
        avgPercentage: Math.round((domainAvg / 10) * 100),
        statusTag: 'Stable',
        tagClass: 'bg-primary text-white',
        barColor: '#2563eb'
      }
    ];
  }

  // ----------------------------------------------------
  // LEADERBOARDS & ATTENTION LISTS
  // ----------------------------------------------------
  get top5Performers(): StudentScoreRecord[] {
    return [...this.filteredRecords]
      .sort((a, b) => b.overallScore - a.overallScore)
      .slice(0, 5);
  }

  get bottomNeedsAttention(): StudentScoreRecord[] {
    return [...this.filteredRecords]
      .sort((a, b) => a.overallScore - b.overallScore)
      .slice(0, 5);
  }

  assignRemedialModule(student: StudentScoreRecord) {
    student.remedialAssigned = true;
    this.showToast(`📌 Remedial training assigned to ${student.name} (${student.rollNumber})!`);
  }

  // ----------------------------------------------------
  // FACULTY ALERT DISPATCH
  // ----------------------------------------------------
  openAlertFacultyModal() {
    this.showAlertFacultyModal = true;
  }

  closeAlertFacultyModal() {
    this.showAlertFacultyModal = false;
  }

  sendFacultyAlert() {
    this.closeAlertFacultyModal();
    this.showToast(`🚀 Diagnostic Alert & Remedial Notice emailed to ${this.facultyForm.facultyEmail}!`);
  }

  // ----------------------------------------------------
  // EXPORT REPORTS
  // ----------------------------------------------------
  exportPdfReport() {
    this.showToast('📄 Generating TPO Management Diagnostic PDF Report...');
  }

  exportExcelMatrix() {
    this.showToast('📊 Exporting Full Evaluation Excel Data Matrix...');
  }

  showToast(msg: string) {
    this.notificationService.showSuccess(msg);
  }
}
