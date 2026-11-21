import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, params } from '../../../core/api';
import { SILENT_ERRORS } from '../../../core/interceptors/error.interceptor';

export type OrgType = 'COLLEGE' | 'UNIVERSITY' | 'OTHER';
export type EnquiryStatus = 'NEW' | 'CONTACTED' | 'CLOSED';
export interface NewEnquiry {
  fullName: string; email: string; phone: string; organization: string; orgType: OrgType; designation?: string; city?: string;
  studentCount?: string; message?: string; website?: string;
}
export interface Enquiry {
  id: string; fullName: string; email: string; phone: string; organization: string; orgType: OrgType; designation?: string; city?: string;
  studentCount?: string; message?: string; status: EnquiryStatus; adminNote?: string; handledBy?: string; createdAt: string; updatedAt: string;
}
export interface EnquiryPage { rows: Enquiry[]; total: number; page: number; size: number; }

export const ORG_TYPES: { key: OrgType; label: string }[] = [
  { key: 'COLLEGE', label: 'College / Institute' }, { key: 'UNIVERSITY', label: 'University' },
 { key: 'OTHER', label: 'Other' },
];
export const STUDENT_COUNTS: { key: string; label: string }[] = [
  { key: 'UNDER_500', label: 'Under 500' }, { key: '500_1000', label: '500 - 1,000' },
  { key: '1000_3000', label: '1,000 - 3,000' }, { key: '3000_PLUS', label: '3,000+' },
];
export const orgTypeLabel = (k?: string) => ORG_TYPES.find(o => o.key === k)?.label ?? k ?? '';
export const studentCountLabel = (k?: string) => STUDENT_COUNTS.find(o => o.key === k)?.label ?? '';

/** Pricing page form (public, no sign-in) and the admin list of enquiries. */
@Injectable({ providedIn: 'root' })
export class EnquiryService {
  private http = inject(HttpClient);
  /** New enquiries, for the admin menu badge. */
  readonly fresh = signal(0);

  submit(body: NewEnquiry): Observable<ApiResponse<null>> {
    // the form shows its own error under the button
    return this.http.post<ApiResponse<null>>(api('/api/v1/public/pricing-enquiries'), body, { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  list(q: { status?: EnquiryStatus | null; q?: string; page?: number; size?: number }): Observable<EnquiryPage> {
    return this.http.get<ApiResponse<EnquiryPage>>(api('/api/v1/admin/pricing-enquiries'), { params: params(q) }).pipe(map(r => r.data));
  }

  refreshNew(): void {
    this.http.get<ApiResponse<{ new: number }>>(api('/api/v1/admin/pricing-enquiries/new-count'))
      .subscribe({ next: r => this.fresh.set(r.data.new), error: () => undefined });
  }

  setStatus(id: string, status: EnquiryStatus, note?: string): Observable<Enquiry> {
    return this.http.post<ApiResponse<Enquiry>>(api(`/api/v1/admin/pricing-enquiries/${id}/status`), { status, note }).pipe(map(r => r.data));
  }
}
