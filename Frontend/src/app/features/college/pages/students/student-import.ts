import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-student-import',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './student-import.html',
  styleUrl: './student-import.scss',
})
export class StudentImport {
  isFileUploaded = false;
  fileName = '';
  isImported = false;

  previewRows = [
    { roll: '2024-CSE-001', name: 'Rahul Sharma', email: 'rahul@mgmcet.ac.in', batch: '2024', branch: 'CSE', status: 'Valid', valid: true },
    { roll: '2024-CSE-002', name: 'Priya Desai', email: 'priya@mgmcet.ac.in', batch: '2024', branch: 'IT', status: 'Valid', valid: true },
    { roll: '2024-CSE-003', name: 'Arun Patel', email: 'arun@mgmcet.ac.in', batch: '2024', branch: 'CSE', status: 'Valid', valid: true },
    { roll: '2024-CSE-004', name: 'Neha Deshmukh', email: 'neha_invalid_email', batch: '2024', branch: 'IT', status: 'Invalid Email', valid: false },
    { roll: '2024-CSE-001', name: 'Duplicate Rahul', email: 'rahul2@mgmcet.ac.in', batch: '2024', branch: 'CSE', status: 'Duplicate Roll', valid: false },
  ];

  errors = [
    'Row 4: Invalid email format (missing @ domain)',
    'Row 5: Duplicate roll number (2024-CSE-001 already exists in system)',
    'Row 12: Missing required field (student full name)',
    'Row 15: Phone number invalid format (not 10 digits)'
  ];

  constructor(private router: Router) {}

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.fileName = file.name;
      this.isFileUploaded = true;
    }
  }

  triggerSimulatedUpload() {
    this.fileName = 'batch_2024_students_roster.csv';
    this.isFileUploaded = true;
  }

  confirmImport() {
    this.isImported = true;
    setTimeout(() => {
      this.router.navigate(['/college/students']);
    }, 1500);
  }

  resetImport() {
    this.isFileUploaded = false;
    this.fileName = '';
    this.isImported = false;
  }
}
