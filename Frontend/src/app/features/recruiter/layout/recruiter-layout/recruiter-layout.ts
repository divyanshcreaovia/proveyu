import { Component, OnInit } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { RecruiterSidebar } from '../sidebar/sidebar';
import { RecruiterHeader } from '../header/header';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-recruiter-layout',
  standalone: true,
  imports: [RouterOutlet, RecruiterSidebar, RecruiterHeader],
  templateUrl: './recruiter-layout.html',
  styleUrl: './recruiter-layout.scss',
})
export class RecruiterLayout implements OnInit {
  activeTitle: string = 'Dashboard';

  private routeTitleMap: { [key: string]: string } = {
    '/recruiter/dashboard': 'Recruiter Dashboard',
    '/recruiter/post-requirement': 'Post Requirement',
    '/recruiter/profile': 'Company & Recruiter Profile',
    '/recruiter/candidate-detail': 'Candidate Profiles',
    '/recruiter/chat': 'Candidate Chats & Invites',
    '/recruiter/settings': 'Company Settings',
    '/recruiter/support': 'Help & Support Desk',
  };

  constructor(private router: Router) {}

  ngOnInit() {
    this.updateTitle(this.router.url);
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.updateTitle(event.urlAfterRedirects || event.url);
      });
  }

  private updateTitle(url: string) {
    const matchedKey = Object.keys(this.routeTitleMap).find((key) => url.includes(key));
    if (matchedKey) {
      this.activeTitle = this.routeTitleMap[matchedKey];
    } else {
      this.activeTitle = 'Recruiter Portal';
    }
  }
}
