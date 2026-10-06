import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface TeamMember {
  id: string;
  name: string;
  email: string;
  phone: string;
  department: string;
  role: string;
  access: string;
  status: 'Active' | 'Pending Invite' | 'Inactive';
  lastActive: string;
  avatarColor: string;
}

export interface RoleMatrixItem {
  feature: string;
  headTpo: boolean;
  coordinator: boolean;
  adminStaff: boolean;
  invigilator: boolean;
}

@Component({
  selector: 'app-college-team',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule],
  templateUrl: './team.html',
  styleUrl: './team.scss',
})
export class Team {
  toastMessage: string | null = null;
  activeTab: 'members' | 'roles_matrix' = 'members';

  // Filters
  searchQuery: string = '';
  selectedRoleFilter: string = 'All';
  selectedDeptFilter: string = 'All';

  // Modal states
  showInviteModal: boolean = false;
  showEditModal: boolean = false;
  selectedMember: TeamMember | null = null;

  // New Invite Form Model
  inviteForm = {
    name: '',
    email: '',
    phone: '',
    department: 'Computer Science',
    role: 'Faculty Coordinator (CSE)',
    access: '80% Access',
  };

  teamMembers: TeamMember[] = [
    {
      id: 'MEM-001',
      name: 'Prof. Sanjay Jadhav',
      email: 'sanjay@mgmcet.ac.in',
      phone: '+91 98201 45678',
      department: 'TPO Central Cell',
      role: 'Head TPO (Super Admin)',
      access: 'Full Access (100%)',
      status: 'Active',
      lastActive: 'Just now',
      avatarColor: '#16122b',
    },
    {
      id: 'MEM-002',
      name: 'Dr. Rahul Sharma',
      email: 'rahul.sharma@mgmcet.ac.in',
      phone: '+91 98203 11223',
      department: 'Computer Science',
      role: 'Faculty Coordinator (CSE)',
      access: '80% Access',
      status: 'Active',
      lastActive: '20 mins ago',
      avatarColor: '#2563eb',
    },
    {
      id: 'MEM-003',
      name: 'Prof. Priya Desai',
      email: 'priya.desai@mgmcet.ac.in',
      phone: '+91 98204 99887',
      department: 'Information Technology',
      role: 'Faculty Coordinator (IT)',
      access: '80% Access',
      status: 'Active',
      lastActive: '1 hour ago',
      avatarColor: '#7c3aed',
    },
    {
      id: 'MEM-004',
      name: 'Amit Patel',
      email: 'amit.patel@mgmcet.ac.in',
      phone: '+91 98205 33445',
      department: 'Placement Cell Operations',
      role: 'Admin Placement Officer',
      access: '60% Access',
      status: 'Active',
      lastActive: '3 hours ago',
      avatarColor: '#059669',
    },
    {
      id: 'MEM-005',
      name: 'Divya Kumar',
      email: 'divya.k@mgmcet.ac.in',
      phone: '+91 98206 55667',
      department: 'Lab & Infrastructure',
      role: 'Test & Lab Invigilator',
      access: '40% Access',
      status: 'Active',
      lastActive: 'Yesterday',
      avatarColor: '#d97706',
    },
    {
      id: 'MEM-006',
      name: 'Sandhya Nair',
      email: 'sandhya.nair@mgmcet.ac.in',
      phone: '+91 98207 77889',
      department: 'Lab & Infrastructure',
      role: 'Test & Lab Invigilator',
      access: '40% Access',
      status: 'Active',
      lastActive: '2 days ago',
      avatarColor: '#ea580c',
    },
    {
      id: 'MEM-007',
      name: 'Prof. Aniket Joshi',
      email: 'aniket.j@mgmcet.ac.in',
      phone: '+91 98209 11990',
      department: 'Electronics & Telecomm',
      role: 'Faculty Coordinator (EXTC)',
      access: '80% Access',
      status: 'Pending Invite',
      lastActive: 'Never logged in',
      avatarColor: '#9333ea',
    },
  ];

  // RBAC Permission Matrix Reference
  roleMatrix: RoleMatrixItem[] = [
    { feature: 'View Student Diagnostic Profiles & Scores', headTpo: true, coordinator: true, adminStaff: true, invigilator: true },
    { feature: 'Conduct & Proctored Live Test Launches', headTpo: true, coordinator: true, adminStaff: false, invigilator: true },
    { feature: 'Export Excel Matrices & Institutional Reports', headTpo: true, coordinator: true, adminStaff: true, invigilator: false },
    { feature: 'Dispatch Remedial Training SMS / Email Alerts', headTpo: true, coordinator: true, adminStaff: false, invigilator: false },
    { feature: 'Placement Corporate Drives & Shortlisting Engine', headTpo: true, coordinator: true, adminStaff: true, invigilator: false },
    { feature: 'Manage Student Rosters & Add Batch Data', headTpo: true, coordinator: false, adminStaff: true, invigilator: false },
    { feature: 'Team User Invitations & Role Privilege Grants', headTpo: true, coordinator: false, adminStaff: false, invigilator: false },
    { feature: 'Audit Trail & Compliance Security Inspection', headTpo: true, coordinator: false, adminStaff: false, invigilator: false },
  ];

  // Computed KPI Getters
  get totalMembers(): number {
    return this.teamMembers.length;
  }

  get activeMembers(): number {
    return this.teamMembers.filter(m => m.status === 'Active').length;
  }

  get facultyCoordinators(): number {
    return this.teamMembers.filter(m => m.role.toLowerCase().includes('coordinator')).length;
  }

  get invigilatorsCount(): number {
    return this.teamMembers.filter(m => m.role.toLowerCase().includes('invigilator')).length;
  }

  get filteredMembers(): TeamMember[] {
    return this.teamMembers.filter(m => {
      const matchesSearch = !this.searchQuery.trim() ||
        m.name.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        m.email.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        m.department.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        m.role.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesRole = this.selectedRoleFilter === 'All' || m.role.includes(this.selectedRoleFilter);
      const matchesDept = this.selectedDeptFilter === 'All' || m.department === this.selectedDeptFilter;

      return matchesSearch && matchesRole && matchesDept;
    });
  }

  setTab(tab: 'members' | 'roles_matrix') {
    this.activeTab = tab;
  }

  showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 3500);
  }

  openInviteModal() {
    this.inviteForm = {
      name: '',
      email: '',
      phone: '',
      department: 'Computer Science',
      role: 'Faculty Coordinator (CSE)',
      access: '80% Access',
    };
    this.showInviteModal = true;
  }

  closeInviteModal() {
    this.showInviteModal = false;
  }

  submitInvite() {
    if (!this.inviteForm.name || !this.inviteForm.email) {
      alert('Please fill in Member Name and Email.');
      return;
    }

    const newMember: TeamMember = {
      id: `MEM-00${this.teamMembers.length + 1}`,
      name: this.inviteForm.name,
      email: this.inviteForm.email,
      phone: this.inviteForm.phone || '+91 98000 00000',
      department: this.inviteForm.department,
      role: this.inviteForm.role,
      access: this.inviteForm.access,
      status: 'Pending Invite',
      lastActive: 'Invitation Sent',
      avatarColor: '#2563eb',
    };

    this.teamMembers.unshift(newMember);
    this.showInviteModal = false;
    this.showToast(`Invitation sent to ${newMember.email} successfully!`);
  }

  openEditModal(m: TeamMember) {
    this.selectedMember = { ...m };
    this.showEditModal = true;
  }

  closeEditModal() {
    this.showEditModal = false;
    this.selectedMember = null;
  }

  saveEditMember() {
    if (!this.selectedMember) return;
    const index = this.teamMembers.findIndex(x => x.id === this.selectedMember!.id);
    if (index !== -1) {
      this.teamMembers[index] = { ...this.selectedMember };
      this.showToast(`Access updated for ${this.selectedMember.name}`);
    }
    this.closeEditModal();
  }

  resendInvite(m: TeamMember) {
    this.showToast(`Re-sent invitation email link to ${m.email}`);
  }

  toggleMemberStatus(m: TeamMember) {
    m.status = m.status === 'Active' ? 'Inactive' : 'Active';
    this.showToast(`Status updated to ${m.status} for ${m.name}`);
  }

  togglePermission(item: RoleMatrixItem, role: 'headTpo' | 'coordinator' | 'adminStaff' | 'invigilator') {
    if (role === 'headTpo') {
      this.showToast('Super Admin (Head TPO) must retain all security permissions.');
      return;
    }
    item[role] = !item[role];
    const statusText = item[role] ? 'Granted' : 'Revoked';
    const roleName = role === 'coordinator' ? 'Faculty Coordinator' : role === 'adminStaff' ? 'Admin Placement Staff' : 'Test Invigilator';
    this.showToast(`${statusText} privilege: "${item.feature}" for ${roleName}`);
  }

  saveMatrixPermissions() {
    this.showToast('Security permissions and institutional RBAC policy saved successfully!');
  }

  resetMatrixPermissions() {
    this.roleMatrix = [
      { feature: 'View Student Diagnostic Profiles & Scores', headTpo: true, coordinator: true, adminStaff: true, invigilator: true },
      { feature: 'Conduct & Proctored Live Test Launches', headTpo: true, coordinator: true, adminStaff: false, invigilator: true },
      { feature: 'Export Excel Matrices & Institutional Reports', headTpo: true, coordinator: true, adminStaff: true, invigilator: false },
      { feature: 'Dispatch Remedial Training SMS / Email Alerts', headTpo: true, coordinator: true, adminStaff: false, invigilator: false },
      { feature: 'Placement Corporate Drives & Shortlisting Engine', headTpo: true, coordinator: true, adminStaff: true, invigilator: false },
      { feature: 'Manage Student Rosters & Add Batch Data', headTpo: true, coordinator: false, adminStaff: true, invigilator: false },
      { feature: 'Team User Invitations & Role Privilege Grants', headTpo: true, coordinator: false, adminStaff: false, invigilator: false },
      { feature: 'Audit Trail & Compliance Security Inspection', headTpo: true, coordinator: false, adminStaff: false, invigilator: false },
    ];
    this.showToast('Role permissions reset to default institutional template.');
  }

  exportTeamRoster() {
    this.showToast('Exporting TPO Team Roster & Permission Matrix to CSV...');
  }
}
