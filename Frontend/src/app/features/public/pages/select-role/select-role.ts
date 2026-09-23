import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';

export interface VerificationTrack {
  id: string;
  title: string;
  desc: string;
  duration: string;
  format: string;
  icon: string;
  recommended?: boolean;
}

@Component({
  selector: 'app-select-role',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './select-role.html',
  styleUrl: './select-role.scss',
})
export class SelectRole {
  private router = inject(Router);

  // View state: 'choose' | 'candidate_wizard' | 'recruiter_wizard'
  viewState: 'choose' | 'candidate_wizard' | 'recruiter_wizard' = 'choose';

  // Stepper step (1-5)
  currentStep = 1;

  // Candidate Form Data
  formData = {
    fullName: 'Rahul Sharma',
    email: 'rahul.sharma@example.com',
    phone: '+91 98765 43210',
    city: 'Bengaluru',
    college: 'RV College of Engineering',
    degree: 'B.Tech',
    graduationYear: 2026,
    stream: 'Computer Science & Engineering',
    status: 'fresher', // 'student' | 'fresher' | 'experienced'
    selectedTrack: 'universal',
    photoUploaded: true,
    idUploaded: true
  };

  // Recruiter Form Data
  recruiterFormData = {
    contactName: 'Vikram Mehta',
    workEmail: 'vikram.mehta@apexplacements.com',
    phone: '+91 98123 45678',
    city: 'Bengaluru',
    companyName: 'Apex Placement Agency',
    orgType: 'agency', // 'agency' | 'startup' | 'corporate' | 'staffing'
    website: 'https://apexplacements.com',
    primaryTrack: 'universal',
    hiringVolume: '5-20',
    candidateLevel: 'fresher',
    accessTier: 'agency_console',
    domainVerified: true,
    gstVerified: true
  };

  consentInvigilation = true;
  consentData = true;
  isSubmitted = false;

  availableTracks: VerificationTrack[] = [
    {
      id: 'universal',
      title: 'Universal Fresher Competency Passport',
      desc: 'Evaluates core logic, QA testing, Spring Boot backend, & debugging in 1 test.',
      duration: '3 Hours',
      format: 'In-Person Lab',
      icon: '🛡️',
      recommended: true
    },
    {
      id: 'java',
      title: 'Java Spring Boot Microservices Track',
      desc: 'REST API design, Spring Security, JPA/Hibernate, SQL queries & performance.',
      duration: '3 Hours',
      format: 'In-Person Lab',
      icon: '☕'
    },
    {
      id: 'qa',
      title: 'QA & Test Automation Engineering',
      desc: 'Selenium/Cypress, REST Assured, test strategy, bug isolation & edge cases.',
      duration: '3 Hours',
      format: 'In-Person Lab',
      icon: '🔧'
    },
    {
      id: 'fullstack',
      title: 'Full Stack Web Engineering (Angular/React & Node)',
      desc: 'Frontend component building, state management, API integration & DB schema.',
      duration: '3 Hours',
      format: 'In-Person Lab',
      icon: '💻'
    }
  ];

  selectRoleTab(role: 'candidate' | 'recruiter') {
    if (role === 'candidate') {
      this.viewState = 'candidate_wizard';
      this.currentStep = 1;
    } else {
      this.viewState = 'recruiter_wizard';
      this.currentStep = 1;
    }
  }

  selectRole(role: 'candidate' | 'recruiter') {
    this.selectRoleTab(role);
  }

  nextStep() {
    if (this.currentStep < 5) {
      this.currentStep++;
    }
  }

  prevStep() {
    if (this.currentStep > 1) {
      this.currentStep--;
    } else {
      this.viewState = 'choose';
    }
  }

  setStep(step: number) {
    if (step <= this.currentStep) {
      this.currentStep = step;
    }
  }

  getStepTitle(): string {
    if (this.viewState === 'candidate_wizard') {
      switch (this.currentStep) {
        case 1: return 'Step 1: Personal & Contact Information';
        case 2: return 'Step 2: Educational Background & Status';
        case 3: return 'Step 3: Choose Verification Track';
        case 4: return 'Step 4: Biometric & Govt ID Document Upload';
        case 5: return 'Step 5: Review Summary & Submit';
        default: return '';
      }
    } else {
      switch (this.currentStep) {
        case 1: return 'Step 1: Recruiter Contact & Location';
        case 2: return 'Step 2: Organization & Company Profile';
        case 3: return 'Step 3: Hiring Requirements & Volume';
        case 4: return 'Step 4: Corporate & GST Identification';
        case 5: return 'Step 5: Review Organization & Activate';
        default: return '';
      }
    }
  }

  getStepSub(): string {
    if (this.viewState === 'candidate_wizard') {
      switch (this.currentStep) {
        case 1: return 'Provide your contact details to receive official Admit Card & test alerts.';
        case 2: return 'Help recruiters verify your academic credentials and graduation timeline.';
        case 3: return 'Select the skill domain you wish to get invigilated for at the exam lab.';
        case 4: return 'Upload original photo and Govt ID for instant gate biometric matching.';
        case 5: return 'Verify your details before submitting your verification registration.';
        default: return '';
      }
    } else {
      switch (this.currentStep) {
        case 1: return 'Provide your contact details for candidate pipeline & admit card dispatch.';
        case 2: return 'Specify your company type, website, and primary hiring focus.';
        case 3: return 'Help us tailor verified candidate recommendations for your hiring targets.';
        case 4: return 'Verify corporate email domain & business registration for employer badge.';
        case 5: return 'Review your organization details before activating recruiter portal access.';
        default: return '';
      }
    }
  }

  getSelectedTrackTitle(): string {
    const track = this.availableTracks.find(t => t.id === this.formData.selectedTrack);
    return track ? track.title : 'Universal Fresher Competency Passport';
  }

  submitRegistration() {
    this.isSubmitted = true;
    setTimeout(() => {
      if (this.viewState === 'recruiter_wizard') {
        this.router.navigate(['/recruiter']);
      } else {
        this.router.navigate(['/candidate']);
      }
    }, 1800);
  }
}
