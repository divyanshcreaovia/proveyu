import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface EducationItem {
  id: number;
  degree: string;
  institution: string;
  year: string;
  score: string;
}

export interface ExperienceItem {
  id: number;
  company: string;
  role: string;
  duration: string;
  description: string;
}

export interface SkillItem {
  name: string;
  level: 'Beginner' | 'Intermediate' | 'Expert';
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  activeTab: 'personal' | 'education' | 'professional' | 'skills' | 'preferences' | 'resume' = 'personal';
  newSkillInput = '';

  candidateInfo = {
    fullName: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    phone: '+91 98765 43210',
    location: 'Bengaluru, Karnataka',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
    headline: 'Software Engineer | Full Stack & Systems',
    linkedin: 'https://linkedin.com/in/rahulsharma',
    github: 'https://github.com/rahulsharma',
    portfolio: 'https://rahulsharma.dev',
    bio: 'Passionate software engineer with 2+ years of hands-on experience building scalable web applications. Strong foundations in DSA, System Design, and Modern Cloud Architecture.'
  };

  educationList: EducationItem[] = [
    { id: 1, degree: 'B.Tech in Computer Science', institution: 'NIT Karnataka, Surathkal', year: '2020 - 2024', score: '8.8 CGPA' },
    { id: 2, degree: 'Higher Secondary (Class XII)', institution: 'Delhi Public School', year: '2018 - 2020', score: '94.2%' }
  ];

  experienceList: ExperienceItem[] = [
    { id: 1, company: 'Microsoft', role: 'Software Engineering Intern', duration: 'Jan 2024 - Jun 2024', description: 'Developed microservices using Node.js & TypeScript, optimizing API response latencies by 35%.' },
    { id: 2, company: 'TechSolutions', role: 'Frontend Developer Intern', duration: 'May 2023 - Aug 2023', description: 'Built responsive dashboard interfaces using Angular & Bootstrap.' }
  ];

  skillsList: SkillItem[] = [
    { name: 'Data Structures & Algorithms', level: 'Expert' },
    { name: 'Angular & TypeScript', level: 'Expert' },
    { name: 'Java & Spring Boot', level: 'Intermediate' },
    { name: 'SQL & PostgreSQL', level: 'Intermediate' },
    { name: 'System Design', level: 'Intermediate' },
    { name: 'Docker & AWS', level: 'Beginner' }
  ];

  preferences = {
    desiredRole: 'Software Engineer / SDE-1',
    jobType: 'Full-time',
    preferredLocations: 'Bengaluru, Hyderabad, Remote',
    expectedCtc: '₹18 - ₹25 LPA',
    noticePeriod: '15 Days / Immediate'
  };

  resume = {
    fileName: 'Rahul_Sharma_Resume_2024.pdf',
    fileSize: '1.4 MB',
    lastUpdated: '18 Sep, 2024'
  };

  // Form Modals / New Item DTOs
  newEducation: Partial<EducationItem> = {};
  showEducationForm = false;

  newExperience: Partial<ExperienceItem> = {};
  showExperienceForm = false;

  constructor(private notificationService: NotificationService) {}

  ngOnInit() {}

  get completionScore(): number {
    let score = 30; // base personal info
    if (this.educationList.length > 0) score += 20;
    if (this.experienceList.length > 0) score += 20;
    if (this.skillsList.length >= 3) score += 15;
    if (this.resume.fileName) score += 15;
    return Math.min(score, 100);
  }

  saveProfile() {
    this.notificationService.showSuccess('Profile information updated successfully!', 'Profile Updated');
  }

  savePreferences() {
    this.notificationService.showModal({
      type: 'warning',
      title: 'Notice Period Verification Required',
      message: `Notice Period setting: "${this.preferences.noticePeriod || 'Not Specified'}".\n\nEnter your exact notice period — Immediate, 30, 45, 60, or 90 days. Accurate details help match you with the right recruiter and opportunity.`,
      confirmText: 'Confirm & Save Preferences',
      cancelText: 'Edit Notice Period',
      onConfirm: () => {
        this.notificationService.showSuccess(`Career preferences updated! Notice Period saved as: ${this.preferences.noticePeriod}.`, 'Preferences Saved');
      }
    });
  }

  addSkill() {
    if (this.newSkillInput.trim()) {
      this.skillsList.push({ name: this.newSkillInput.trim(), level: 'Intermediate' });
      this.newSkillInput = '';
      this.notificationService.showSuccess('New skill added to candidate profile!', 'Skill Added');
    }
  }

  removeSkill(index: number) {
    const removed = this.skillsList[index]?.name;
    this.skillsList.splice(index, 1);
    this.notificationService.showInfo(`Skill "${removed}" removed from profile.`, 'Skill Removed');
  }

  addEducation() {
    if (this.newEducation.degree && this.newEducation.institution) {
      this.educationList.push({
        id: Date.now(),
        degree: this.newEducation.degree,
        institution: this.newEducation.institution,
        year: this.newEducation.year || '2024',
        score: this.newEducation.score || 'N/A'
      });
      this.newEducation = {};
      this.showEducationForm = false;
      this.notificationService.showSuccess('Education record added successfully!', 'Education Saved');
    }
  }

  deleteEducation(id: number) {
    this.educationList = this.educationList.filter(e => e.id !== id);
    this.notificationService.showInfo('Education record deleted.', 'Deleted');
  }

  addExperience() {
    if (this.newExperience.company && this.newExperience.role) {
      this.experienceList.push({
        id: Date.now(),
        company: this.newExperience.company,
        role: this.newExperience.role,
        duration: this.newExperience.duration || '2024',
        description: this.newExperience.description || ''
      });
      this.newExperience = {};
      this.showExperienceForm = false;
      this.notificationService.showSuccess('Professional experience added successfully!', 'Experience Saved');
    }
  }

  deleteExperience(id: number) {
    this.experienceList = this.experienceList.filter(e => e.id !== id);
    this.notificationService.showInfo('Experience record deleted.', 'Deleted');
  }

  uploadResumeSimulation(event: any) {
    const file = event.target.files?.[0];
    if (file) {
      this.resume.fileName = file.name;
      this.resume.fileSize = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
      this.resume.lastUpdated = 'Just now';
      this.notificationService.showSuccess('New resume PDF uploaded successfully!', 'Resume Uploaded');
    }
  }

  changeAvatar() {
    const avatars = [
      'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80'
    ];
    const currentIdx = avatars.indexOf(this.candidateInfo.avatar);
    const nextAvatar = avatars[(currentIdx + 1) % avatars.length];
    this.candidateInfo.avatar = nextAvatar;
    this.notificationService.showSuccess('Profile avatar updated!', 'Avatar Updated');
  }

  shareProfile() {
    navigator.clipboard?.writeText(window.location.href);
    this.notificationService.showSuccess('Profile share link copied to clipboard!', 'Link Copied');
  }
}
