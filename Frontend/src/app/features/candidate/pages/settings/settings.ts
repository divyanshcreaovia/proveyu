import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  activeTab = 'notifications';
  private http = inject(HttpClient);

  isSaving = false;
  successMessage = '';
  errorMessage = '';

  settingsData: any = {
    emailNotifications: true,
    smsNotifications: false,
    pushNotifications: true,
    testReminders: true,
    interviewInvites: true,
    applicationUpdates: true,
    jobOffers: true,
    marketingUpdates: false,
    newsletter: false
  };

  ngOnInit() {
    this.loadSettings();
  }

  loadSettings() {
    const token = sessionStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.get('http://localhost:8080/api/v1/candidates/settings', { headers }).subscribe({
      next: (res: any) => {
        if (res.data) {
          this.settingsData = res.data;
        }
      },
      error: (err) => console.error('Error loading settings', err)
    });
  }

  saveSettings() {
    const token = sessionStorage.getItem('token');
    if (!token) return;

    this.isSaving = true;
    this.successMessage = '';
    this.errorMessage = '';

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.put('http://localhost:8080/api/v1/candidates/settings', this.settingsData, { headers }).subscribe({
      next: (res: any) => {
        this.isSaving = false;
        this.successMessage = 'Settings saved successfully!';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        console.error('Error saving settings', err);
        this.isSaving = false;
        this.errorMessage = 'Failed to save settings. Please try again.';
        setTimeout(() => this.errorMessage = '', 3000);
      }
    });
  }
}
