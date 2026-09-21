import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  activeTab = 'notifications';

  notificationSettings = [
    { id: 'email', label: 'Email Notifications', desc: 'Get emails about tests, interviews and offers', checked: true },
    { id: 'sms', label: 'SMS Notifications', desc: 'Important alerts and reminders', checked: false },
    { id: 'push', label: 'Push Notifications', desc: 'Real-time updates on your mobile', checked: true }
  ];

  notificationTypes = [
    { label: 'Test Reminders', checked: true },
    { label: 'Interview Invites', checked: true },
    { label: 'Application Updates', checked: true },
    { label: 'Job Offers', checked: true },
    { label: 'Marketing Updates', checked: false },
    { label: 'Newsletter', checked: false }
  ];
}
