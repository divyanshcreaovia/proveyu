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

  recruiterName: string = sessionStorage.getItem('candidateFullName') || 'Recruiter';

  isNotificationOpen: boolean = false;
  isExpandedModalOpen: boolean = false;
  activeFilterTab: 'recent' | 'all' = 'recent';
  unreadNotificationsCount: number = 3;

  notifications: NotificationItem[] = [
    {
      id: 1,
      title: 'Candidate Skill Passport Unlocked',
      desc: 'You unlocked Rahul Sharma\'s verified 98th Percentile Java Passport.',
      time: '15 mins ago',
      unread: true,
      icon: 'bi-award',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 2,
      title: 'Interview Invite Accepted',
      desc: 'Candidate Ananya Gupta accepted your interview invite for Senior Frontend Engineer.',
      time: '1 hour ago',
      unread: true,
      icon: 'bi-check-circle',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 3,
      title: 'New Verified Candidate Match',
      desc: '3 new candidates matching React JS requirements added to talent pool.',
      time: '3 hours ago',
      unread: true,
      icon: 'bi-people',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 4,
      title: 'Agency Subscription Active',
      desc: 'Your Enterprise hiring plan renewed with 50 monthly candidate unlocks.',
      time: 'Yesterday',
      unread: false,
      icon: 'bi-building',
      iconBg: '#f8fafc',
      iconColor: '#0f172a',
    },
    {
      id: 5,
      title: 'Proctoring Report Verification Passed',
      desc: 'AI & In-Person proctoring logs for Slot #84920 verified clean.',
      time: '2 days ago',
      unread: false,
      icon: 'bi-shield-check',
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
