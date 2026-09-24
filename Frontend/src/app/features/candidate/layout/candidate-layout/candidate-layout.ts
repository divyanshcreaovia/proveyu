import { Component, OnInit } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { Sidebar } from '../sidebar/sidebar';
import { Header } from '../header/header';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-candidate-layout',
  standalone: true,
  imports: [RouterOutlet, Sidebar, Header],
  templateUrl: './candidate-layout.html',
  styleUrl: './candidate-layout.scss',
})
export class CandidateLayout implements OnInit {
  activeTitle: string = 'Overview';

  private routeTitleMap: { [key: string]: string } = {
    '/candidate/dashboard': 'Overview',
    '/candidate/profile': 'Profile',
    '/candidate/book-slot': 'Book Exam',
    '/candidate/my-passport': 'My Passport',
    '/candidate/prep-guide': 'Prep Guide',
    '/candidate/interview-invites': 'Interview Invites',
    '/candidate/results': 'Results & Offers',
    '/candidate/settings': 'Settings',
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
    const matchedKey = Object.keys(this.routeTitleMap).find(key => url.includes(key));
    if (matchedKey) {
      this.activeTitle = this.routeTitleMap[matchedKey];
    } else {
      this.activeTitle = 'Candidate Portal';
    }
  }
}
