import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface SemesterGrade {
  semNumber: number;
  label: string;
  shortLabel: string;
  yearLabel: string;
  sgpa: number | null;
}

@Component({
  selector: 'app-college-student-form',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './student-form.html',
  styleUrl: './student-form.scss',
})
export class StudentForm implements OnInit {
  studentId: string = '001';
  isEditMode: boolean = true;
  isSaved: boolean = false;

  formData = {
    rollNumber: '2024-CSE-001',
    firstName: 'Rahul',
    lastName: 'Sharma',
    email: 'rahul@mgmcet.ac.in',
    phone: '+91 98765 43210',
    dob: '2002-01-05',
    gender: 'Male',
    aadharNumber: '5432 1098 7654',
    panNumber: 'ABCDE1234F',
    address: 'Sector 19, Nerul, Navi Mumbai, Maharashtra - 400706',
    collegeName: 'MGM College of Engineering & Technology, Kamothe',
    batchYear: '2024',
    branch: 'Computer Science & Engineering',
    degree: 'Bachelor of Technology (B.Tech)',
    cgpa: '8.50',
    tenthPercentage: 88,
    twelfthPercentage: 86,
    activeBacklogs: 0,
    gapYears: 0,
    gradDate: '2024-06-30',
    resumeUploaded: true,
    linkedin: 'https://linkedin.com/in/rahul-sharma-cs',
    github: 'https://github.com/rahul-sharma',
    portfolio: 'https://rahulsharma.dev'
  };

  // 1 to 8 Semesters CGPA / SGPA Records
  semesters: SemesterGrade[] = [
    { semNumber: 1, label: 'Semester 1', shortLabel: 'Sem 1', yearLabel: '1st Year', sgpa: 8.20 },
    { semNumber: 2, label: 'Semester 2', shortLabel: 'Sem 2', yearLabel: '1st Year', sgpa: 8.35 },
    { semNumber: 3, label: 'Semester 3', shortLabel: 'Sem 3', yearLabel: '2nd Year', sgpa: 8.40 },
    { semNumber: 4, label: 'Semester 4', shortLabel: 'Sem 4', yearLabel: '2nd Year', sgpa: 8.55 },
    { semNumber: 5, label: 'Semester 5', shortLabel: 'Sem 5', yearLabel: '3rd Year', sgpa: 8.60 },
    { semNumber: 6, label: 'Semester 6', shortLabel: 'Sem 6', yearLabel: '3rd Year', sgpa: 8.50 },
    { semNumber: 7, label: 'Semester 7', shortLabel: 'Sem 7', yearLabel: '4th Year', sgpa: 8.65 },
    { semNumber: 8, label: 'Semester 8', shortLabel: 'Sem 8', yearLabel: '4th Year', sgpa: 8.75 }
  ];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('studentId');
    if (id) {
      this.studentId = id;
      this.isEditMode = true;
      this.loadStudentData(id);
    } else {
      this.isEditMode = false;
      this.syncFormDataCgpa();
    }
  }

  loadStudentData(id: string): void {
    if (id === '001') {
      this.formData = {
        rollNumber: '2024-CSE-001',
        firstName: 'Rahul',
        lastName: 'Sharma',
        email: 'rahul@mgmcet.ac.in',
        phone: '+91 98765 43210',
        dob: '2002-01-05',
        gender: 'Male',
        aadharNumber: '5432 1098 7654',
        panNumber: 'ABCDE1234F',
        address: 'Sector 19, Nerul, Navi Mumbai, Maharashtra - 400706',
        collegeName: 'MGM College of Engineering & Technology, Kamothe',
        batchYear: '2024',
        branch: 'Computer Science & Engineering',
        degree: 'Bachelor of Technology (B.Tech)',
        cgpa: '8.50',
        tenthPercentage: 88,
        twelfthPercentage: 86,
        activeBacklogs: 0,
        gapYears: 0,
        gradDate: '2024-06-30',
        resumeUploaded: true,
        linkedin: 'https://linkedin.com/in/rahul-sharma-cs',
        github: 'https://github.com/rahul-sharma',
        portfolio: 'https://rahulsharma.dev'
      };
      this.semesters = [
        { semNumber: 1, label: 'Semester 1', shortLabel: 'Sem 1', yearLabel: '1st Year', sgpa: 8.20 },
        { semNumber: 2, label: 'Semester 2', shortLabel: 'Sem 2', yearLabel: '1st Year', sgpa: 8.35 },
        { semNumber: 3, label: 'Semester 3', shortLabel: 'Sem 3', yearLabel: '2nd Year', sgpa: 8.40 },
        { semNumber: 4, label: 'Semester 4', shortLabel: 'Sem 4', yearLabel: '2nd Year', sgpa: 8.55 },
        { semNumber: 5, label: 'Semester 5', shortLabel: 'Sem 5', yearLabel: '3rd Year', sgpa: 8.60 },
        { semNumber: 6, label: 'Semester 6', shortLabel: 'Sem 6', yearLabel: '3rd Year', sgpa: 8.50 },
        { semNumber: 7, label: 'Semester 7', shortLabel: 'Sem 7', yearLabel: '4th Year', sgpa: 8.65 },
        { semNumber: 8, label: 'Semester 8', shortLabel: 'Sem 8', yearLabel: '4th Year', sgpa: 8.75 }
      ];
    } else {
      this.formData = {
        rollNumber: '2024-CSE-012',
        firstName: 'Sandhya',
        lastName: 'Kulkarni',
        email: 'sandhya.k@mgmcet.ac.in',
        phone: '+91 98765 12345',
        dob: '2002-08-14',
        gender: 'Female',
        aadharNumber: '9876 5432 1098',
        panNumber: 'FGHIJ5678K',
        address: 'Sector 15, Kamothe, Navi Mumbai, Maharashtra - 410209',
        collegeName: 'MGM College of Engineering & Technology, Kamothe',
        batchYear: '2024',
        branch: 'Computer Science & Engineering',
        degree: 'Bachelor of Technology (B.Tech)',
        cgpa: '8.40',
        tenthPercentage: 85,
        twelfthPercentage: 84,
        activeBacklogs: 0,
        gapYears: 0,
        gradDate: '2024-06-30',
        resumeUploaded: true,
        linkedin: 'https://linkedin.com/in/sandhya-k',
        github: 'https://github.com/sandhya-k',
        portfolio: 'https://sandhya-dev.io'
      };
      this.semesters = [
        { semNumber: 1, label: 'Semester 1', shortLabel: 'Sem 1', yearLabel: '1st Year', sgpa: 8.10 },
        { semNumber: 2, label: 'Semester 2', shortLabel: 'Sem 2', yearLabel: '1st Year', sgpa: 8.25 },
        { semNumber: 3, label: 'Semester 3', shortLabel: 'Sem 3', yearLabel: '2nd Year', sgpa: 8.30 },
        { semNumber: 4, label: 'Semester 4', shortLabel: 'Sem 4', yearLabel: '2nd Year', sgpa: 8.50 },
        { semNumber: 5, label: 'Semester 5', shortLabel: 'Sem 5', yearLabel: '3rd Year', sgpa: 8.45 },
        { semNumber: 6, label: 'Semester 6', shortLabel: 'Sem 6', yearLabel: '3rd Year', sgpa: 8.60 },
        { semNumber: 7, label: 'Semester 7', shortLabel: 'Sem 7', yearLabel: '4th Year', sgpa: 8.40 },
        { semNumber: 8, label: 'Semester 8', shortLabel: 'Sem 8', yearLabel: '4th Year', sgpa: 8.60 }
      ];
    }
    this.syncFormDataCgpa();
  }

  // Real-time Aggregate CGPA calculated across all entered semesters
  get aggregateCgpa(): number {
    const valid = this.semesters.filter(
      s => s.sgpa !== null && s.sgpa !== undefined && (s.sgpa as any) !== '' && !isNaN(Number(s.sgpa)) && Number(s.sgpa) > 0
    );
    if (valid.length === 0) return 0;
    const sum = valid.reduce((total, s) => total + Number(s.sgpa), 0);
    return parseFloat((sum / valid.length).toFixed(2));
  }

  // Number of completed semesters entered
  get completedSemestersCount(): number {
    return this.semesters.filter(
      s => s.sgpa !== null && s.sgpa !== undefined && (s.sgpa as any) !== '' && !isNaN(Number(s.sgpa)) && Number(s.sgpa) > 0
    ).length;
  }

  get highestSemesterText(): string {
    const valid = this.semesters.filter(
      s => s.sgpa !== null && s.sgpa !== undefined && (s.sgpa as any) !== '' && !isNaN(Number(s.sgpa)) && Number(s.sgpa) > 0
    );
    if (valid.length === 0) return 'N/A';
    const highest = valid.reduce((prev, curr) => (Number(curr.sgpa) > Number(prev.sgpa) ? curr : prev), valid[0]);
    return `${highest.shortLabel} (${Number(highest.sgpa).toFixed(2)})`;
  }

  get lowestSemesterText(): string {
    const valid = this.semesters.filter(
      s => s.sgpa !== null && s.sgpa !== undefined && (s.sgpa as any) !== '' && !isNaN(Number(s.sgpa)) && Number(s.sgpa) > 0
    );
    if (valid.length === 0) return 'N/A';
    const lowest = valid.reduce((prev, curr) => (Number(curr.sgpa) < Number(prev.sgpa) ? curr : prev), valid[0]);
    return `${lowest.shortLabel} (${Number(lowest.sgpa).toFixed(2)})`;
  }

  onSemesterChange(): void {
    this.syncFormDataCgpa();
  }

  syncFormDataCgpa(): void {
    if (this.aggregateCgpa > 0) {
      this.formData.cgpa = this.aggregateCgpa.toFixed(2);
    }
  }

  resetSemesters(): void {
    this.semesters.forEach(s => s.sgpa = null);
    this.formData.cgpa = '0.00';
    this.notificationService.showInfo('Semester CGPA records cleared. You can now re-enter grades.');
  }

  prefillSampleGrades(): void {
    const sampleGrades = [8.20, 8.35, 8.40, 8.55, 8.60, 8.50, 8.65, 8.75];
    this.semesters.forEach((s, idx) => {
      s.sgpa = sampleGrades[idx] || 8.50;
    });
    this.syncFormDataCgpa();
    this.notificationService.showSuccess('Prefilled all 8 semesters with verified academic performance.', 'Grades Loaded');
  }

  onSubmit(): void {
    this.syncFormDataCgpa();
    this.isSaved = true;
    this.notificationService.showSuccess(
      `Student record for ${this.formData.firstName} ${this.formData.lastName} updated successfully! Aggregate CGPA: ${this.aggregateCgpa.toFixed(2)}`,
      'Profile Saved'
    );
    setTimeout(() => {
      this.router.navigate(['/college/students']);
    }, 1200);
  }
}
