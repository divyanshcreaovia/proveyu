import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  activeTab = 'notifications';
  toastMessage: string | null = null;

  notificationSettings = [
    { id: 'email', label: 'Email Notifications', desc: 'Get instant emails about test bookings, admit cards, and interview offers', checked: true },
    { id: 'sms', label: 'SMS & WhatsApp Alerts', desc: 'Important exam reminders, OTPs, and venue updates directly to your mobile', checked: true },
    { id: 'push', label: 'Push Notifications', desc: 'Real-time updates on your mobile app and web browser', checked: true }
  ];

  notificationTypes = [
    { id: 'test_reminders', label: 'Test Reminders & Admit Cards', checked: true },
    { id: 'interview_invites', label: 'Direct Interview Invites from Recruiters', checked: true },
    { id: 'application_updates', label: 'Skill Passport Verification & Score updates', checked: true },
    { id: 'job_offers', label: 'Job Requirement Matches', checked: true },
    { id: 'marketing_updates', label: 'Career Fair & Placement Events', checked: false },
    { id: 'newsletter', label: 'Monthly Skill Benchmarking Reports', checked: false }
  ];

  privacySettings = [
    { id: 'passport_public', label: 'Public Skill Passport Profile', desc: 'Allow verified recruiters to view your invigilated assessment scores', checked: true },
    { id: 'searchable', label: 'Recruiter Search Visibility', desc: 'Appear in candidate search queries for matching tech stacks', checked: true },
    { id: 'show_email', label: 'Show Email to Verified Employers', desc: 'Allow hiring partners to email interview invites directly', checked: false }
  ];

  accountDetails = {
    name: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    phone: '+91 98765 43210',
    location: 'Bangalore, India'
  };

  savePreferences() {
    this.showToast('Candidate settings and preferences saved successfully!');
  }

  showToast(message: string) {
    this.toastMessage = message;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }
}

