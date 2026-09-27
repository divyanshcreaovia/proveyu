import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { RouterModule, Router } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header implements OnInit {
  @Input() title: string = 'Dashboard';
  fullName: string = 'Candidate';
  private http = inject(HttpClient);
  private router = inject(Router);

  notifications: any[] = [];
  unreadCount = 0;
  showDropdown = false;
  hasUnreadInvite = false;
  unreadInviteMessage = '';

  ngOnInit() {
    const savedName = sessionStorage.getItem('candidateFullName');
    if (savedName) {
      this.fullName = savedName;
    }
    this.loadNotifications();
  }

  loadNotifications() {
    const token = sessionStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.get('http://localhost:8080/api/v1/notifications', { headers }).subscribe({
      next: (res: any) => {
        if (res.data) {
          this.notifications = res.data;
          this.unreadCount = this.notifications.filter(n => !n.read).length;
          const invite = this.notifications.find(n => !n.read && n.type === 'INTERVIEW_INVITATION');
          if (invite) {
            this.hasUnreadInvite = true;
            this.unreadInviteMessage = invite.message;
          }
        }
      },
      error: (err) => console.error('Error loading notifications', err)
    });
  }

  toggleDropdown() {
    this.showDropdown = !this.showDropdown;
  }

  onNotificationClick(notif: any) {
    if (!notif.read) {
      this.markAsRead(notif);
    }
    this.showDropdown = false;
    if (notif.type === 'INTERVIEW_INVITATION' || notif.type === 'INVITATION_CONFIRMED') {
      this.router.navigate(['/candidate/interview-invites']);
    }
  }

  goToInvites() {
    this.hasUnreadInvite = false;
    this.router.navigate(['/candidate/interview-invites']);
  }

  dismissInviteAlert() {
    this.hasUnreadInvite = false;
  }

  markAsRead(notification: any, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (notification.read) return;

    const token = sessionStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.put(`http://localhost:8080/api/v1/notifications/${notification.id}/read`, {}, { headers }).subscribe({
      next: () => {
        notification.read = true;
        this.unreadCount = Math.max(0, this.unreadCount - 1);
      },
      error: (err) => console.error('Error marking as read', err)
    });
  }
}
