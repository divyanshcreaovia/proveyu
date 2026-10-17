import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

export interface ReportItem {
  id: string;
  title: string;
  desc: string;
  category: 'Diagnostic & Analytics' | 'Student Roster' | 'Placements & Drives' | 'Executive Leadership';
  size: string;
  format: 'PDF / Excel' | 'PDF Only' | 'Excel Matrix';
  icon: string;
  iconBg: string;
  iconColor: string;
  lastGenerated: string;
  frequency: string;
}

@Component({
  selector: 'app-college-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports {
  constructor(private notificationService: NotificationService) {}
  toastMessage: string | null = null;
  downloadingId: string | null = null;

  // Filter & Search
  searchQuery: string = '';
  selectedCategory: string = 'All';

  reports: ReportItem[] = [
    {
      id: 'REP-01',
      title: 'Tier Distribution & Benchmark Report',
      desc: 'Institutional breakdown across Tier 1, Tier 2, and Tier 3 cohorts with comparative score improvement curves.',
      category: 'Diagnostic & Analytics',
      size: '2.3 MB',
      format: 'PDF / Excel',
      icon: 'bi-bar-chart-fill',
      iconBg: '#eff6ff',
      iconColor: '#2563eb',
      lastGenerated: 'Today, 11:20 AM',
      frequency: 'Weekly Automated',
    },
    {
      id: 'REP-02',
      title: 'Cumulative Student Performance Dossier',
      desc: 'Granular assessment scores for all enrolled students across proctored tests, sectional percentiles, and ranks.',
      category: 'Student Roster',
      size: '5.1 MB',
      format: 'PDF / Excel',
      icon: 'bi-mortarboard-fill',
      iconBg: '#f5f3ff',
      iconColor: '#7c3aed',
      lastGenerated: 'Yesterday',
      frequency: 'Per Assessment',
    },
    {
      id: 'REP-03',
      title: 'Placement Recruitment & CTC Matrix',
      desc: 'Company-wise hiring breakdown, salary CTC statistics, offer letters status, and department conversion rates.',
      category: 'Placements & Drives',
      size: '3.7 MB',
      format: 'PDF / Excel',
      icon: 'bi-briefcase-fill',
      iconBg: '#ecfdf5',
      iconColor: '#059669',
      lastGenerated: 'Oct 18, 2024',
      frequency: 'Monthly Sync',
    },
    {
      id: 'REP-04',
      title: 'Monthly Executive Leadership Summary',
      desc: 'Concise 2-page dashboard report prepared for Principal, Dean, and Management Board review meetings.',
      category: 'Executive Leadership',
      size: '0.8 MB',
      format: 'PDF Only',
      icon: 'bi-award-fill',
      iconBg: '#fef3c7',
      iconColor: '#d97706',
      lastGenerated: 'Oct 01, 2024',
      frequency: 'Monthly Board',
    },
    {
      id: 'REP-05',
      title: 'Sectional Skill Gap & Remedial Audit',
      desc: 'Deep diagnostic audit highlighting weak aptitude areas (Quantitative, Logic, Coding) requiring faculty workshops.',
      category: 'Diagnostic & Analytics',
      size: '3.2 MB',
      format: 'PDF / Excel',
      icon: 'bi-pie-chart-fill',
      iconBg: '#eff6ff',
      iconColor: '#0284c7',
      lastGenerated: '3 days ago',
      frequency: 'Bi-Weekly',
    },
    {
      id: 'REP-06',
      title: 'Corporate Shortlisting & Eligibility Ledger',
      desc: 'Matched candidate rosters filtered by CGPA, branch cutoffs, and minimum ProveYu tier benchmarks.',
      category: 'Placements & Drives',
      size: '2.9 MB',
      format: 'Excel Matrix',
      icon: 'bi-table',
      iconBg: '#ecfdf5',
      iconColor: '#10b981',
      lastGenerated: 'Today, 09:40 AM',
      frequency: 'Drive-Specific',
    },
  ];

  get filteredReports(): ReportItem[] {
    return this.reports.filter(r => {
      const matchesSearch = !this.searchQuery.trim() ||
        r.title.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        r.desc.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        r.category.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesCat = this.selectedCategory === 'All' || r.category === this.selectedCategory;
      return matchesSearch && matchesCat;
    });
  }

  showToast(msg: string) {
    this.notificationService.showSuccess(msg);
  }

  downloadReport(rep: ReportItem, format: string) {
    this.downloadingId = rep.id;
    this.showToast(`Preparing ${rep.title} in ${format} format...`);
    setTimeout(() => {
      this.downloadingId = null;
      this.showToast(`Downloaded: ${rep.title} (${rep.size})`);
    }, 1200);
  }

  generateCustomReport() {
    this.showToast('Opening Custom Report Builder wizard...');
  }
}
