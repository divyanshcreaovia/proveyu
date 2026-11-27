import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { guestGuard } from './core/guards/guest.guard';

const COLLEGE = ['COLLEGE_ADMIN', 'COLLEGE_COORDINATOR', 'COLLEGE_VIEWER'];
const COLLEGE_WRITE = ['COLLEGE_ADMIN', 'COLLEGE_COORDINATOR'];
const COLLEGE_ADMIN = ['COLLEGE_ADMIN'];

export const routes: Routes = [
  // ---------------------------------------------------------------- public site
  { path: '', loadComponent: () => import('./features/public/pages/home/home').then(m => m.Home) },
  { path: 'for-colleges', title: 'For colleges', loadComponent: () => import('./features/public/pages/for-colleges/for-colleges').then(m => m.ForColleges) },
  { path: 'for-students', title: 'For students', loadComponent: () => import('./features/public/pages/for-students/for-students').then(m => m.ForStudents) },
  { path: 'how-it-works', title: 'How it works', loadComponent: () => import('./features/public/pages/how-it-works/how-it-works').then(m => m.HowItWorks) },
  { path: 'pricing', title: 'Pricing', loadComponent: () => import('./features/public/pages/pricing/pricing').then(m => m.Pricing) },
  // old pages of the in-person assessment model: keep old links working
  { path: 'why-in-person', redirectTo: 'how-it-works' },
  { path: 'fresher-model', redirectTo: 'for-students' },
  // portal choosers (no form): open for everyone, also when signed in; each portal's own login page keeps guestGuard
  { path: 'select-role', loadComponent: () => import('./features/public/pages/select-role/select-role').then(m => m.SelectRole) },
  { path: 'login', loadComponent: () => import('./features/public/pages/login/login').then(m => m.Login) },
  { path: 'forbidden', loadComponent: () => import('./shared/pages/forbidden').then(m => m.Forbidden) },

  // old candidate portal URL (the student portal replaced it)
  { path: 'candidate/campus-drives', redirectTo: '/student/drives' },

  // ---------------------------------------------------------------- platform admin (no sign-up: admins are created by an admin)
  { path: 'admin/login', title: 'Admin sign in', canActivate: [guestGuard], loadComponent: () => import('./features/admin/auth/admin-login').then(m => m.AdminLogin) },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard], data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/admin/layout/admin-shell').then(m => m.AdminShell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./features/admin/pages/dashboard').then(m => m.AdminDashboardPage) },
      { path: 'colleges', title: 'Colleges', loadComponent: () => import('./features/admin/pages/colleges').then(m => m.AdminCollegesPage) },
      { path: 'users', title: 'Users', loadComponent: () => import('./features/admin/pages/users').then(m => m.AdminUsersPage) },
      { path: 'admins', title: 'Admins', loadComponent: () => import('./features/admin/pages/admins').then(m => m.AdminAdminsPage) },
      { path: 'cxo-users', title: 'CXO accounts', loadComponent: () => import('./features/admin/pages/cxo-users').then(m => m.AdminCxoUsersPage) },
      { path: 'payments', title: 'Interview payments', loadComponent: () => import('./features/admin/pages/payments').then(m => m.AdminPaymentsPage) },
      { path: 'mock-tests', title: 'Mock tests', loadComponent: () => import('./features/admin/pages/mock-tests').then(m => m.AdminMockTestsPage) },
      { path: 'off-campus', title: 'Off-campus jobs', loadComponent: () => import('./features/admin/pages/off-campus').then(m => m.AdminOffCampusPage) },
      { path: 'code-ground', title: 'Code ground', loadComponent: () => import('./features/admin/pages/code-ground').then(m => m.AdminCodeGroundPage) },
      { path: 'prep', redirectTo: 'mock-tests' },
      { path: 'proctoring', title: 'Proctoring', loadComponent: () => import('./features/admin/pages/proctoring').then(m => m.AdminProctoringPage) },
      { path: 'support', title: 'Support', loadComponent: () => import('./features/admin/pages/support').then(m => m.AdminSupportPage) },
      { path: 'enquiries', title: 'Pricing enquiries', loadComponent: () => import('./features/admin/pages/enquiries').then(m => m.AdminEnquiriesPage) },
      { path: 'broadcast', title: 'Broadcast', loadComponent: () => import('./features/admin/pages/broadcast').then(m => m.AdminBroadcastPage) },
      { path: 'settings', title: 'Settings', loadComponent: () => import('./features/admin/pages/settings').then(m => m.AdminSettingsPage) },
      { path: 'audit', title: 'Audit log', loadComponent: () => import('./features/admin/pages/audit').then(m => m.AdminAuditPage) },
    ],
  },

  // ---------------------------------------------------------------- student portal (college students, CANDIDATE role)
  { path: 'student/login', canActivate: [guestGuard], loadComponent: () => import('./features/student/auth/student-login').then(m => m.StudentLogin) },
  { path: 'student/signup', canActivate: [guestGuard], loadComponent: () => import('./features/student/auth/student-signup').then(m => m.StudentSignup) },
  {
    path: 'student/forgot-password', data: { loginUrl: '/student/login', variant: 'student' },
    loadComponent: () => import('./features/college/auth/forgot-password').then(m => m.ForgotPassword),
  },
  {
    // full-screen test player (outside the sidebar layout, like a real exam)
    path: 'student/test/:id', title: 'Mock test', canActivate: [authGuard, roleGuard], data: { roles: ['CANDIDATE'] },
    loadComponent: () => import('./features/student/prep/test-player').then(m => m.TestPlayer),
  },
  {
    // full-screen AI interview room
    path: 'student/interview-room/:id', title: 'AI interview', canActivate: [authGuard, roleGuard], data: { roles: ['CANDIDATE'] },
    loadComponent: () => import('./features/student/interview/interview-room').then(m => m.InterviewRoom),
  },
  {
    path: 'student',
    canActivate: [authGuard, roleGuard], data: { roles: ['CANDIDATE'] },
    loadComponent: () => import('./features/student/layout/student-shell').then(m => m.StudentShell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./features/student/portal/dashboard').then(m => m.StudentDashboard) },
      { path: 'drives', title: 'Campus drives', loadComponent: () => import('./features/student/portal/drives').then(m => m.StudentDrives) },
      { path: 'off-campus', title: 'Off-campus jobs', loadComponent: () => import('./features/student/portal/off-campus').then(m => m.StudentOffCampus) },
      { path: 'applications', title: 'My applications', loadComponent: () => import('./features/student/portal/applications').then(m => m.StudentApplications) },
      { path: 'interviews', title: 'Interviews', loadComponent: () => import('./features/student/portal/interviews').then(m => m.StudentInterviews) },
      { path: 'offers', title: 'Offers', loadComponent: () => import('./features/student/portal/offers').then(m => m.StudentOffers) },
      { path: 'announcements', title: 'Announcements', loadComponent: () => import('./features/student/portal/announcements').then(m => m.StudentAnnouncements) },
      { path: 'chat', title: 'Chat with placement cell', loadComponent: () => import('./features/student/portal/chat').then(m => m.StudentChat) },
      { path: 'documents', title: 'Document locker', loadComponent: () => import('./features/student/portal/documents').then(m => m.StudentDocuments) },
      { path: 'profile', title: 'My profile', loadComponent: () => import('./features/student/portal/profile').then(m => m.StudentProfile) },
      { path: 'prep', title: 'Preparation', loadComponent: () => import('./features/student/prep/prep-home').then(m => m.PrepHome) },
      { path: 'college-tests', title: 'College tests', data: { tab: 'college' }, loadComponent: () => import('./features/student/prep/prep-home').then(m => m.PrepHome) },
      { path: 'prep/result/:id', title: 'Test result', loadComponent: () => import('./features/student/prep/prep-result').then(m => m.PrepResult) },
      { path: 'code-ground', title: 'Code ground', loadComponent: () => import('./features/student/code-ground/code-ground').then(m => m.CodeGround) },
      { path: 'ai-interview', title: 'AI interview', loadComponent: () => import('./features/student/interview/interview-home').then(m => m.InterviewHome) },
      { path: 'ai-interview/report/:id', title: 'Interview report', loadComponent: () => import('./features/student/interview/interview-report').then(m => m.InterviewReport) },
    ],
  },

  // ---------------------------------------------------------------- CXO portal (college leadership; accounts are created by a platform admin)
  { path: 'cxo/login', title: 'Leadership sign in', canActivate: [guestGuard], loadComponent: () => import('./features/cxo/auth/cxo-login').then(m => m.CxoLogin) },
  {
    path: 'cxo/forgot-password', data: { loginUrl: '/cxo/login', variant: 'cxo' },
    loadComponent: () => import('./features/college/auth/forgot-password').then(m => m.ForgotPassword),
  },
  // a shared dashboard opens from its link, with no sign-in
  { path: 'cxo/share/:token', title: 'Shared dashboard', loadComponent: () => import('./features/cxo/share/cxo-shared').then(m => m.CxoShared) },
  {
    path: 'cxo',
    canActivate: [authGuard, roleGuard], data: { roles: ['CXO'] },
    loadComponent: () => import('./features/cxo/layout/cxo-shell').then(m => m.CxoShell),
    children: [
      { path: '', redirectTo: 'studio', pathMatch: 'full' },
      { path: 'studio', title: 'AI Studio', loadComponent: () => import('./features/cxo/studio/cxo-studio').then(m => m.CxoStudio) },
      { path: 'dashboards', title: 'Saved dashboards', loadComponent: () => import('./features/cxo/dashboards/cxo-dashboards').then(m => m.CxoDashboards) },
      { path: 'dashboards/:id', title: 'Dashboard', loadComponent: () => import('./features/cxo/dashboards/cxo-dashboard-view').then(m => m.CxoDashboardView) },
      { path: 'changes', title: 'What changed', loadComponent: () => import('./features/cxo/insights/cxo-changes').then(m => m.CxoChanges) },
      { path: 'forecast', title: 'Forecast', loadComponent: () => import('./features/cxo/insights/cxo-forecast').then(m => m.CxoForecast) },
      { path: 'benchmark', title: 'NIRF / NAAC figures', loadComponent: () => import('./features/cxo/insights/cxo-benchmark').then(m => m.CxoBenchmark) },
    ],
  },

  // ---------------------------------------------------------------- company Drive Room (RECRUITER role)
  { path: 'company/login', canActivate: [guestGuard], loadComponent: () => import('./features/company/company-login').then(m => m.CompanyLogin) },
  { path: 'company/accept', loadComponent: () => import('./features/company/company-accept').then(m => m.CompanyAccept) },
  {
    path: 'company/forgot-password', data: { loginUrl: '/company/login', variant: 'company' },
    loadComponent: () => import('./features/college/auth/forgot-password').then(m => m.ForgotPassword),
  },
  {
    path: 'company',
    canActivate: [authGuard, roleGuard], data: { roles: ['RECRUITER'] },
    loadComponent: () => import('./features/company/company-shell').then(m => m.CompanyShell),
    children: [
      { path: '', redirectTo: 'drives', pathMatch: 'full' },
      { path: 'drives', title: 'My drives', loadComponent: () => import('./features/company/company-drives').then(m => m.CompanyDrives) },
      { path: 'drives/:id', title: 'Drive Room', loadComponent: () => import('./features/company/drive-room').then(m => m.DriveRoom) },
    ],
  },

  // ---------------------------------------------------------------- college auth (public)
  { path: 'college/login', canActivate: [guestGuard], loadComponent: () => import('./features/college/auth/college-login').then(m => m.CollegeLogin) },
  { path: 'college/register', canActivate: [guestGuard], loadComponent: () => import('./features/college/auth/college-register').then(m => m.CollegeRegister) },
  { path: 'college/verify-email', loadComponent: () => import('./features/college/auth/verify-email').then(m => m.VerifyEmail) },
  { path: 'college/forgot-password', loadComponent: () => import('./features/college/auth/forgot-password').then(m => m.ForgotPassword) },
  { path: 'college/accept-invite', loadComponent: () => import('./features/college/auth/accept-invite').then(m => m.AcceptInvite) },
  {
    path: 'college/pending-approval', canActivate: [authGuard, roleGuard], data: { roles: COLLEGE },
    loadComponent: () => import('./features/college/auth/pending-approval').then(m => m.PendingApproval),
  },

  // ---------------------------------------------------------------- college portal
  {
    path: 'college',
    canActivate: [authGuard, roleGuard], data: { roles: COLLEGE },
    loadComponent: () => import('./features/college/layout/college-shell').then(m => m.CollegeShell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./features/college/dashboard/dashboard').then(m => m.CollegeDashboard) },
      { path: 'students', title: 'Students', loadComponent: () => import('./features/college/students/students').then(m => m.Students) },
      {
        path: 'students/import', title: 'Import students', canActivate: [roleGuard], data: { roles: COLLEGE_WRITE },
        loadComponent: () => import('./features/college/students/student-import').then(m => m.StudentImport),
      },
      { path: 'companies', title: 'Companies', loadComponent: () => import('./features/college/companies/companies').then(m => m.Companies) },
      { path: 'drives', title: 'Placement drives', loadComponent: () => import('./features/college/drives/drives').then(m => m.Drives) },
      {
        path: 'drives/new', title: 'New drive', canActivate: [roleGuard], data: { roles: COLLEGE_WRITE },
        loadComponent: () => import('./features/college/drives/drive-form').then(m => m.DriveForm),
      },
      {
        path: 'drives/:id/edit', title: 'Edit drive', canActivate: [roleGuard], data: { roles: COLLEGE_WRITE },
        loadComponent: () => import('./features/college/drives/drive-form').then(m => m.DriveForm),
      },
      { path: 'drives/:id', title: 'Drive', loadComponent: () => import('./features/college/drives/drive-detail').then(m => m.DriveDetail) },
      { path: 'interviews', title: 'Interviews', loadComponent: () => import('./features/college/interviews/interviews').then(m => m.Interviews) },
      { path: 'offers', title: 'Offers', loadComponent: () => import('./features/college/offers/offers').then(m => m.Offers) },
      { path: 'team', title: 'Team & roles', loadComponent: () => import('./features/college/team/team').then(m => m.Team) },
      { path: 'communication', title: 'Communication', loadComponent: () => import('./features/college/communication/communication').then(m => m.Communication) },
      { path: 'reports', title: 'Reports & analytics', loadComponent: () => import('./features/college/reports/reports').then(m => m.Reports) },
      { path: 'code-ground', title: 'Code ground leaderboard', loadComponent: () => import('./features/college/code-ground/code-leaderboard').then(m => m.CodeLeaderboard) },
      { path: 'off-campus', title: 'Off-campus', loadComponent: () => import('./features/college/off-campus/off-campus').then(m => m.CollegeOffCampus) },
      { path: 'mock-tests', title: 'Tests', loadComponent: () => import('./features/college/prep/college-mock-tests').then(m => m.CollegeMockTests) },
      {
        path: 'audit', title: 'Audit log', canActivate: [roleGuard], data: { roles: COLLEGE_ADMIN },
        loadComponent: () => import('./features/college/audit/audit-log').then(m => m.AuditLog),
      },
      {
        path: 'settings', title: 'Settings', canActivate: [roleGuard], data: { roles: COLLEGE_ADMIN },
        loadComponent: () => import('./features/college/settings/settings').then(m => m.CollegeSettings),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
