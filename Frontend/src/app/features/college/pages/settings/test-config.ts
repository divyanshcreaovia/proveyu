import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../../../shared/services/notification.service';

@Component({
  selector: 'app-college-test-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './test-config.html',
  styleUrl: './test-config.scss',
})
export class TestConfig {
  constructor(private notificationService: NotificationService) {}

  config = {
    tier1Cutoff: 70,
    tier2Cutoff: 60,
    showScoresImmediately: true,
    showDetailedFeedback: true,
    allowRetakes: false,
    proctorType: 'Supervised',
    emailReminders: true
  };

  isSaved = false;

  saveDefaults() {
    this.isSaved = true;
    this.notificationService.showSuccess('Default assessment settings updated successfully!');
    setTimeout(() => this.isSaved = false, 2000);
  }
}
