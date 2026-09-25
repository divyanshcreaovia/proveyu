import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HighchartsChartComponent } from 'highcharts-angular';
import * as Highcharts from 'highcharts';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, HighchartsChartComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  Highcharts: typeof Highcharts = Highcharts;

  // Active chart toggle
  activeChartTab: 'score' | 'percentile' = 'score';

  // 1. Welcome / Profile Summary Data
  candidateProfile = {
    name: 'Rahul Sharma',
    tier: 'Silver Tier Candidate',
    passportId: 'PY-8842-5678',
    email: 'rahul.sharma@example.com',
    phone: '+91 98765 43210',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
    completionPercentage: 85
  };

  // 2. 5 Top Professional Metric Cards
  statCards = [
    {
      label: 'UPCOMING EXAMS',
      value: '1 Scheduled',
      subtext: 'DSA Assessment on 24 Sep',
      accentColor: '#7c3aed',
      textColor: '#0f172a',
      route: '/candidate/book-slot'
    },
    {
      label: 'EXAMS TAKEN',
      value: '8 Exams',
      subtext: '6 Passed (75% Pass Rate)',
      accentColor: '#10b981',
      textColor: '#0f172a',
      route: '/candidate/my-passport'
    },
    {
      label: 'ACTIVE INVITES',
      value: '3 Invites',
      subtext: 'Google, Microsoft & TCS',
      accentColor: '#c026d3',
      textColor: '#0f172a',
      route: '/candidate/interview-invites'
    },
    {
      label: 'PERFORMANCE TIER',
      value: '92.4 %ile',
      subtext: 'Top 8% All India Rank',
      accentColor: '#0284c7',
      textColor: '#0284c7',
      route: '/candidate/my-passport'
    },
    {
      label: 'PASSPORT IDENTITY',
      value: 'Silver Tier',
      subtext: 'ID: PY-8842-5678',
      accentColor: '#475569',
      textColor: '#0f172a',
      route: '/candidate/my-passport'
    }
  ];

  // 3. Next Exam Details
  nextExam = {
    subject: 'DSA & Algorithms Assessment',
    date: '24 Sep, 2024',
    time: '11:00 AM - 12:30 PM',
    center: 'iON Digital Zone, Koramangala, Bengaluru',
    mode: 'In-Person (AI Proctored)',
    admitCardReady: true
  };

  // 4. Active Recruiter Interview Invites (Real Candidate Data)
  interviewInvites = [
    {
      id: 'INV-9021',
      company: 'Google India',
      role: 'Senior Frontend Engineer (React/TypeScript)',
      ctc: '₹22 - 28 LPA',
      status: 'Interview Scheduled',
      date: 'Tomorrow, 02:30 PM',
      location: 'Virtual / Google Meet',
      badgeBg: '#e0f2fe',
      badgeColor: '#0369a1'
    },
    {
      id: 'INV-8910',
      company: 'Microsoft Corporation',
      role: 'Fullstack Software Engineer (Node/C#)',
      ctc: '₹24 - 30 LPA',
      status: 'Invite Pending',
      date: '28 Sep, 11:00 AM',
      location: 'Virtual / MS Teams',
      badgeBg: '#fef3c7',
      badgeColor: '#b45309'
    }
  ];

  // 5. Verified Skill Passport Scorecards
  verifiedSkills = [
    { skill: 'Data Structures & Algorithms', percentile: '98.2 %ile', score: '94/100', status: 'Proctored Verified', bg: '#e0f2fe', color: '#0369a1' },
    { skill: 'Frontend Web & React.js', percentile: '96.5 %ile', score: '92/100', status: 'Proctored Verified', bg: '#f3e8ff', color: '#7c3aed' },
    { skill: 'Java Enterprise & Spring', percentile: '91.4 %ile', score: '88/100', status: 'Proctored Verified', bg: '#dcfce7', color: '#15803d' }
  ];

  // 6. Exam History
  examHistory = [
    { name: 'Java Core & OOPs', date: '15 Aug 2024', score: '88 / 100', percentile: '91.2 %ile', status: 'Passed' },
    { name: 'SQL & Database Design', date: '02 Aug 2024', score: '82 / 100', percentile: '86.4 %ile', status: 'Passed' },
    { name: 'Frontend React & Web', date: '20 Jul 2024', score: '94 / 100', percentile: '98.0 %ile', status: 'Passed' },
    { name: 'Basic Coding Round 1', date: '05 Jul 2024', score: '76 / 100', percentile: '78.5 %ile', status: 'Passed' }
  ];

  // 7. Recent Activity Timeline
  recentActivities = [
    { text: 'Slot booked for DSA Assessment (24 Sep, 11:00 AM)', time: '2 hours ago', icon: 'bi-calendar-check-fill text-primary' },
    { text: 'Payment Receipt #PY-9012 generated (₹499)', time: 'Yesterday', icon: 'bi-credit-card-fill text-success' },
    { text: 'Scorecard published for Java Core (88/100)', time: '3 days ago', icon: 'bi-file-earmark-bar-graph-fill text-warning' },
    { text: 'Recruiter from Google viewed candidate passport', time: '5 days ago', icon: 'bi-eye-fill text-info' }
  ];

  // 8. Profile Completion Missing Items
  missingProfileItems = [
    'Add Work Experience / Projects',
    'Upload Resume PDF',
    'Verify Mobile OTP'
  ];

  // Highcharts Configurations
  performanceChartOptions: Highcharts.Options = {};
  skillsRadarOptions: Highcharts.Options = {};

  ngOnInit() {
    this.initPerformanceChart();
    this.initSkillsRadarChart();
  }

  initPerformanceChart() {
    this.performanceChartOptions = {
      chart: {
        type: 'areaspline',
        backgroundColor: 'transparent',
        height: 230,
        style: { fontFamily: 'Inter, sans-serif' }
      },
      title: { text: '' },
      credits: { enabled: false },
      xAxis: {
        categories: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct'],
        lineColor: '#e8ecf2',
        labels: { style: { color: '#64748b', fontSize: '12px' } }
      },
      yAxis: {
        title: { text: '' },
        gridLineColor: '#f1f3f9',
        gridLineDashStyle: 'Dash',
        max: 100,
        labels: {
          format: '{value}%',
          style: { color: '#64748b', fontSize: '11px' }
        }
      },
      tooltip: {
        shared: true,
        backgroundColor: '#16122b',
        borderRadius: 12,
        style: { color: '#ffffff', fontSize: '12px' },
        headerFormat: '<span style="font-size:11px;color:#cbd5e1">{point.key} Score</span><br/>'
      },
      plotOptions: {
        areaspline: {
          fillOpacity: 0.15,
          marker: { radius: 4, symbol: 'circle' }
        }
      },
      series: [
        {
          name: 'Your Score (%)',
          type: 'areaspline',
          color: '#00bcd4',
          data: [65, 70, 88, 76, 85, 92, 89, 94, 91, 96]
        },
        {
          name: 'Batch Benchmark (%)',
          type: 'areaspline',
          color: '#16122b',
          data: [55, 58, 62, 65, 68, 70, 72, 74, 75, 78]
        }
      ]
    };
  }

  initSkillsRadarChart() {
    this.skillsRadarOptions = {
      chart: {
        type: 'column',
        backgroundColor: 'transparent',
        height: 230,
        style: { fontFamily: 'Inter, sans-serif' }
      },
      title: { text: '' },
      credits: { enabled: false },
      xAxis: {
        categories: ['DSA', 'System Design', 'Java / OOPs', 'SQL & DB', 'Problem Solving'],
        crosshair: true,
        labels: { style: { color: '#16122b', fontWeight: '600', fontSize: '11px' } }
      },
      yAxis: {
        min: 0,
        max: 100,
        title: { text: '' },
        labels: { format: '{value}%' },
        gridLineColor: '#f1f3f9'
      },
      tooltip: {
        backgroundColor: '#16122b',
        borderRadius: 10,
        style: { color: '#ffffff' }
      },
      series: [
        {
          name: 'Skill Proficiency',
          type: 'column',
          color: '#16122b',
          data: [94, 72, 88, 82, 90]
        }
      ]
    };
  }

  // Modal states for Exam Details and Reschedule
  isExamDetailsModalOpen = false;
  isRescheduleModalOpen = false;

  // Reschedule selection states
  rescheduleDate = '28 Sep, 2024';
  rescheduleTime = '02:30 PM - 04:00 PM';
  rescheduleSuccessMsg = false;

  availableRescheduleDates = [
    '26 Sep, 2024',
    '28 Sep, 2024',
    '30 Sep, 2024',
    '02 Oct, 2024'
  ];

  availableRescheduleTimes = [
    '09:00 AM - 10:30 AM',
    '11:00 AM - 12:30 PM',
    '02:30 PM - 04:00 PM',
    '05:00 PM - 06:30 PM'
  ];

  openExamDetailsModal(event?: Event) {
    if (event) event.preventDefault();
    this.isExamDetailsModalOpen = true;
  }

  closeExamDetailsModal() {
    this.isExamDetailsModalOpen = false;
  }

  openRescheduleModal(event?: Event) {
    if (event) event.preventDefault();
    this.isRescheduleModalOpen = true;
    this.rescheduleSuccessMsg = false;
  }

  closeRescheduleModal() {
    this.isRescheduleModalOpen = false;
  }

  confirmReschedule() {
    this.nextExam.date = this.rescheduleDate;
    this.nextExam.time = this.rescheduleTime;
    this.rescheduleSuccessMsg = true;
    setTimeout(() => {
      this.isRescheduleModalOpen = false;
      this.rescheduleSuccessMsg = false;
    }, 1200);
  }
}

