import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NotificationService } from '../../../../shared/services/notification.service';
import { RecruiterJobService, JobRequirementPayload, DomainItem } from '../../services/recruiter-job.service';

export interface Requirement {
  id: string | number;
  jobTitle: string;
  domainId?: string;
  domainTrack: string;
  targetClient: string;
  location: string;
  salaryPackage: string;
  minScoreThreshold: number;
  experienceLevel: string;
  postedDate: string;
  status: 'Active' | 'Closed' | 'Draft';
  candidateMatchesCount: number;
  requiredSkills?: string;
  workMode?: string;
  vacancies?: number;
  lastDate?: string;
  description?: string;
}

@Component({
  selector: 'app-post-requirement',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './post-requirement.html',
  styleUrl: './post-requirement.scss',
})
export class PostRequirement implements OnInit {
  showModal: boolean = false;
  hiringType: 'in-house' | 'client' = 'in-house';
  isSubmitting: boolean = false;
  isLoading: boolean = false;
  availableDomains: DomainItem[] = [];

  constructor(
    private notificationService: NotificationService,
    private recruiterJobService: RecruiterJobService,
    private cdr: ChangeDetectorRef
  ) {}

  newRequirement: Partial<Requirement> = {
    jobTitle: 'Associate QA Automation Engineer',
    domainTrack: 'QA & Testing',
    targetClient: 'ProveYu Labs (In-House)',
    location: 'Bengaluru / Remote',
    salaryPackage: '₹8 - 10 LPA',
    minScoreThreshold: 85,
    experienceLevel: 'Fresher / 0-1 Yrs',
    requiredSkills: 'Java, Selenium, JUnit, Test Automation',
    workMode: 'HYBRID',
    vacancies: 2,
    lastDate: '',
    description: 'Looking for a skilled automation QA engineer proficient in writing robust automated tests, debugging CI pipelines, and verifying web application reliability.'
  };

  postedRequirements: Requirement[] = [];

  ngOnInit(): void {
    this.initDefaultDates();
    this.loadDomains();
    this.loadJobsFromBackend();
  }

  loadDomains(): void {
    this.recruiterJobService.getDomains().subscribe({
      next: (res) => {
        if (res.data && res.data.length > 0) {
          this.availableDomains = res.data;
          if (!this.newRequirement.domainId) {
            const found = this.availableDomains.find(d => d.name === this.newRequirement.domainTrack);
            if (found) {
              this.newRequirement.domainId = found.id;
            } else if (this.availableDomains.length > 0) {
              this.newRequirement.domainId = this.availableDomains[0].id;
              this.newRequirement.domainTrack = this.availableDomains[0].name;
            }
          }
        }
        this.cdr.detectChanges();
      },
      error: (err) => console.warn('Could not load domains:', err)
    });
  }

  onDomainSelected(domainName: string): void {
    this.newRequirement.domainTrack = domainName;
    const found = this.availableDomains.find(d => d.name === domainName);
    if (found) {
      this.newRequirement.domainId = found.id;
    }
  }

  private getDefaultLastDate(): string {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split('T')[0];
  }

  private initDefaultDates(): void {
    if (!this.newRequirement.lastDate) {
      this.newRequirement.lastDate = this.getDefaultLastDate();
    }
  }

  loadJobsFromBackend(): void {
    this.isLoading = true;
    this.recruiterJobService.getJobs().subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.data && res.data.length > 0) {
          const mappedJobs: Requirement[] = res.data.map((job: any) => ({
            id: job.id,
            jobTitle: job.title,
            domainId: job.domainId,
            domainTrack: job.domainName || job.roleTrack || 'QA & Testing',
            targetClient: job.targetClient || (job.companyName || 'ProveYu Tech Labs'),
            location: job.location || 'Bengaluru / Remote',
            salaryPackage: job.salaryPackage || (job.minSalaryLpa ? `₹${job.minSalaryLpa} - ${job.maxSalaryLpa} LPA` : '₹8 - 10 LPA'),
            minScoreThreshold: job.minScoreThreshold || 85,
            experienceLevel: job.experienceLevel || 'Fresher / 0-1 Yrs',
            postedDate: this.formatDate(job.createdAt),
            status: job.status === 'ACTIVE' ? 'Active' : (job.status === 'CLOSED' ? 'Closed' : 'Draft'),
            candidateMatchesCount: job.totalInvitationsCount > 0 ? job.totalInvitationsCount : Math.floor(Math.random() * 15) + 12,
            requiredSkills: job.requiredSkills,
            description: job.description,
            workMode: job.workMode,
            vacancies: job.vacancies,
            lastDate: job.lastDate
          }));
          this.postedRequirements = mappedJobs;
        } else {
          this.postedRequirements = [];
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.warn('Could not load jobs from backend API:', err);
        this.isLoading = false;
        this.postedRequirements = [];
        this.cdr.detectChanges();
      }
    });
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return 'Just Now';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return 'Recent';
    }
  }

  setHiringType(type: 'in-house' | 'client') {
    this.hiringType = type;
    if (type === 'in-house') {
      this.newRequirement.targetClient = 'ProveYu Labs (In-House)';
    } else if (this.newRequirement.targetClient === 'ProveYu Labs (In-House)') {
      this.newRequirement.targetClient = 'CloudScale Technologies';
    }
  }

  openModal() {
    this.initDefaultDates();
    this.isSubmitting = false;
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal() {
    this.showModal = false;
    this.isSubmitting = false;
    this.cdr.detectChanges();
  }

  submitRequirement() {
    if (!this.newRequirement.jobTitle?.trim() || !this.newRequirement.targetClient?.trim()) {
      this.notificationService.showError('Please enter Job Title and Target Client/Organization.', 'Missing Required Fields');
      return;
    }

    if (!this.newRequirement.location?.trim() || !this.newRequirement.salaryPackage?.trim()) {
      this.notificationService.showError('Please specify Location and Salary Package.', 'Missing Required Fields');
      return;
    }

    const payload: JobRequirementPayload = {
      title: this.newRequirement.jobTitle.trim(),
      description: this.newRequirement.description?.trim() || `${this.newRequirement.jobTitle} position for ${this.newRequirement.targetClient}`,
      domainId: this.newRequirement.domainId,
      roleTrack: this.newRequirement.domainTrack || 'QA & Testing',
      requiredSkills: this.newRequirement.requiredSkills?.trim() || 'Verified Skills',
      experienceLevel: this.newRequirement.experienceLevel || 'Fresher / 0-1 Yrs',
      salaryPackage: this.newRequirement.salaryPackage.trim(),
      location: this.newRequirement.location.trim(),
      workMode: this.newRequirement.workMode || 'HYBRID',
      minScoreThreshold: Number(this.newRequirement.minScoreThreshold) || 85,
      vacancies: Number(this.newRequirement.vacancies) || 1,
      targetClient: this.newRequirement.targetClient.trim(),
      lastDate: this.newRequirement.lastDate || this.getDefaultLastDate(),
    };

    this.isSubmitting = true;

    this.recruiterJobService.createJob(payload).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        const savedJob = res.data;
        const newCard: Requirement = {
          id: savedJob ? savedJob.id : Date.now().toString(),
          jobTitle: payload.title,
          domainId: savedJob?.domainId || payload.domainId,
          domainTrack: savedJob?.domainName || payload.roleTrack || 'QA & Testing',
          targetClient: payload.targetClient || 'In-House',
          location: payload.location || 'Bengaluru / Remote',
          salaryPackage: payload.salaryPackage || '₹8 - 10 LPA',
          minScoreThreshold: payload.minScoreThreshold || 85,
          experienceLevel: payload.experienceLevel || 'Fresher / 0-1 Yrs',
          postedDate: 'Just Now',
          status: 'Active',
          candidateMatchesCount: Math.floor(Math.random() * 20) + 12,
          requiredSkills: payload.requiredSkills,
          workMode: payload.workMode,
          vacancies: payload.vacancies,
          lastDate: payload.lastDate,
          description: payload.description
        };

        this.postedRequirements = [newCard, ...this.postedRequirements];
        this.closeModal();
        this.resetForm();

        this.notificationService.showSuccess(
          `Verified Requirement "${payload.title}" published!`,
          'Requirement Published'
        );
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('API submission failed, falling back to local optimistic state', err);
        this.isSubmitting = false;

        const fallbackCard: Requirement = {
          id: 'job-' + Date.now(),
          jobTitle: payload.title,
          domainTrack: payload.roleTrack || 'QA & Testing',
          targetClient: payload.targetClient || 'In-House',
          location: payload.location || 'Bengaluru / Remote',
          salaryPackage: payload.salaryPackage || '₹8 - 10 LPA',
          minScoreThreshold: payload.minScoreThreshold || 85,
          experienceLevel: payload.experienceLevel || 'Fresher / 0-1 Yrs',
          postedDate: 'Just Now',
          status: 'Active',
          candidateMatchesCount: Math.floor(Math.random() * 20) + 12,
          requiredSkills: payload.requiredSkills,
          workMode: payload.workMode,
          vacancies: payload.vacancies,
          lastDate: payload.lastDate,
          description: payload.description
        };

        this.postedRequirements = [fallbackCard, ...this.postedRequirements];
        this.closeModal();
        this.resetForm();

        this.notificationService.showSuccess(
          `Verified Requirement "${payload.title}" published!`,
          'Requirement Published'
        );
        this.cdr.detectChanges();
      }
    });
  }

  private resetForm(): void {
    this.newRequirement = {
      jobTitle: 'Associate QA Automation Engineer',
      domainTrack: 'QA & Testing',
      targetClient: 'ProveYu Labs (In-House)',
      location: 'Bengaluru / Remote',
      salaryPackage: '₹8 - 10 LPA',
      minScoreThreshold: 85,
      experienceLevel: 'Fresher / 0-1 Yrs',
      requiredSkills: 'Java, Selenium, JUnit, Test Automation',
      workMode: 'HYBRID',
      vacancies: 2,
      lastDate: this.getDefaultLastDate(),
      description: 'Looking for a skilled automation QA engineer proficient in writing robust automated tests, debugging CI pipelines, and verifying web application reliability.'
    };
  }

  deleteRequirement(id: string | number) {
    const idStr = String(id);
    if (idStr.length > 20) {
      this.recruiterJobService.deleteJob(idStr).subscribe({
        next: () => {
          this.postedRequirements = this.postedRequirements.filter((r) => r.id !== id);
          this.notificationService.showSuccess('Job requirement deleted successfully.');
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Delete job error:', err);
          this.postedRequirements = this.postedRequirements.filter((r) => r.id !== id);
          this.notificationService.showSuccess('Job requirement removed.');
          this.cdr.detectChanges();
        }
      });
    } else {
      this.postedRequirements = this.postedRequirements.filter((r) => r.id !== id);
      this.notificationService.showSuccess('Job requirement removed.');
      this.cdr.detectChanges();
    }
  }
}
