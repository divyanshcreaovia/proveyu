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
    path: '**',
    redirectTo: ''
  }
];

