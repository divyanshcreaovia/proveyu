import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

export interface Requirement {
  id: number;
  jobTitle: string;
  domainTrack: string;
  targetClient: string;
  location: string;
  salaryPackage: string;
  minScoreThreshold: number;
  experienceLevel: string;
  postedDate: string;
  status: 'Active' | 'Closed' | 'Draft';
  candidateMatchesCount: number;
}

@Component({
  selector: 'app-post-requirement',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './post-requirement.html',
  styleUrl: './post-requirement.scss',
})
export class PostRequirement {
  showModal: boolean = false;
  saveNotification: string = '';
  hiringType: 'in-house' | 'client' = 'in-house';

  newRequirement: Partial<Requirement> = {
    jobTitle: 'Associate QA Automation Engineer',
    domainTrack: 'QA & Testing',
    targetClient: 'ProveYu Labs (In-House)',
    location: 'Bengaluru / Remote',
    salaryPackage: '₹8 - 10 LPA',
    minScoreThreshold: 85,
    experienceLevel: 'Fresher / 0-1 Yrs',
  };

  setHiringType(type: 'in-house' | 'client') {
    this.hiringType = type;
    if (type === 'in-house') {
      this.newRequirement.targetClient = 'ProveYu Labs (In-House)';
    } else if (this.newRequirement.targetClient === 'ProveYu Labs (In-House)') {
      this.newRequirement.targetClient = 'CloudScale Technologies';
    }
  }

  postedRequirements: Requirement[] = [
    {
      id: 1,
      jobTitle: 'Associate QA Automation Engineer',
      domainTrack: 'QA & Testing',
      targetClient: 'CloudScale Technologies',
      location: 'Bengaluru / Remote',
      salaryPackage: '₹8 - 10 LPA',
      minScoreThreshold: 85,
      experienceLevel: 'Fresher / 0-1 Yrs',
      postedDate: '23 Sep 2026',
      status: 'Active',
      candidateMatchesCount: 18,
    },
    {
      id: 2,
      jobTitle: 'Junior Java Spring Boot Developer',
      domainTrack: 'Java Spring Boot Microservices',
      targetClient: 'Apex Placement Agency',
      location: 'Hyderabad / On-site',
      salaryPackage: '₹10 - 12 LPA',
      minScoreThreshold: 80,
      experienceLevel: 'Fresher / 0-1 Yrs',
      postedDate: '20 Sep 2026',
      status: 'Active',
      candidateMatchesCount: 32,
    },
    {
      id: 3,
      jobTitle: 'Full Stack React & Node Engineer',
      domainTrack: 'Full Stack Engineering',
      targetClient: 'InnoTech Solutions',
      location: 'Pune / Hybrid',
      salaryPackage: '₹12 - 15 LPA',
      minScoreThreshold: 88,
      experienceLevel: 'Junior / 1-3 Yrs',
      postedDate: '15 Sep 2026',
      status: 'Active',
      candidateMatchesCount: 24,
    },
  ];

  openModal() {
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  submitRequirement() {
    if (!this.newRequirement.jobTitle || !this.newRequirement.targetClient) {
      this.saveNotification = 'Please enter Job Title and Target Client.';
      setTimeout(() => {
        this.saveNotification = '';
      }, 3000);
      return;
    }

    const requirement: Requirement = {
      id: Date.now(),
      jobTitle: this.newRequirement.jobTitle || '',
      domainTrack: this.newRequirement.domainTrack || 'QA & Testing',
      targetClient: this.newRequirement.targetClient || '',
      location: this.newRequirement.location || 'Bengaluru / Remote',
      salaryPackage: this.newRequirement.salaryPackage || '₹8 - 10 LPA',
      minScoreThreshold: Number(this.newRequirement.minScoreThreshold) || 85,
      experienceLevel: this.newRequirement.experienceLevel || 'Fresher / 0-1 Yrs',
      postedDate: 'Just Now',
      status: 'Active',
      candidateMatchesCount: Math.floor(Math.random() * 20) + 12,
    };

    this.postedRequirements.unshift(requirement);
    this.closeModal();

    this.saveNotification = `Verified Requirement "${requirement.jobTitle}" posted successfully!`;
    setTimeout(() => {
      this.saveNotification = '';
    }, 3500);

    // Reset default form state for future postings
    this.newRequirement = {
      jobTitle: 'Associate QA Automation Engineer',
      domainTrack: 'QA & Testing',
      targetClient: 'CloudScale Technologies',
      location: 'Bengaluru / Remote',
      salaryPackage: '₹8 - 10 LPA',
      minScoreThreshold: 85,
      experienceLevel: 'Fresher / 0-1 Yrs',
    };
  }

  deleteRequirement(id: number) {
    this.postedRequirements = this.postedRequirements.filter((r) => r.id !== id);
    this.saveNotification = 'Requirement closed.';
    setTimeout(() => {
      this.saveNotification = '';
    }, 3000);
  }
}
