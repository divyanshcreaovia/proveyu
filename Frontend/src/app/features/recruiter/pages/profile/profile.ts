import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface TeamMember {
  id: number;
  name: string;
  role: string;
  email: string;
  avatar: string;
}

@Component({
  selector: 'app-recruiter-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  activeTab: 'company' | 'hiring' | 'verification' | 'team' = 'company';
  completionScore: number = 0;
  saveNotification: string = '';
  isSaving: boolean = false;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  recruiterInfo = {
    contactName: '',
    workEmail: '',
    phone: '',
    city: '',
    companyName: '',
    orgType: 'agency',
    website: '',
    headline: '',
    about: '',
    avatar: '',
    linkedin: '',
  };

  hiringPreferences = {
    hiringVolume: '5-20',
    candidateLevel: 'all',
    primaryTrack: 'universal',
    preferredCities: '',
    customNotes: '',
  };

  verificationData = {
    domainVerified: false,
    domainName: '',
    gstVerified: false,
    gstNumber: '',
    cinVerified: false,
    cinNumber: '',
  };

  teamMembers: TeamMember[] = [];

  newMember = {
    name: '',
    role: 'Senior Tech Recruiter',
    email: '',
    avatar: '',
  };
  showMemberForm = false;

  private getAuthHeaders(): { [header: string]: string } {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  ngOnInit() {
    this.http.get<any>('http://localhost:8080/api/v1/recruiters/profile', {
      headers: this.getAuthHeaders()
    }).subscribe({
      next: (res) => {
        if (res && res.data) {
          const d = res.data;
          this.recruiterInfo = {
            contactName: d.contactName || '',
            workEmail: d.workEmail || '',
            phone: d.phone || '',
            city: d.city || '',
            companyName: d.companyName || '',
            orgType: d.orgType || 'agency',
            website: d.website || '',
            headline: d.headline || '',
            about: d.about || '',
            avatar: d.avatar || '',
            linkedin: d.linkedin || '',
          };
          this.hiringPreferences = {
            hiringVolume: d.hiringVolume || '5-20',
            candidateLevel: d.candidateLevel || 'all',
            primaryTrack: d.primaryTrack || 'universal',
            preferredCities: d.preferredCities || '',
            customNotes: d.customNotes || '',
          };
          this.calculateCompletionScore();
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        console.error('Failed to load profile', err);
      }
    });
  }

  calculateCompletionScore() {
    let filled = 0;
    const checks = [
      this.recruiterInfo.companyName,
      this.recruiterInfo.contactName,
      this.recruiterInfo.workEmail,
      this.recruiterInfo.phone,
      this.recruiterInfo.city,
      this.recruiterInfo.orgType,
      this.recruiterInfo.website,
      this.recruiterInfo.headline,
      this.hiringPreferences.hiringVolume,
      this.hiringPreferences.candidateLevel
    ];
    checks.forEach(val => {
      if (val && val.toString().trim().length > 0) {
        filled++;
      }
    });
    this.completionScore = Math.min(100, Math.round((filled / checks.length) * 100));
  }

  saveProfile() {
    const trimmedCompanyName = (this.recruiterInfo.companyName || '').trim();
    if (!trimmedCompanyName) {
      alert('Please enter a Company Name before saving!');
      return;
    }

    this.isSaving = true;
    const payload = {
      ...this.hiringPreferences,
      ...this.recruiterInfo,
      companyName: trimmedCompanyName
    };
    console.log('PAYLOAD BEFORE SAVE:', payload);

    this.http.put('http://localhost:8080/api/v1/recruiters/profile', payload, {
      headers: this.getAuthHeaders()
    }).subscribe({
      next: () => {
        this.isSaving = false;
        this.saveNotification = 'Company profile & hiring settings saved successfully!';
        if (this.recruiterInfo.contactName) {
          sessionStorage.setItem('candidateFullName', this.recruiterInfo.contactName);
        }
        this.calculateCompletionScore();
        this.cdr.detectChanges();
        setTimeout(() => {
          this.saveNotification = '';
          this.cdr.detectChanges();
        }, 3000);
      },
      error: (err) => {
        this.isSaving = false;
        console.error('Failed to save profile', err);
        alert('Failed to save profile. Please make sure the backend is reachable.');
        this.cdr.detectChanges();
      }
    });
  }

  shareProfile() {
    this.saveNotification = 'Public Recruiter Profile link copied to clipboard!';
    setTimeout(() => {
      this.saveNotification = '';
      this.cdr.detectChanges();
    }, 3000);
  }

  changeAvatar() {
    this.saveNotification = 'Avatar update simulation triggered!';
    setTimeout(() => {
      this.saveNotification = '';
      this.cdr.detectChanges();
    }, 3000);
  }

  onMemberAvatarChange(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    if (fileInput.files && fileInput.files[0]) {
      const file = fileInput.files[0];
      const reader = new FileReader();
      reader.onload = (e) => {
        this.newMember.avatar = e.target?.result as string;
        this.cdr.detectChanges();
      };
      reader.readAsDataURL(file);
    }
  }

  addTeamMember() {
    if (!this.newMember.name || !this.newMember.email) return;
    this.teamMembers.push({
      id: Date.now(),
      name: this.newMember.name,
      role: this.newMember.role,
      email: this.newMember.email,
      avatar: this.newMember.avatar || '',
    });
    this.newMember = { name: '', role: 'Senior Tech Recruiter', email: '', avatar: '' };
    this.showMemberForm = false;
    this.saveNotification = 'New team member invited successfully!';
    setTimeout(() => {
      this.saveNotification = '';
      this.cdr.detectChanges();
    }, 3000);
  }

  deleteTeamMember(id: number) {
    this.teamMembers = this.teamMembers.filter((m) => m.id !== id);
    this.saveNotification = 'Team member removed.';
    setTimeout(() => {
      this.saveNotification = '';
      this.cdr.detectChanges();
    }, 3000);
  }
}
