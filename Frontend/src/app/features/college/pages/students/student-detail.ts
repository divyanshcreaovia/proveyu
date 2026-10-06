import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-student-detail',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './student-detail.html',
  styleUrl: './student-detail.scss',
})
export class StudentDetail {
  student = {
    id: '001',
    roll: '2024-CSE-001',
    name: 'Rahul Sharma',
    email: 'rahul@college.edu',
    phone: '+91-9876543210',
    location: 'Mumbai',
    batch: '2024',
    branch: 'Computer Science Engineering',
    college: 'MGM CET, Kamothe',
    degree: 'Bachelor of Engineering (B.E.)',
    cgpa: '8.5 / 10',
    dob: '05 Jan 2002',
    gradDate: 'June 2024',
    skills: ['Java', 'Spring Boot', 'React', 'PostgreSQL', 'Docker'],
    languages: ['English', 'Hindi', 'Marathi'],
    tier: 'Tier 1 (Excellent)',
    passportScore: '78 / 100',
    percentile: '92nd Percentile',
    proctorRating: '4.8 / 5 ⭐',
    placementStatus: 'Placed',
    company: 'TCS',
    position: 'Associate Software Engineer',
    ctc: '₹3.5L per annum',
    offerDate: 'Oct 5, 2024',
    joiningDate: 'Jan 2025'
  };

  testHistory = [
    { name: 'Test 1', score: '78/100', tier: 'Tier 1', date: 'Sep 15', coding: '28/30', aptitude: '26/30', comm: '24/30' },
    { name: 'Test 2', score: '82/100', tier: 'Tier 1', date: 'Sep 29', coding: '29/30', aptitude: '26/30', comm: '27/30' },
    { name: 'Test 3', score: '85/100', tier: 'Tier 1', date: 'Oct 13', coding: '30/30', aptitude: '27/30', comm: '28/30' }
  ];

  collegeDrives = [
    { company: 'TCS (On-Campus Drive)', role: 'Software Engineer Trainee', status: 'Selected & Offered (₹3.5L)', badge: 'bg-success-subtle text-success border border-success-subtle' },
    { company: 'Capgemini Campus Drive', role: 'Analyst', status: 'Shortlisted for HR Round', badge: 'bg-primary-subtle text-primary border border-primary-subtle' },
    { company: 'Tech Mahindra Drive', role: 'Software Engineer', status: 'Aptitude Test Cleared', badge: 'bg-info-subtle text-info border border-info-subtle' }
  ];

  recruiterInvites = [
    { company: 'Wipro Technologies', role: 'Full Stack Developer', status: 'Interview Scheduled Oct 25', badge: 'bg-primary-subtle text-primary border border-primary-subtle' },
    { company: 'Amdocs India', role: 'Backend Engineer', status: 'Invite Sent Oct 18', badge: 'bg-warning-subtle text-warning border border-warning-subtle' },
    { company: 'Infosys Limited', role: 'System Engineer', status: 'Auto-Matched based on Tier 1', badge: 'bg-info-subtle text-info border border-info-subtle' }
  ];
}
