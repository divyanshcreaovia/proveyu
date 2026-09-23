import { Component, Input, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

interface NotificationItem {
  id: number;
  title: string;
  desc: string;
  time: string;
  unread: boolean;
  icon: string;
  iconBg: string;
  iconColor: string;
}

@Component({
  selector: 'app-recruiter-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class RecruiterHeader {
  @Input() title: string = 'Recruiter Dashboard';

  isNotificationOpen: boolean = false;
  unreadNotificationsCount: number = 3;

  notifications: NotificationItem[] = [
    {
      id: 1,
      title: 'Candidate Skill Passport Unlocked',
      desc: 'You unlocked Rahul Sharma\'s verified 98th Percentile Java Passport.',
      time: '15 mins ago',
      unread: true,
      icon: 'bi-file-earmark-check-fill',
      iconBg: '#f3e8ff',
      iconColor: '#8b5cf6',
    },
    {
      id: 2,
      title: 'Interview Invite Accepted',
      desc: 'Candidate Ananya Gupta accepted your interview invite for Senior Frontend Engineer.',
      time: '1 hour ago',
      unread: true,
      icon: 'bi-check-circle-fill',
      iconBg: '#e6f9f0',
      iconColor: '#10b981',
    },
    {
      id: 3,
      title: 'New Verified Candidate Match',
      desc: '3 new candidates matching React JS requirements added to talent pool.',
      time: '3 hours ago',
      unread: true,
      icon: 'bi-people-fill',
      iconBg: '#e6f4fe',
      iconColor: '#0284c7',
    },
    {
      id: 4,
      title: 'Agency Subscription Active',
      desc: 'Your Enterprise hiring plan renewed with 50 monthly candidate unlocks.',
      time: 'Yesterday',
      unread: false,
      icon: 'bi-building-fill',
      iconBg: '#fff0e6',
      iconColor: '#f97316',
    },
    {
      id: 5,
      title: 'Proctoring Report Verification Passed',
      desc: 'AI & In-Person proctoring logs for Slot #84920 verified clean.',
      time: '2 days ago',
      unread: false,
      icon: 'bi-shield-check',
      iconBg: '#f1f5f9',
      iconColor: '#64748b',
    },
  ];

  constructor(private elementRef: ElementRef) {}

  toggleNotifications(event: MouseEvent) {
    event.stopPropagation();
    this.isNotificationOpen = !this.isNotificationOpen;
  }

  markAllRead(event: MouseEvent) {
    event.stopPropagation();
    this.notifications.forEach((n) => (n.unread = false));
    this.unreadNotificationsCount = 0;
  }

  markSingleRead(notif: NotificationItem, event: MouseEvent) {
    event.stopPropagation();
    if (notif.unread) {
      notif.unread = false;
      this.unreadNotificationsCount = Math.max(0, this.unreadNotificationsCount - 1);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isNotificationOpen = false;
    }
  }
}
