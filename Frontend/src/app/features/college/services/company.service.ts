import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../../../core/api';
import { Company, CompanyDetail, CompanyRequest, Contact, Feedback, MouStatus, RecruiterOption } from '../../../core/models/college.models';
import { FileService } from '../../../core/services/file.service';

const BASE = '/api/v1/college/companies';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  list(q: { q?: string; industry?: string; mouStatus?: MouStatus | null; deleted?: boolean }, page = 0, size = 20, sort = 'name', dir = 'asc'): Observable<Page<Company>> {
    return this.http.get<ApiResponse<Page<Company>>>(api(BASE), { params: params({ ...q, page, size, sort, dir }) }).pipe(map(r => r.data));
  }
  /** All active companies (for selects). */
  all(): Observable<Company[]> { return this.list({}, 0, 200).pipe(map(p => p.content)); }
  get(id: string): Observable<CompanyDetail> { return this.http.get<ApiResponse<CompanyDetail>>(api(`${BASE}/${id}`)).pipe(map(r => r.data)); }
  create(b: CompanyRequest): Observable<Company> { return this.http.post<ApiResponse<Company>>(api(BASE), b).pipe(map(r => r.data)); }
  update(id: string, b: CompanyRequest): Observable<Company> { return this.http.put<ApiResponse<Company>>(api(`${BASE}/${id}`), b).pipe(map(r => r.data)); }
  remove(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/${id}`)); }
  restore(id: string): Observable<Company> { return this.http.post<ApiResponse<Company>>(api(`${BASE}/${id}/restore`), {}).pipe(map(r => r.data)); }
  addContact(id: string, c: Omit<Contact, 'id'>): Observable<Contact> {
    return this.http.post<ApiResponse<Contact>>(api(`${BASE}/${id}/contacts`), c).pipe(map(r => r.data));
  }
  updateContact(contactId: string, c: Omit<Contact, 'id'>): Observable<Contact> {
    return this.http.put<ApiResponse<Contact>>(api(`${BASE}/contacts/${contactId}`), c).pipe(map(r => r.data));
  }
  deleteContact(contactId: string): Observable<unknown> { return this.http.delete(api(`${BASE}/contacts/${contactId}`)); }
  addFeedback(id: string, f: { driveId?: string | null; rating: number; comment?: string }): Observable<Feedback> {
    return this.http.post<ApiResponse<Feedback>>(api(`${BASE}/${id}/feedback`), f).pipe(map(r => r.data));
  }
  invite(id: string, email: string, message?: string): Observable<unknown> { return this.http.post(api(`${BASE}/${id}/invite`), { email, message }); }
  searchRecruiters(q: string): Observable<RecruiterOption[]> {
    return this.http.get<ApiResponse<RecruiterOption[]>>(api(`${BASE}/recruiters`), { params: { q } }).pipe(map(r => r.data));
  }
  export(format: string, q: Record<string, unknown> = {}): Observable<void> { return this.files.download(`${BASE}/export`, { ...q, format }, `companies.${format}`); }
}
