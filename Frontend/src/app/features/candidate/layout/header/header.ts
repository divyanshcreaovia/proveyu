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
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  @Input() title: string = 'Dashboard';

  isNotificationOpen: boolean = false;
  isExpandedModalOpen: boolean = false;
  activeFilterTab: 'recent' | 'all' = 'recent';
  unreadNotificationsCount: number = 3;

  notifications: NotificationItem[] = [
    {
      id: 1,
      title: 'Exam Slot Booked Successfully',
      desc: 'Your assessment slot is scheduled for 25th Sept at iON Digital Zone, Center 02.',
      time: '10 mins ago',
      unread: true,
      icon: 'bi-calendar3',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 2,
      title: 'Skill Passport Percentile Updated',
      desc: 'Your standardized score in React & Frontend Engineering updated to 94.5%.',
      time: '2 hours ago',
      unread: true,
      icon: 'bi-bar-chart-line',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 3,
      title: 'Admit Card Generated',
      desc: 'Admit Card is available for instant download on your Candidate Dashboard.',
      time: '5 hours ago',
      unread: true,
      icon: 'bi-file-earmark-text',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 4,
      title: 'New Interview Invite Received',
      desc: 'TCS Talent Acquisition sent an invite for Senior Fullstack Developer role.',
      time: '1 day ago',
      unread: false,
      icon: 'bi-envelope',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 5,
      title: 'Support Ticket Resolved',
      desc: 'Ticket #PVU-849201 resolved by our candidate helpdesk team.',
      time: '2 days ago',
      unread: false,
      icon: 'bi-headset',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
  ];

  constructor(private elementRef: ElementRef) {}

  toggleNotifications(event: MouseEvent) {
    event.stopPropagation();
    this.isNotificationOpen = !this.isNotificationOpen;
  }

  openExpandModal(event?: MouseEvent) {
    if (event) event.stopPropagation();
    this.isNotificationOpen = false;
    this.isExpandedModalOpen = true;
  }

  closeExpandModal() {
    this.isExpandedModalOpen = false;
  }

  setFilterTab(tab: 'recent' | 'all') {
    this.activeFilterTab = tab;
  }

  get filteredNotifications(): NotificationItem[] {
    if (this.activeFilterTab === 'recent') {
      return this.notifications.slice(0, 3);
    }
    return this.notifications;
  }

  markAllRead(event: MouseEvent) {
    event.stopPropagation();
    this.notifications.forEach((n) => (n.unread = false));
    this.unreadNotificationsCount = 0;
  }

  markSingleRead(notif: NotificationItem, event?: MouseEvent) {
    if (event) event.stopPropagation();
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
