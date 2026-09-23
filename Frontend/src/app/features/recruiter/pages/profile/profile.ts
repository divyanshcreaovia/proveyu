import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

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
export class Profile {
  activeTab: 'company' | 'hiring' | 'verification' | 'team' = 'company';
  completionScore: number = 95;
  saveNotification: string = '';

  recruiterInfo = {
    contactName: 'Vikram Mehta',
    workEmail: 'vikram.mehta@apexplacements.com',
    phone: '+91 98123 45678',
    city: 'Bengaluru',
    companyName: 'Apex Placement Agency',
    orgType: 'agency', // 'agency' | 'startup' | 'corporate' | 'services'
    website: 'https://apexplacements.com',
    headline: 'Premier Tech Placement Agency & Candidate Invigilation Partner',
    about: 'We connect top tech enterprises with invigilated, 100% verified candidate skill passports across Java, React, QA, and Fullstack Engineering.',
    avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=80',
    linkedin: 'https://linkedin.com/in/vikram-mehta-apex',
  };

  hiringPreferences = {
    hiringVolume: '5-20',
    candidateLevel: 'fresher',
    primaryTrack: 'universal',
    preferredCities: 'Bengaluru, Hyderabad, Pune, Delhi NCR',
    customNotes: 'Looking for verified candidate passports with >85 percentile scores in coding lab invigilation.',
  };

  verificationData = {
    domainVerified: true,
    domainName: 'apexplacements.com',
    gstVerified: true,
    gstNumber: '29ABCDE1234F1ZH',
    cinVerified: true,
    cinNumber: 'U72900KA2022PTC123456',
  };

  teamMembers: TeamMember[] = [
    {
      id: 1,
      name: 'Vikram Mehta',
      role: 'Agency Lead & Admin',
      email: 'vikram.mehta@apexplacements.com',
      avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=80',
    },
    {
      id: 2,
      name: 'Ananya Sen',
      role: 'Senior Tech Recruiter',
      email: 'ananya.sen@apexplacements.com',
      avatar: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80',
    },
    {
      id: 3,
      name: 'Rohan Verma',
      role: 'Invigilation Specialist',
      email: 'rohan.verma@apexplacements.com',
      avatar: 'https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150&auto=format&fit=crop&q=80',
    },
  ];

  newMember = {
    name: '',
    role: 'Senior Tech Recruiter',
    email: '',
    avatar: '',
  };
  showMemberForm = false;

  saveProfile() {
    this.saveNotification = 'Company profile & hiring settings saved successfully!';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
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
      avatar: this.newMember.avatar || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
    });
    this.newMember = { name: '', role: 'Senior Tech Recruiter', email: '', avatar: '' };
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
