import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-college-test-config',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './test-config.html',
  styleUrl: './test-config.scss',
})
export class TestConfig {
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
    setTimeout(() => this.isSaved = false, 2000);
  }
}
