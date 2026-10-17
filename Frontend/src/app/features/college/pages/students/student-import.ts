import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface StudentImportRow {
  // Section 1: Personal
  rollNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dob: string;
  gender: string;
  aadharNumber: string;
  panNumber: string;
  address: string;

  // Section 2: Academic
  collegeName: string;
  batchYear: string;
  branch: string;
  degree: string;
  tenthPercentage: number | string;
  twelfthPercentage: number | string;
  activeBacklogs: number | string;
  gapYears: number | string;
  gradDate: string;

  // Section 3: Semesters 1-8 CGPA
  sem1: number | string;
  sem2: number | string;
  sem3: number | string;
  sem4: number | string;
  sem5: number | string;
  sem6: number | string;
  sem7: number | string;
  sem8: number | string;
  aggregateCgpa: number | string;

  // Section 4: Professional Links & Status
  status: 'Placed' | 'Unplaced';
  linkedin: string;
  github: string;
  portfolio: string;

  valid: boolean;
}

@Component({
  selector: 'app-college-student-import',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './student-import.html',
  styleUrl: './student-import.scss',
})
export class StudentImport implements OnInit {
  isFileUploaded = false;
  fileName = '';
  fileSize = '';
  isImported = false;
  isProcessing = false;

  // Active view tab for table
  activeTab: 'all' | 'personal' | 'academic' | 'semesters' | 'placement' = 'all';

  // Search filter for preview table
  tableSearchQuery = '';

  // Clean Column Headers matching Add Student
  readonly columnHeaders: string[] = [
    'Roll Number',
    'First Name',
    'Last Name',
    'College Email',
    'Mobile Phone',
    'Date of Birth',
    'Gender',
    'Aadhaar Number',
    'PAN Number',
    'Residential Address',
    'College Name',
    'Batch Passing Year',
    'Branch Department',
    'Degree Name',
    '10th Marks (%)',
    '12th Marks (%)',
    'Active Backlogs',
    'Education Gap Years',
    'Graduation Date',
    'Sem 1 SGPA',
    'Sem 2 SGPA',
    'Sem 3 SGPA',
    'Sem 4 SGPA',
    'Sem 5 SGPA',
    'Sem 6 SGPA',
    'Sem 7 SGPA',
    'Sem 8 SGPA',
    'Aggregate CGPA',
    'Placement Status',
    'LinkedIn Profile',
    'GitHub Profile',
    'Portfolio URL'
  ];

  // Comprehensive sample rows matching all Add Student fields (Hiring Company & CTC removed)
  previewRows: StudentImportRow[] = [
    {
      rollNumber: '2024-CSE-001',
      firstName: 'Rahul',
      lastName: 'Sharma',
      email: 'rahul@mgmcet.ac.in',
      phone: '+91 98765 43210',
      dob: '2002-01-05',
      gender: 'Male',
      aadharNumber: '5432 1098 7654',
      panNumber: 'ABCDE1234F',
      address: 'Sector 19, Nerul, Navi Mumbai - 400706',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Computer Science & Engineering',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 88.0,
      twelfthPercentage: 86.0,
      activeBacklogs: 0,
      gapYears: 0,
      gradDate: '2024-06-30',
      sem1: 8.20,
      sem2: 8.35,
      sem3: 8.40,
      sem4: 8.55,
      sem5: 8.60,
      sem6: 8.50,
      sem7: 8.65,
      sem8: 8.75,
      aggregateCgpa: 8.50,
      status: 'Placed',
      linkedin: 'https://linkedin.com/in/rahul-sharma-cs',
      github: 'https://github.com/rahul-sharma',
      portfolio: 'https://rahulsharma.dev',
      valid: true
    },
    {
      rollNumber: '2024-IT-002',
      firstName: 'Priya',
      lastName: 'Desai',
      email: 'priya@mgmcet.ac.in',
      phone: '+91 98765 43211',
      dob: '2002-04-12',
      gender: 'Female',
      aadharNumber: '4321 0987 6543',
      panNumber: 'BCDEF2345G',
      address: 'Sector 15, Vashi, Navi Mumbai - 400703',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Information Technology',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 91.5,
      twelfthPercentage: 89.0,
      activeBacklogs: 0,
      gapYears: 0,
      gradDate: '2024-06-30',
      sem1: 8.60,
      sem2: 8.70,
      sem3: 8.75,
      sem4: 8.80,
      sem5: 8.90,
      sem6: 8.85,
      sem7: 8.95,
      sem8: 9.05,
      aggregateCgpa: 8.82,
      status: 'Placed',
      linkedin: 'https://linkedin.com/in/priya-desai',
      github: 'https://github.com/priya-desai',
      portfolio: 'https://priyadesai.tech',
      valid: true
    },
    {
      rollNumber: '2024-CSE-003',
      firstName: 'Arun',
      lastName: 'Patel',
      email: 'arun@mgmcet.ac.in',
      phone: '+91 98765 43212',
      dob: '2001-11-20',
      gender: 'Male',
      aadharNumber: '3210 9876 5432',
      panNumber: 'CDEFG3456H',
      address: 'Kharghar, Navi Mumbai - 410210',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Computer Science & Engineering',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 79.0,
      twelfthPercentage: 78.5,
      activeBacklogs: 0,
      gapYears: 0,
      gradDate: '2024-06-30',
      sem1: 7.40,
      sem2: 7.50,
      sem3: 7.60,
      sem4: 7.55,
      sem5: 7.70,
      sem6: 7.65,
      sem7: 7.80,
      sem8: 7.90,
      aggregateCgpa: 7.64,
      status: 'Unplaced',
      linkedin: 'https://linkedin.com/in/arun-patel',
      github: 'https://github.com/arun-patel',
      portfolio: '',
      valid: true
    },
    {
      rollNumber: '2024-MECH-004',
      firstName: 'Rohan',
      lastName: 'Verma',
      email: 'rohan.v@mgmcet.ac.in',
      phone: '+91 98765 43213',
      dob: '2002-07-08',
      gender: 'Male',
      aadharNumber: '2109 8765 4321',
      panNumber: 'DEFGH4567J',
      address: 'Panvel, Navi Mumbai - 410206',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Mechanical Engineering',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 76.5,
      twelfthPercentage: 74.0,
      activeBacklogs: 0,
      gapYears: 1,
      gradDate: '2024-06-30',
      sem1: 7.10,
      sem2: 7.20,
      sem3: 7.30,
      sem4: 7.25,
      sem5: 7.40,
      sem6: 7.35,
      sem7: 7.50,
      sem8: 7.60,
      aggregateCgpa: 7.34,
      status: 'Unplaced',
      linkedin: 'https://linkedin.com/in/rohan-verma',
      github: '',
      portfolio: '',
      valid: true
    },
    {
      rollNumber: '2024-EXTC-005',
      firstName: 'Sneha',
      lastName: 'Patil',
      email: 'sneha.p@mgmcet.ac.in',
      phone: '+91 98765 43214',
      dob: '2002-09-18',
      gender: 'Female',
      aadharNumber: '1098 7654 3210',
      panNumber: 'EFGHI5678K',
      address: 'Kalyan, Maharashtra - 421301',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Electronics & Telecommunication',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 84.0,
      twelfthPercentage: 82.0,
      activeBacklogs: 0,
      gapYears: 0,
      gradDate: '2024-06-30',
      sem1: 8.00,
      sem2: 8.10,
      sem3: 8.15,
      sem4: 8.25,
      sem5: 8.30,
      sem6: 8.20,
      sem7: 8.40,
      sem8: 8.50,
      aggregateCgpa: 8.24,
      status: 'Placed',
      linkedin: 'https://linkedin.com/in/sneha-patil',
      github: 'https://github.com/sneha-patil',
      portfolio: '',
      valid: true
    },
    {
      rollNumber: '2024-CIVIL-006',
      firstName: 'Amit',
      lastName: 'Joshi',
      email: 'amit.j@mgmcet.ac.in',
      phone: '+91 98765 43215',
      dob: '2001-12-04',
      gender: 'Male',
      aadharNumber: '9876 5432 1098',
      panNumber: 'FGHIJ6789L',
      address: 'Thane West, Maharashtra - 400602',
      collegeName: 'MGM College of Engineering & Technology, Kamothe',
      batchYear: '2024',
      branch: 'Civil Engineering',
      degree: 'Bachelor of Technology (B.Tech)',
      tenthPercentage: 73.0,
      twelfthPercentage: 71.0,
      activeBacklogs: 1,
      gapYears: 0,
      gradDate: '2024-06-30',
      sem1: 6.80,
      sem2: 6.90,
      sem3: 7.00,
      sem4: 6.85,
      sem5: 7.10,
      sem6: 7.05,
      sem7: 7.20,
      sem8: 7.30,
      aggregateCgpa: 7.03,
      status: 'Unplaced',
      linkedin: 'https://linkedin.com/in/amit-joshi',
      github: '',
      portfolio: '',
      valid: true
    }
  ];

  constructor(
    private router: Router,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {}

  // Filter preview rows by search query
  get filteredPreviewRows(): StudentImportRow[] {
    const q = this.tableSearchQuery.trim().toLowerCase();
    if (!q) return this.previewRows;
    return this.previewRows.filter(r =>
      r.rollNumber.toLowerCase().includes(q) ||
      r.firstName.toLowerCase().includes(q) ||
      r.lastName.toLowerCase().includes(q) ||
      r.email.toLowerCase().includes(q) ||
      r.branch.toLowerCase().includes(q) ||
      r.status.toLowerCase().includes(q)
    );
  }

  get totalRowsCount(): number {
    return this.previewRows.length;
  }

  // Download Sample or Blank CSV/Excel Template (No Company, No CTC)
  downloadTemplate(withData: boolean = true): void {
    const headers = this.columnHeaders;

    const rowsData: string[][] = [];

    if (withData) {
      this.previewRows.forEach(row => {
        rowsData.push([
          row.rollNumber,
          row.firstName,
          row.lastName,
          row.email,
          row.phone,
          row.dob,
          row.gender,
          row.aadharNumber,
          row.panNumber,
          row.address,
          row.collegeName,
          row.batchYear,
          row.branch,
          row.degree,
          String(row.tenthPercentage),
          String(row.twelfthPercentage),
          String(row.activeBacklogs),
          String(row.gapYears),
          row.gradDate,
          String(row.sem1),
          String(row.sem2),
          String(row.sem3),
          String(row.sem4),
          String(row.sem5),
          String(row.sem6),
          String(row.sem7),
          String(row.sem8),
          String(row.aggregateCgpa),
          row.status,
          row.linkedin,
          row.github,
          row.portfolio
        ]);
      });
    }

    // Format RFC4180 CSV
    const csvContent = [
      headers.map(h => `"${h.replace(/"/g, '""')}"`).join(','),
      ...rowsData.map(r => r.map(val => `"${(val || '').replace(/"/g, '""')}"`).join(','))
    ].join('\r\n');

    // Add UTF-8 Byte Order Mark (BOM) so Excel opens UTF-8 text cleanly
    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const fileName = withData
      ? 'proveyu_student_import_sample_template.csv'
      : 'proveyu_student_import_blank_template.csv';
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    if (withData) {
      this.notificationService.showSuccess(
        `Downloaded sample template with ${rowsData.length} demo candidates! Open in Microsoft Excel to add your student data.`,
        'Template Downloaded'
      );
    } else {
      this.notificationService.showInfo(
        'Downloaded blank CSV template with all column headers. Add student rows and upload!',
        'Blank Template Ready'
      );
    }
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.fileName = file.name;
      this.fileSize = `${(file.size / 1024).toFixed(1)} KB`;
      this.isFileUploaded = true;
      this.notificationService.showSuccess(
        `Loaded "${this.fileName}" (${this.fileSize}). Parsed student records with Semester 1-8 CGPA and identity data.`,
        'File Loaded'
      );
    }
  }

  triggerSimulatedUpload(): void {
    this.fileName = 'mgm_cet_btech_2024_roster_complete.csv';
    this.fileSize = '48.5 KB';
    this.isFileUploaded = true;
    this.notificationService.showInfo(
      'Loaded sample dataset containing Personal details, Academic cutoffs, Sem 1-8 CGPA, and Social profiles.',
      'Demo Dataset Loaded'
    );
  }

  confirmImport(): void {
    this.isProcessing = true;
    setTimeout(() => {
      this.isProcessing = false;
      this.isImported = true;
      this.notificationService.showSuccess(
        `Successfully imported ${this.totalRowsCount} student profiles with complete CGPA and personal records into MGM CET Campus Roster!`,
        'Import Completed'
      );
      setTimeout(() => {
        this.router.navigate(['/college/students']);
      }, 1500);
    }, 900);
  }

  resetImport(): void {
    this.isFileUploaded = false;
    this.fileName = '';
    this.fileSize = '';
    this.isImported = false;
    this.isProcessing = false;
    this.notificationService.showInfo('Upload reset. You can choose a new file or download the template.');
  }
}
