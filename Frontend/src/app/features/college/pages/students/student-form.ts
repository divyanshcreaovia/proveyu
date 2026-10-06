import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-college-student-form',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './student-form.html',
  styleUrl: './student-form.scss',
})
export class StudentForm {
  formData = {
    rollNumber: '2024-CSE-012',
    firstName: 'Sandhya',
    lastName: 'Kulkarni',
    email: 'sandhya.k@mgmcet.ac.in',
    phone: '+91 98765 12345',
    dob: '2002-08-14',
    gender: 'Female',
    address: 'Sector 15, Kamothe, Navi Mumbai',
    collegeName: 'MGM CET, Kamothe',
    batchYear: '2024',
    branch: 'Computer Science Engineering',
    degree: 'Bachelor of Engineering (B.E.)',
    cgpa: '8.4',
    gradDate: '2024-06-30',
    resumeUploaded: true,
    linkedin: 'https://linkedin.com/in/sandhya-k',
    github: 'https://github.com/sandhya-k',
    portfolio: 'https://sandhya-dev.io'
  };

  isSaved = false;

  constructor(private router: Router) {}

  onSubmit() {
    this.isSaved = true;
    setTimeout(() => {
      this.router.navigate(['/college/students']);
    }, 1200);
  }
}
