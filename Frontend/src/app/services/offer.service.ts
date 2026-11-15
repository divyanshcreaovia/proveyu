import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../core/api';
import { Offer, OfferRequest, OfferStatus } from '../core/models/college.models';
import { FileService } from './file.service';

const BASE = '/api/v1/college/offers';

export interface OfferFilter { status?: OfferStatus | null; companyId?: string | null; driveId?: string | null; batchYear?: number | null; q?: string; }

@Injectable({ providedIn: 'root' })
export class OfferService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  list(f: OfferFilter, page = 0, size = 20): Observable<Page<Offer>> {
    return this.http.get<ApiResponse<Page<Offer>>>(api(BASE), { params: params({ ...f, page, size }) }).pipe(map(r => r.data));
  }
  create(b: OfferRequest): Observable<Offer> { return this.http.post<ApiResponse<Offer>>(api(BASE), b).pipe(map(r => r.data)); }
  update(id: string, b: OfferRequest): Observable<Offer> { return this.http.put<ApiResponse<Offer>>(api(`${BASE}/${id}`), b).pipe(map(r => r.data)); }
  changeStatus(id: string, status: OfferStatus, remarks?: string): Observable<Offer> {
    return this.http.patch<ApiResponse<Offer>>(api(`${BASE}/${id}/status`), { status, remarks }).pipe(map(r => r.data));
  }
  uploadLetter(id: string, file: File): Observable<Offer> {
    return this.http.post<ApiResponse<Offer>>(api(`${BASE}/${id}/letter`), FileService.form(file)).pipe(map(r => r.data));
  }
  openLetter(id: string): void { this.files.open(`${BASE}/${id}/letter`); }
  export(format: string, f: OfferFilter = {}): Observable<void> { return this.files.download(`${BASE}/export`, { ...f, format }, `offers.${format}`); }
}
