import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  activeTab = 'personal';
  
  candidateInfo = {
    fullName: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    phone: '+91 98765 43210',
    location: 'Bengaluru, Karnataka',
    bio: 'Passionate software engineer with 2+ years of experience in building scalable web applications. Looking for challenging opportunities in product-based companies.'
  };

  education = [
    { degree: 'B.Tech in Computer Science', institution: 'NIT Karnataka', year: '2020 - 2024' }
  ];
}
