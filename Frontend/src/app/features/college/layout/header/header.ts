import { Component, Input, ElementRef, HostListener } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

export interface CollegeNotificationItem {
  id: number;
  title: string;
  desc: string;
  time: string;
  unread: boolean;
  icon: string;
}

@Component({
  selector: 'app-college-header',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class CollegeHeader {
  @Input() title: string = 'College Dashboard';

  isNotificationOpen = false;
  isProfileOpen = false;
  isExpandedModalOpen = false;
  activeFilterTab: 'recent' | 'all' = 'recent';

  notifications: CollegeNotificationItem[] = [
    {
      id: 1,
      title: 'Batch 2024 Assessment Live',
      desc: '250 students are currently attempting Test 4 in Lab A & Lab B with active AI proctoring.',
      time: '10m ago',
      unread: true,
      icon: 'bi-lightning-charge-fill text-warning'
    },
    {
      id: 2,
      title: 'TCS Recruiter Shortlist',
      desc: 'TCS Talent Acquisition viewed 8 Tier 1 student skill passports for interview rounds.',
      time: '45m ago',
      unread: true,
      icon: 'bi-building-check text-success'
    },
    {
      id: 3,
      title: 'Roster Import Sync Completed',
      desc: '120 computer engineering student profiles verified and mapped to placement drives.',
      time: '3h ago',
      unread: true,
      icon: 'bi-people-fill text-primary'
    },
    {
      id: 4,
      title: 'Corporate Drive Scheduled',
      desc: 'Infosys Special Placement Drive slot confirmed for 28th October.',
      time: '1d ago',
      unread: false,
      icon: 'bi-calendar-check text-purple'
    },
    {
      id: 5,
      title: 'Institutional SLA Report Ready',
      desc: 'Weekly lab proctoring report & bandwidth health audit is ready for review.',
      time: '2d ago',
      unread: false,
      icon: 'bi-shield-check text-info'
    }
  ];

  constructor(private elementRef: ElementRef) {}

  get unreadNotificationsCount(): number {
    return this.notifications.filter(n => n.unread).length;
  }

  toggleNotifications(event: MouseEvent) {
    event.stopPropagation();
    this.isNotificationOpen = !this.isNotificationOpen;
    this.isProfileOpen = false;
  }

  toggleProfile(event: MouseEvent) {
    event.stopPropagation();
    this.isProfileOpen = !this.isProfileOpen;
    this.isNotificationOpen = false;
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

  get filteredNotifications(): CollegeNotificationItem[] {
    if (this.activeFilterTab === 'recent') {
      return this.notifications.slice(0, 3);
    }
    return this.notifications;
  }

  markAllRead(event: MouseEvent) {
    event.stopPropagation();
    this.notifications.forEach(n => (n.unread = false));
  }

  markSingleRead(notif: CollegeNotificationItem, event?: MouseEvent) {
    if (event) event.stopPropagation();
    if (notif.unread) {
      notif.unread = false;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isNotificationOpen = false;
      this.isProfileOpen = false;
    }
  }
}
