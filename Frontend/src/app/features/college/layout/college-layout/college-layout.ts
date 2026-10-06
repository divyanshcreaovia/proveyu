import { Component, OnInit } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { CollegeSidebar } from '../sidebar/sidebar';
import { CollegeHeader } from '../header/header';
import { CollegeFooter } from '../footer/footer';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-college-layout',
  standalone: true,
  imports: [RouterOutlet, CollegeSidebar, CollegeHeader, CollegeFooter],
  templateUrl: './college-layout.html',
  styleUrl: './college-layout.scss',
})
export class CollegeLayout implements OnInit {
  activeTitle: string = 'College Dashboard';

  private routeTitleMap: { [key: string]: string } = {
    '/college/dashboard': 'TPO College Dashboard',
    '/college/students/create': 'Add New Student',
    '/college/students/import': 'Bulk Import Students',
    '/college/students': 'Student Roster & Profiles',
    '/college/tests/create': 'Create & Schedule Test',
    '/college/tests': 'Proctored Assessments',
    '/college/results/analytics': 'Analytics & Performance Dashboard',
    '/college/placements': 'Campus Placement Hub',
    '/college/team/invite': 'Invite Team Member',
    '/college/team/audit': 'System Audit Logs',
    '/college/team': 'TPO Team Management',
    '/college/reports': 'Reports & Data Exports',
    '/college/settings/profile': 'College Profile Settings',
    '/college/settings/test-config': 'Default Assessment Config',
    '/college/settings/billing': 'Plan & Billing Invoices',
    '/college/support/tickets': 'Submit Support Ticket',
    '/college/support': 'Help Center & Support',
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
      this.activeTitle = 'College TPO Portal';
    }
  }
}
