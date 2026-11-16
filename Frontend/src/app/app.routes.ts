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
  { path: 'how-it-works', loadComponent: () => import('./features/public/pages/how-it-works/how-it-works').then(m => m.HowItWorks) },
  { path: 'why-in-person', loadComponent: () => import('./features/public/pages/why-in-person/why-in-person').then(m => m.WhyInPerson) },
  { path: 'fresher-model', loadComponent: () => import('./features/public/pages/fresher-model/fresher-model').then(m => m.FresherModel) },
  { path: 'select-role', canActivate: [guestGuard], loadComponent: () => import('./features/public/pages/select-role/select-role').then(m => m.SelectRole) },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./features/public/pages/login/login').then(m => m.Login) },
  { path: 'forbidden', loadComponent: () => import('./shared/pages/forbidden').then(m => m.Forbidden) },

  // ---------------------------------------------------------------- candidate portal
  {
    path: 'candidate',
    canActivate: [authGuard, roleGuard], data: { roles: ['CANDIDATE'] },
    loadComponent: () => import('./features/candidate/layout/candidate-layout/candidate-layout').then(m => m.CandidateLayout),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/candidate/pages/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'profile', loadComponent: () => import('./features/candidate/pages/profile/profile').then(m => m.Profile) },
      { path: 'book-slot', loadComponent: () => import('./features/candidate/pages/book-slot/book-slot').then(m => m.BookSlot) },
      { path: 'my-passport', loadComponent: () => import('./features/candidate/pages/my-passport/my-passport').then(m => m.MyPassport) },
      { path: 'campus-drives', redirectTo: '/student/drives' },
      { path: 'interview-invites', loadComponent: () => import('./features/candidate/pages/interview-invites/interview-invites').then(m => m.InterviewInvites) },
      { path: 'settings', loadComponent: () => import('./features/candidate/pages/settings/settings').then(m => m.Settings) },
      { path: 'support', loadComponent: () => import('./features/candidate/pages/support/support').then(m => m.Support) },
    ],
  },

  // ---------------------------------------------------------------- recruiter portal
  {
    path: 'recruiter',
    canActivate: [authGuard, roleGuard], data: { roles: ['RECRUITER'] },
    loadComponent: () => import('./features/recruiter/layout/recruiter-layout/recruiter-layout').then(m => m.RecruiterLayout),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/recruiter/pages/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'manage-hiring', loadComponent: () => import('./features/recruiter/pages/manage-hiring/manage-hiring').then(m => m.ManageHiring) },
      { path: 'post-requirement', loadComponent: () => import('./features/recruiter/pages/post-requirement/post-requirement').then(m => m.PostRequirement) },
      { path: 'profile', loadComponent: () => import('./features/recruiter/pages/profile/profile').then(m => m.Profile) },
      { path: 'candidate-detail', loadComponent: () => import('./features/recruiter/pages/candidate-detail/candidate-detail').then(m => m.CandidateDetail) },
      { path: 'chat', loadComponent: () => import('./features/recruiter/pages/chat/chat').then(m => m.Chat) },
      { path: 'settings', loadComponent: () => import('./features/recruiter/pages/settings/settings').then(m => m.Settings) },
      { path: 'support', loadComponent: () => import('./features/recruiter/pages/support/support').then(m => m.Support) },
    ],
  },

  // ---------------------------------------------------------------- platform admin
  {
    path: 'admin/colleges', canActivate: [authGuard, roleGuard], data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/admin/admin-colleges').then(m => m.AdminColleges),
  },
  {
    path: 'admin/prep', title: 'Mock tests', canActivate: [authGuard, roleGuard], data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/admin/admin-prep').then(m => m.AdminPrep),
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
    path: 'student',
    canActivate: [authGuard, roleGuard], data: { roles: ['CANDIDATE'] },
    loadComponent: () => import('./features/student/layout/student-shell').then(m => m.StudentShell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./features/student/dashboard').then(m => m.StudentDashboard) },
      { path: 'drives', title: 'Campus drives', loadComponent: () => import('./features/student/drives').then(m => m.StudentDrives) },
      { path: 'applications', title: 'My applications', loadComponent: () => import('./features/student/applications').then(m => m.StudentApplications) },
      { path: 'interviews', title: 'Interviews', loadComponent: () => import('./features/student/interviews').then(m => m.StudentInterviews) },
      { path: 'offers', title: 'Offers', loadComponent: () => import('./features/student/offers').then(m => m.StudentOffers) },
      { path: 'announcements', title: 'Announcements', loadComponent: () => import('./features/student/announcements').then(m => m.StudentAnnouncements) },
      { path: 'chat', title: 'Chat with placement cell', loadComponent: () => import('./features/student/chat').then(m => m.StudentChat) },
      { path: 'documents', title: 'Document locker', loadComponent: () => import('./features/student/documents').then(m => m.StudentDocuments) },
      { path: 'profile', title: 'My profile', loadComponent: () => import('./features/student/profile').then(m => m.StudentProfile) },
      { path: 'prep', title: 'Preparation', loadComponent: () => import('./features/student/prep/prep-home').then(m => m.PrepHome) },
      { path: 'prep/result/:id', title: 'Test result', loadComponent: () => import('./features/student/prep/prep-result').then(m => m.PrepResult) },
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
