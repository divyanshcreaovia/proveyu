import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface DepartmentHead {
  name: string;
  department: string;
  email: string;
  phone: string;
}

@Component({
  selector: 'app-college-profile',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class CollegeProfile {
  activeTab: 'institution' | 'tpo' | 'departments' | 'accreditation' | 'branding' = 'institution';
  isSaved = false;
  toastMessage: string | null = null;

  profile = {
    // Basic Details
    collegeName: 'MGM College of Engineering and Technology',
    shortCode: 'MGMCET-019',
    establishedYear: 2001,
    affiliation: 'University of Mumbai / AICTE Approved',
    aisheCode: 'C-34190',
    website: 'https://mgmcet.ac.in',
    contactEmail: 'contact@mgmcet.ac.in',
    contactPhone: '+91 22 2743 3403',
    logo: 'https://images.unsplash.com/photo-1562774053-701939374585?w=300&auto=format&fit=crop&q=80',
    campusCover: 'https://images.unsplash.com/photo-1541339907198-e08756dedf3f?w=1200&auto=format&fit=crop&q=80',
    
    // Address & Campus Info
    addressLine: 'At Junction NH-4 and Sion-Panvel Expressway, Sector 1, Kamothe',
    city: 'Navi Mumbai',
    state: 'Maharashtra',
    pincode: '410209',
    country: 'India',
    campusType: 'Urban Campus (15+ Acres)',
    totalStudents: 2850,
    enrolledInProveYu: 1240,

    // Placement Overview & Metrics
    placementRate: 78,
    avgCtc: '4.8 LPA',
    highestCtc: '14.5 LPA',
    topRecruiters: 'Amazon, TCS, Infosys, Capgemini, Jio Platforms, L&T',

    // Primary Head TPO
    tpoName: 'Prof. Sanjay Jadhav',
    tpoDesignation: 'Head of Training & Placement Cell',
    tpoEmail: 'sanjay.jadhav@mgmcet.ac.in',
    tpoPhone: '+91 98765 43210',
    tpoAltPhone: '+91 22 2743 3409',
    tpoLinkedin: 'https://linkedin.com/in/sanjayjadhav-tpo',
    tpoCabin: 'Central Admin Block, 2nd Floor, Room 204',

    // Accreditation
    naacGrade: 'A+ Grade (3.32 CGPA)',
    nbaAccredited: true,
    nirfRankBand: 'Rank Band 151 - 200 (Engineering)',
    isoCertified: 'ISO 9001:2015 Certified',

    // Institutional Branding
    primaryColor: '#16122b',
    accentColor: '#4f46e5',
    portalSubdomain: 'mgm-cet.proveyu.io'
  };

  departments: DepartmentHead[] = [
    { department: 'Computer Engineering', name: 'Dr. Anita Deshmukh', email: 'hod.comp@mgmcet.ac.in', phone: '+91 98230 11223' },
    { department: 'Information Technology', name: 'Dr. Vivek Patil', email: 'hod.it@mgmcet.ac.in', phone: '+91 98231 44556' },
    { department: 'Electronics & Telecomm.', name: 'Prof. Sneha Kulkarni', email: 'hod.extc@mgmcet.ac.in', phone: '+91 98232 77889' },
    { department: 'Mechanical Engineering', name: 'Prof. Ramesh Shinde', email: 'hod.mech@mgmcet.ac.in', phone: '+91 98233 99001' }
  ];

  newDept: DepartmentHead = { department: '', name: '', email: '', phone: '' };
  showAddDept = false;

  get profileCompletionScore(): number {
    let score = 50;
    if (this.profile.collegeName) score += 10;
    if (this.profile.website) score += 5;
    if (this.profile.tpoEmail && this.profile.tpoPhone) score += 15;
    if (this.profile.naacGrade) score += 10;
    if (this.departments.length > 2) score += 10;
    return Math.min(100, score);
  }

  showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }

  saveChanges() {
    this.isSaved = true;
    this.showToast('College profile and institutional settings updated successfully!');
    setTimeout(() => (this.isSaved = false), 3000);
  }

  addDepartment() {
    if (!this.newDept.department || !this.newDept.name) {
      this.showToast('Please provide Department Name and Faculty Lead Name.');
      return;
    }
    this.departments.push({ ...this.newDept });
    this.newDept = { department: '', name: '', email: '', phone: '' };
    this.showAddDept = false;
    this.showToast('Department Coordinator added successfully!');
  }

  removeDepartment(index: number) {
    this.departments.splice(index, 1);
    this.showToast('Department removed.');
  }

  onLogoError(event: any) {
    event.target.src = 'https://ui-avatars.com/api/?name=MGM+CET&background=16122b&color=fff&size=128';
  }
}
