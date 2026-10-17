import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/public/pages/home/home').then(m => m.Home)
  },
  {
    path: 'how-it-works',
    loadComponent: () => import('./features/public/pages/how-it-works/how-it-works').then(m => m.HowItWorks)
  },
  {
    path: 'why-in-person',
    loadComponent: () => import('./features/public/pages/why-in-person/why-in-person').then(m => m.WhyInPerson)
  },
  {
    path: 'fresher-model',
    loadComponent: () => import('./features/public/pages/fresher-model/fresher-model').then(m => m.FresherModel)
  },
  {
    path: 'select-role',
    loadComponent: () => import('./features/public/pages/select-role/select-role').then(m => m.SelectRole)
  },
  {
    path: 'login',
    loadComponent: () => import('./features/public/pages/login/login').then(m => m.Login)
  },
  {
    path: 'candidate',
    loadComponent: () => import('./features/candidate/layout/candidate-layout/candidate-layout').then(m => m.CandidateLayout),
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/candidate/pages/dashboard/dashboard').then(m => m.Dashboard)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/candidate/pages/profile/profile').then(m => m.Profile)
      },
      {
        path: 'book-slot',
        loadComponent: () => import('./features/candidate/pages/book-slot/book-slot').then(m => m.BookSlot)
      },
      {
        path: 'my-passport',
        loadComponent: () => import('./features/candidate/pages/my-passport/my-passport').then(m => m.MyPassport)
      },
      {
        path: 'interview-invites',
        loadComponent: () => import('./features/candidate/pages/interview-invites/interview-invites').then(m => m.InterviewInvites)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/candidate/pages/settings/settings').then(m => m.Settings)
      },
      {
        path: 'support',
        loadComponent: () => import('./features/candidate/pages/support/support').then(m => m.Support)
      }
    ]
  },
  {
    path: 'recruiter',
    loadComponent: () => import('./features/recruiter/layout/recruiter-layout/recruiter-layout').then(m => m.RecruiterLayout),
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/recruiter/pages/dashboard/dashboard').then(m => m.Dashboard)
      },
      {
        path: 'manage-hiring',
        loadComponent: () => import('./features/recruiter/pages/manage-hiring/manage-hiring').then(m => m.ManageHiring)
      },
      {
        path: 'post-requirement',
        loadComponent: () => import('./features/recruiter/pages/post-requirement/post-requirement').then(m => m.PostRequirement)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/recruiter/pages/profile/profile').then(m => m.Profile)
      },
      {
        path: 'candidate-detail',
        loadComponent: () => import('./features/recruiter/pages/candidate-detail/candidate-detail').then(m => m.CandidateDetail)
      },
      {
        path: 'chat',
        loadComponent: () => import('./features/recruiter/pages/chat/chat').then(m => m.Chat)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/recruiter/pages/settings/settings').then(m => m.Settings)
      },
      {
        path: 'support',
        loadComponent: () => import('./features/recruiter/pages/support/support').then(m => m.Support)
      }
    ]
  },
  {
    path: 'college',
    loadComponent: () => import('./features/college/layout/college-layout/college-layout').then(m => m.CollegeLayout),
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/college/pages/dashboard/dashboard').then(m => m.Dashboard)
      },
      {
        path: 'students',
        loadComponent: () => import('./features/college/pages/students/students').then(m => m.Students)
      },
      {
        path: 'students/create',
        loadComponent: () => import('./features/college/pages/students/student-form').then(m => m.StudentForm)
      },
      {
        path: 'students/import',
        loadComponent: () => import('./features/college/pages/students/student-import').then(m => m.StudentImport)
      },
      {
        path: 'students/:studentId',
        loadComponent: () => import('./features/college/pages/students/student-detail').then(m => m.StudentDetail)
      },
      {
        path: 'students/:studentId/edit',
        loadComponent: () => import('./features/college/pages/students/student-form').then(m => m.StudentForm)
      },
      {
        path: 'tests',
        loadComponent: () => import('./features/college/pages/tests/tests').then(m => m.Tests)
      },
      {
        path: 'tests/create',
        loadComponent: () => import('./features/college/pages/tests/tests').then(m => m.Tests)
      },
      {
        path: 'tests/:testId',
        loadComponent: () => import('./features/college/pages/tests/tests').then(m => m.Tests)
      },
      {
        path: 'results',
        loadComponent: () => import('./features/college/pages/results/results').then(m => m.Results)
      },
      {
        path: 'results/analytics',
        loadComponent: () => import('./features/college/pages/results/results').then(m => m.Results)
      },
      {
        path: 'results/students/:studentId',
        loadComponent: () => import('./features/college/pages/results/results').then(m => m.Results)
      },
      {
        path: 'results/:testId',
        loadComponent: () => import('./features/college/pages/results/results').then(m => m.Results)
      },
      {
        path: 'placements',
        loadComponent: () => import('./features/college/pages/placements/placements').then(m => m.Placements)
      },
      {
        path: 'placements/add-requirement',
        loadComponent: () => import('./features/college/pages/placements/add-requirement').then(m => m.AddRequirement)
      },
      {
        path: 'team',
        loadComponent: () => import('./features/college/pages/team/team').then(m => m.Team)
      },
      {
        path: 'team/invite',
        loadComponent: () => import('./features/college/pages/team/team-invite').then(m => m.TeamInvite)
      },
      {
        path: 'team/audit',
        loadComponent: () => import('./features/college/pages/team/audit-log').then(m => m.AuditLog)
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/college/pages/reports/reports').then(m => m.Reports)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/college/pages/settings/settings').then(m => m.CollegeSettingsShell),
        children: [
          {
            path: 'profile',
            loadComponent: () => import('./features/college/pages/settings/profile').then(m => m.CollegeProfile)
          },
          {
            path: 'test-config',
            loadComponent: () => import('./features/college/pages/settings/test-config').then(m => m.TestConfig)
          },
          {
            path: 'billing',
            loadComponent: () => import('./features/college/pages/settings/billing').then(m => m.Billing)
          },
          {
            path: '',
            redirectTo: 'profile',
            pathMatch: 'full'
          }
        ]
      },
      {
        path: 'support',
        loadComponent: () => import('./features/college/pages/support/support').then(m => m.Support)
      },
      {
        path: 'support/tickets',
        loadComponent: () => import('./features/college/pages/support/ticket-form').then(m => m.TicketForm)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];


