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
    this.loadProfile();
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
        this.saveNotification = 'Profile saved successfully!';
        sessionStorage.setItem('candidateFullName', this.recruiterInfo.contactName);
        // Reload profile data from server to confirm saved values
        this.loadProfile();
        setTimeout(() => {
          this.saveNotification = '';
        }, 2500);
      },
      error: (err) => {
        console.error('Failed to save profile', err);
        alert('Failed to save profile.');
      }
    });
  }

  loadProfile() {
    this.http.get<any>('http://localhost:8080/api/v1/recruiters/profile', {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    }).subscribe({
      next: (res) => {
        if (res.data) {
          this.recruiterInfo = {
            contactName: res.data.contactName || '',
            workEmail:   res.data.workEmail   || '',
            phone:       res.data.phone        || '',
            city:        res.data.city         || '',
            companyName: res.data.companyName  || '',
            orgType:     res.data.orgType      || '',
            website:     res.data.website      || '',
            headline:    res.data.headline     || '',
            about:       res.data.about        || '',
            avatar:      res.data.avatar       || '',
            linkedin:    res.data.linkedin     || '',
          };
          this.hiringPreferences = {
            hiringVolume:    res.data.hiringVolume    || '',
            candidateLevel:  res.data.candidateLevel  || '',
            primaryTrack:    res.data.primaryTrack    || '',
            preferredCities: res.data.preferredCities || '',
            customNotes:     res.data.customNotes     || '',
          };
          this.calculateCompletionScore();
          this.cdr.detectChanges();
        }
      },
      error: (err) => console.error('Failed to load profile', err)
    });
  }

  calculateCompletionScore() {
    // Each field has a weight. Total = 100%
    const fields: { value: string; weight: number }[] = [
      { value: this.recruiterInfo.companyName,  weight: 15 },
      { value: this.recruiterInfo.contactName,  weight: 10 },
      { value: this.recruiterInfo.workEmail,    weight: 5  },
      { value: this.recruiterInfo.phone,        weight: 10 },
      { value: this.recruiterInfo.city,         weight: 10 },
      { value: this.recruiterInfo.orgType,      weight: 10 },
      { value: this.recruiterInfo.website,      weight: 10 },
      { value: this.recruiterInfo.headline,     weight: 10 },
      { value: this.recruiterInfo.about,        weight: 10 },
      { value: this.hiringPreferences.hiringVolume, weight: 10 },
    ];

    const earned = fields
      .filter(f => f.value && f.value.trim() !== '')
      .reduce((sum, f) => sum + f.weight, 0);

    this.completionScore = Math.min(earned, 100);
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
