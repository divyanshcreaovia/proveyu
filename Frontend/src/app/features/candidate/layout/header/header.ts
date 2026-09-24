import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { RouterModule } from '@angular/router';

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

  notifications: any[] = [];
  unreadCount = 0;
  showDropdown = false;

  ngOnInit() {
    const savedName = localStorage.getItem('candidateFullName');
    if (savedName) {
      this.fullName = savedName;
    }
    this.loadNotifications();
  }

  loadNotifications() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.get('http://localhost:8080/api/v1/notifications', { headers }).subscribe({
      next: (res: any) => {
        if (res.data) {
          this.notifications = res.data;
          this.unreadCount = this.notifications.filter(n => !n.read).length;
        }
      },
      error: (err) => console.error('Error loading notifications', err)
    });
  }

  toggleDropdown() {
    this.showDropdown = !this.showDropdown;
  }

  markAsRead(notification: any, event: Event) {
    event.stopPropagation();
    if (notification.read) return;

    const token = localStorage.getItem('token');
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
