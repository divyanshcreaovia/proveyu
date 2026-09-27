import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DomainItem {
  id: string;
  name: string;
  code: string;
  description?: string;
  active: boolean;
}

export interface CandidateMatch {
  candidateId: string;
  name: string;
  email: string;
  phone?: string;
  location?: string;
  collegeExp?: string;
  domainId?: string;
  domainName?: string;
  score: number;
  percentileBadge?: string;
  tierBadge?: string;
  skillsList?: string;
  headline?: string;
  verifiedCenter?: string;
  passportId?: string;
  isInvited: boolean;
  isShortlisted?: boolean;
  invitationStatus?: string;
}

export interface JobRequirementPayload {
  title: string;
  description?: string;
  domainId?: string;
  roleTrack?: string;
  requiredSkills?: string;
  experienceLevel?: string;
  minExperienceYears?: number;
  maxExperienceYears?: number;
  minSalaryLpa?: number;
  maxSalaryLpa?: number;
  salaryPackage?: string;
  location?: string;
  workMode?: string;
  minScoreThreshold?: number;
  vacancies?: number;
  targetClient?: string;
  lastDate?: string;
  companyId?: string;
}

export interface BackendJobResponse {
  id: string;
  postedBy: string;
  posterName?: string;
  companyId?: string;
  companyName?: string;
  title: string;
  description?: string;
  domainId?: string;
  domainName?: string;
  roleTrack?: string;
  requiredSkills?: string;
  experienceLevel?: string;
  salaryPackage?: string;
  location?: string;
  workMode?: string;
  minScoreThreshold?: number;
  vacancies?: number;
  targetClient?: string;
  lastDate?: string;
  status: string;
  totalInvitationsCount: number;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class RecruiterJobService {
  private baseUrl = 'http://localhost:8080/api/v1/recruiter/jobs';
  private domainsUrl = 'http://localhost:8080/api/v1/domains';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  getDomains(): Observable<any> {
    return this.http.get<any>(this.domainsUrl, { headers: this.getHeaders() });
  }

  getJobs(): Observable<any> {
    return this.http.get<any>(this.baseUrl, { headers: this.getHeaders() });
  }

  getJobById(jobId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/${jobId}`, { headers: this.getHeaders() });
  }

  getJobMatches(jobId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/${jobId}/matches`, { headers: this.getHeaders() });
  }

  getAllCandidates(domainId?: string): Observable<any> {
    const url = domainId ? `${this.baseUrl}/candidates?domainId=${domainId}` : `${this.baseUrl}/candidates`;
    return this.http.get<any>(url, { headers: this.getHeaders() });
  }

  createJob(payload: JobRequirementPayload): Observable<any> {
    return this.http.post<any>(this.baseUrl, payload, { headers: this.getHeaders() });
  }

  updateJobStatus(jobId: string, status: string): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/${jobId}/status?status=${status}`, {}, { headers: this.getHeaders() });
  }

  deleteJob(jobId: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/${jobId}`, { headers: this.getHeaders() });
  }

  sendJobInvitation(jobId: string, payload: { candidateId: string; candidateScoreId?: string; scoreSnapshot?: number; message?: string; notes?: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/${jobId}/invite`, payload, { headers: this.getHeaders() });
  }

  getAllRecruiterInvitations(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/invitations`, { headers: this.getHeaders() });
  }

  getJobInvitations(jobId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/${jobId}/invitations`, { headers: this.getHeaders() });
  }

  updateInvitationStatus(invitationId: string, status: string, notes?: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/invitations/${invitationId}/status`, { status, notes }, { headers: this.getHeaders() });
  }

  shortlistCandidate(payload: { candidateId: string; jobId?: string; message?: string; notes?: string; scoreSnapshot?: number }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/shortlist`, payload, { headers: this.getHeaders() });
  }

  getCandidateInvitations(candidateId?: string): Observable<any> {
    const url = candidateId ? `${this.baseUrl}/candidate/invitations?candidateId=${candidateId}` : `${this.baseUrl}/candidate/invitations`;
    return this.http.get<any>(url, { headers: this.getHeaders() });
  }

  acceptInvitation(invitationId: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/invitations/${invitationId}/accept`, {}, { headers: this.getHeaders() });
  }

  declineInvitation(invitationId: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/invitations/${invitationId}/decline`, {}, { headers: this.getHeaders() });
  }
}
