import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-college-settings-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class CollegeSettingsShell {
  settingsTabs = [
    { label: 'College Profile', icon: 'bi-building-fill', route: '/college/settings/profile' },
    { label: 'Test Config Defaults', icon: 'bi-sliders', route: '/college/settings/test-config' },
    { label: 'Billing & Plan', icon: 'bi-credit-card-2-front', route: '/college/settings/billing' }
  ];
}
