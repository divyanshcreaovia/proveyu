import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-college-students',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './students.html',
  styleUrl: './students.scss',
})
export class Students {
  searchQuery: string = '';
  selectedBatch: string = 'All';
  selectedTier: string = 'All';
  selectedStatus: string = 'All';

  studentsList = [
    { id: '001', roll: '2024-CSE-001', name: 'Rahul Sharma', email: 'rahul@mgmcet.ac.in', phone: '9876543210', batch: '2024', branch: 'Computer Science', tier: 'Tier 1', tierBadge: 'badge-tier-1', status: 'Placed', company: 'TCS', ctc: '₹3.5L', score: '82/100' },
    { id: '002', roll: '2024-CSE-002', name: 'Priya Desai', email: 'priya@mgmcet.ac.in', phone: '9876543211', batch: '2024', branch: 'Information Tech', tier: 'Tier 1', tierBadge: 'badge-tier-1', status: 'Placed', company: 'Wipro', ctc: '₹4.2L', score: '78/100' },
    { id: '003', roll: '2024-CSE-003', name: 'Arun Patel', email: 'arun@mgmcet.ac.in', phone: '9876543212', batch: '2024', branch: 'Computer Science', tier: 'Tier 2', tierBadge: 'badge-tier-2', status: 'Pending', company: '-', ctc: '-', score: '68/100' },
    { id: '004', roll: '2024-IT-004', name: 'Neha Deshmukh', email: 'neha@mgmcet.ac.in', phone: '9876543213', batch: '2024', branch: 'Information Tech', tier: 'Tier 2', tierBadge: 'badge-tier-2', status: 'Pending', company: 'Amdocs', ctc: '₹3.8L', score: '65/100' },
    { id: '005', roll: '2024-EXTC-005', name: 'Amit Verma', email: 'amit@mgmcet.ac.in', phone: '9876543214', batch: '2024', branch: 'Electronics', tier: 'Tier 2', tierBadge: 'badge-tier-2', status: 'Placed', company: 'Accenture', ctc: '₹3.5L', score: '64/100' },
    { id: '006', roll: '2024-CSE-006', name: 'Divya Nair', email: 'divya@mgmcet.ac.in', phone: '9876543215', batch: '2024', branch: 'Computer Science', tier: 'Tier 3', tierBadge: 'badge-tier-3', status: 'No Match', company: '-', ctc: '-', score: '58/100' },
    { id: '007', roll: '2024-IT-007', name: 'Sanjay Joshi', email: 'sanjay.j@mgmcet.ac.in', phone: '9876543216', batch: '2024', branch: 'Information Tech', tier: 'Tier 1', tierBadge: 'badge-tier-1', status: 'Placed', company: 'Deloitte', ctc: '₹5.5L', score: '88/100' },
    { id: '008', roll: '2024-CSE-008', name: 'Sandhya Kumar', email: 'sandhya@mgmcet.ac.in', phone: '9876543217', batch: '2024', branch: 'Computer Science', tier: 'Tier 2', tierBadge: 'badge-tier-2', status: 'Pending', company: '-', ctc: '-', score: '66/100' },
  ];

  get filteredStudents() {
    return this.studentsList.filter(s => {
      const matchesSearch = s.name.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
                            s.roll.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
                            s.email.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchesBatch = this.selectedBatch === 'All' || s.batch === this.selectedBatch;
      const matchesTier = this.selectedTier === 'All' || s.tier === this.selectedTier;
      const matchesStatus = this.selectedStatus === 'All' || s.status === this.selectedStatus;
      return matchesSearch && matchesBatch && matchesTier && matchesStatus;
    });
  }
}
