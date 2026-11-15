import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { Batch, Branch, CollegeProfile, EmailTemplate, Policy, Tier } from '../core/models/college.models';
import { FileService } from './file.service';

const BASE = '/api/v1/college/settings';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  profile(): Observable<CollegeProfile> { return this.http.get<ApiResponse<CollegeProfile>>(api(`${BASE}/profile`)).pipe(map(r => r.data)); }
  saveProfile(b: Partial<CollegeProfile>): Observable<CollegeProfile> {
    return this.http.put<ApiResponse<CollegeProfile>>(api(`${BASE}/profile`), b).pipe(map(r => r.data));
  }
  uploadLogo(file: File): Observable<CollegeProfile> {
    return this.http.post<ApiResponse<CollegeProfile>>(api(`${BASE}/logo`), FileService.form(file)).pipe(map(r => r.data));
  }
  logoUrl(): Observable<string> { return this.files.objectUrl(`${BASE}/logo`); }
  policy(): Observable<Policy> { return this.http.get<ApiResponse<Policy>>(api(`${BASE}/policy`)).pipe(map(r => r.data)); }
  savePolicy(p: Policy): Observable<Policy> { return this.http.put<ApiResponse<Policy>>(api(`${BASE}/policy`), p).pipe(map(r => r.data)); }
  branches(): Observable<Branch[]> { return this.http.get<ApiResponse<Branch[]>>(api(`${BASE}/branches`)).pipe(map(r => r.data)); }
  addBranch(b: { code: string; name: string }): Observable<Branch> { return this.http.post<ApiResponse<Branch>>(api(`${BASE}/branches`), b).pipe(map(r => r.data)); }
  updateBranch(id: string, b: { code: string; name: string }): Observable<Branch> {
    return this.http.put<ApiResponse<Branch>>(api(`${BASE}/branches/${id}`), b).pipe(map(r => r.data));
  }
  deleteBranch(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/branches/${id}`)); }
  batches(): Observable<Batch[]> { return this.http.get<ApiResponse<Batch[]>>(api(`${BASE}/batches`)).pipe(map(r => r.data)); }
  saveBatch(b: { gradYear: number; label: string; active: boolean }, id?: string): Observable<Batch> {
    const req = id ? this.http.put<ApiResponse<Batch>>(api(`${BASE}/batches/${id}`), b) : this.http.post<ApiResponse<Batch>>(api(`${BASE}/batches`), b);
    return req.pipe(map(r => r.data));
  }
  deleteBatch(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/batches/${id}`)); }
  tiers(): Observable<Tier[]> { return this.http.get<ApiResponse<Tier[]>>(api(`${BASE}/tiers`)).pipe(map(r => r.data)); }
  saveTiers(tiers: Tier[]): Observable<Tier[]> { return this.http.put<ApiResponse<Tier[]>>(api(`${BASE}/tiers`), { tiers }).pipe(map(r => r.data)); }
  templates(): Observable<EmailTemplate[]> { return this.http.get<ApiResponse<EmailTemplate[]>>(api(`${BASE}/email-templates`)).pipe(map(r => r.data)); }
  saveTemplate(t: EmailTemplate): Observable<EmailTemplate> {
    return this.http.put<ApiResponse<EmailTemplate>>(api(`${BASE}/email-templates/${t.key}`), t).pipe(map(r => r.data));
  }
  resetTemplate(key: string): Observable<unknown> { return this.http.delete(api(`${BASE}/email-templates/${key}`)); }
}
