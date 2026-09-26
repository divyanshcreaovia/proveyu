import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { OnInit } from '@angular/core';

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

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.http.get<any>('http://localhost:8080/api/v1/recruiters/profile', {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    }).subscribe({
      next: (res) => {
        if (res.data) {
          this.recruiterInfo = { ...this.recruiterInfo, ...res.data };
          this.hiringPreferences = { ...this.hiringPreferences, ...res.data };
          this.cdr.detectChanges();
        }
      },
      error: (err) => console.error('Failed to load profile', err)
    });
  }

  recruiterInfo = {
    contactName: '',
    workEmail: '',
    phone: '',
    city: '',
    companyName: '',
    orgType: '',
    website: '',
    headline: '',
    about: '',
    avatar: '',
    linkedin: '',
  };

  hiringPreferences = {
    hiringVolume: '',
    candidateLevel: '',
    primaryTrack: '',
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

  saveProfile() {
    const payload = { ...this.recruiterInfo, ...this.hiringPreferences };
    console.log("PAYLOAD BEFORE SAVE:", payload);
    if (!payload.companyName) {
      alert("Please enter a Company Name before saving!");
      return;
    }
    this.http.put('http://localhost:8080/api/v1/recruiters/profile', payload, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    }).subscribe({
      next: () => {
        this.saveNotification = 'Company profile & hiring settings saved successfully!';
        sessionStorage.setItem('candidateFullName', this.recruiterInfo.contactName);
        setTimeout(() => {
          this.saveNotification = '';
          window.location.reload();
        }, 1500);
      },
      error: (err) => {
        console.error('Failed to save profile', err);
        alert('Failed to save profile.');
      }
    });
  }

  shareProfile() {
    this.saveNotification = 'Public Recruiter Profile link copied to clipboard!';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
  }

  changeAvatar() {
    this.saveNotification = 'Avatar update simulation triggered!';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
  }

  onMemberAvatarChange(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    if (fileInput.files && fileInput.files[0]) {
      const file = fileInput.files[0];
      const reader = new FileReader();
      reader.onload = (e) => {
        this.newMember.avatar = e.target?.result as string;
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
    this.newMember = { name: '', role: 'Recruiter', email: '', avatar: '' };
    this.showMemberForm = false;
    this.saveNotification = 'New team member invited successfully!';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
  }

  deleteTeamMember(id: number) {
    this.teamMembers = this.teamMembers.filter((m) => m.id !== id);
    this.saveNotification = 'Team member removed.';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
  }
}
