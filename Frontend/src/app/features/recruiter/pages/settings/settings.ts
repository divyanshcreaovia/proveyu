import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-recruiter-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  // Toast System
  toastMessage: string | null = null;

  // Basic Notification Settings Toggles
  notificationSettings = [
    { key: 'newPassport', label: 'New Candidate Invigilated Passport Alert', desc: 'Notify when a candidate submits an audited ProveYu passport.', email: true, app: true },
    { key: 'inviteAccepted', label: 'Interview Invitation Accepted', desc: 'Instant alert when candidate accepts slot or interview invite.', email: true, app: true },
    { key: 'thresholdAlert', label: 'High Score Candidate Alert (80%+)', desc: 'Notify hiring managers when a top tier candidate applies.', email: true, app: false },
    { key: 'creditWarning', label: 'Low Verification Credit Warning', desc: 'Alert when remaining verification credits drop below 15.', email: true, app: true },
    { key: 'weeklyDigest', label: 'Weekly Recruitment & Pipeline Summary', desc: 'Receive Monday morning email report on active hiring stats.', email: true, app: false }
  ];

  // Actions
  saveNotifications() {
    this.showToast('Notification preferences saved successfully!');
  }

  showToast(message: string) {
    this.toastMessage = message;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }
}



