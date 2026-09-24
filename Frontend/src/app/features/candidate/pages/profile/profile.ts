import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

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
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);

  activeTab: 'personal' | 'education' | 'professional' | 'skills' | 'preferences' | 'resume' = 'personal';
  saveNotification: string | null = null;
  newSkillInput = '';

  candidateInfo = {
    fullName: '',
    email: '',
    phone: '',
    location: '',
    avatar: 'https://ui-avatars.com/api/?name=User&background=random',
    headline: '',
    linkedin: '',
    github: '',
    portfolio: '',
    bio: ''
  };

  educationList: EducationItem[] = [];

  experienceList: ExperienceItem[] = [];

  skillsList: SkillItem[] = [];

  preferences = {
    desiredRole: '',
    jobType: '',
    preferredLocations: '',
    expectedCtc: '',
    noticePeriod: ''
  };

  resume = {
    fileName: '',
    fileSize: '',
    lastUpdated: ''
  };

  // Form Modals / New Item DTOs
  newEducation: Partial<EducationItem> = {};
  showEducationForm = false;

  newExperience: Partial<ExperienceItem> = {};
  showExperienceForm = false;

  ngOnInit() {
    const token = localStorage.getItem('token');
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

    this.http.get('http://localhost:8080/api/v1/candidates/profile', { headers }).subscribe({
      next: (res: any) => {
        if (res.success && res.data) {
          if (res.data.skillsList) {
            this.skillsList = res.data.skillsList.split(',').map((s: string) => ({ name: s.trim(), level: 'Intermediate' }));
          }
          if (res.data.resumeUrl) {
            this.resume.fileName = res.data.resumeUrl.split('/').pop() || 'resume.pdf';
          }
          
          this.candidateInfo.fullName = res.data.fullName || '';
          this.candidateInfo.email = res.data.email || '';
          this.candidateInfo.phone = res.data.phone || '';
          this.candidateInfo.location = res.data.location || '';
          this.candidateInfo.headline = res.data.headline || '';
          this.candidateInfo.linkedin = res.data.linkedinUrl || '';
          this.candidateInfo.github = res.data.githubUrl || '';
          this.candidateInfo.portfolio = res.data.portfolioUrl || '';
          this.candidateInfo.bio = res.data.bio || '';
          
          if (this.candidateInfo.fullName) {
             this.candidateInfo.avatar = `https://ui-avatars.com/api/?name=${encodeURIComponent(this.candidateInfo.fullName)}&background=random`;
             localStorage.setItem('candidateFullName', this.candidateInfo.fullName);
          }
          this.cdr.detectChanges();
        }
      },
      error: (err) => console.error('Failed to load profile', err)
    });
  }

  get completionScore(): number {
    let score = 30; // base personal info
    if (this.educationList.length > 0) score += 20;
    if (this.experienceList.length > 0) score += 20;
    if (this.skillsList.length >= 3) score += 15;
    if (this.resume.fileName) score += 15;
    return Math.min(score, 100);
  }

  saveProfile() {
    const payload = {
      experienceTrack: this.experienceList.length > 0 ? 'EXPERIENCED' : 'FRESHER',
      yearsOfExperience: this.experienceList.length,
      uanNumber: '100918273645',
      skillsList: this.skillsList.map(s => s.name).join(', '),
      resumeUrl: 'https://s3.amazonaws.com/proveyu-resumes/' + this.resume.fileName,
      fullName: this.candidateInfo.fullName,
      phone: this.candidateInfo.phone,
      location: this.candidateInfo.location,
      headline: this.candidateInfo.headline,
      linkedinUrl: this.candidateInfo.linkedin,
      githubUrl: this.candidateInfo.github,
      portfolioUrl: this.candidateInfo.portfolio,
      bio: this.candidateInfo.bio
    };

    const token = localStorage.getItem('token');
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

    this.http.post('http://localhost:8080/api/v1/candidates/profile', payload, { headers }).subscribe({
      next: () => {
        this.triggerToast('Profile information updated successfully!');
      },
      error: (err) => {
        console.error('Failed to save profile', err);
        this.triggerToast('Failed to update profile.');
      }
    });
  }

  addSkill() {
    if (this.newSkillInput.trim()) {
      this.skillsList.push({ name: this.newSkillInput.trim(), level: 'Intermediate' });
      this.newSkillInput = '';
      this.triggerToast('New skill added to candidate profile!');
    }
  }

  removeSkill(index: number) {
    this.skillsList.splice(index, 1);
    this.triggerToast('Skill removed.');
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
      this.triggerToast('Education record added successfully!');
    }
  }

  deleteEducation(id: number) {
    this.educationList = this.educationList.filter(e => e.id !== id);
    this.triggerToast('Education record deleted.');
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
      this.triggerToast('Professional experience added!');
    }
  }

  deleteExperience(id: number) {
    this.experienceList = this.experienceList.filter(e => e.id !== id);
    this.triggerToast('Experience record deleted.');
  }

  uploadResumeSimulation(event: any) {
    const file = event.target.files?.[0];
    if (file) {
      this.resume.fileName = file.name;
      this.resume.fileSize = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
      this.resume.lastUpdated = 'Just now';
      this.triggerToast('New resume PDF uploaded successfully!');
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
    this.triggerToast('Profile avatar updated!');
  }

  shareProfile() {
    navigator.clipboard?.writeText(window.location.href);
    this.triggerToast('Profile share link copied to clipboard!');
  }

  private triggerToast(msg: string) {
    this.saveNotification = msg;
    setTimeout(() => {
      this.saveNotification = null;
    }, 3500);
  }
}
