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
    path: '**',
    redirectTo: ''
  }
];

