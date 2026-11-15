import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { DocType, Offer, PortalApplication, PortalDrive, PortalProfile, StudentDocument } from '../core/models/college.models';
import { SILENT_ERRORS } from '../core/interceptors/error.interceptor';
import { HttpContext } from '@angular/common/http';
import { FileService } from './file.service';

const BASE = '/api/v1/college/student';

/** Student (candidate) self-service for campus drives. */
@Injectable({ providedIn: 'root' })
export class StudentPortalService {
  private http = inject(HttpClient);

  profile(): Observable<PortalProfile> {
    return this.http.get<ApiResponse<PortalProfile>>(api(`${BASE}/profile`), { context: new HttpContext().set(SILENT_ERRORS, true) }).pipe(map(r => r.data));
  }
  drives(): Observable<PortalDrive[]> { return this.http.get<ApiResponse<PortalDrive[]>>(api(`${BASE}/drives`)).pipe(map(r => r.data)); }
  apply(driveId: string): Observable<PortalApplication> {
    return this.http.post<ApiResponse<PortalApplication>>(api(`${BASE}/drives/${driveId}/apply`), {}).pipe(map(r => r.data));
  }
  applications(): Observable<PortalApplication[]> { return this.http.get<ApiResponse<PortalApplication[]>>(api(`${BASE}/applications`)).pipe(map(r => r.data)); }
  withdraw(id: string): Observable<PortalApplication> {
    return this.http.post<ApiResponse<PortalApplication>>(api(`${BASE}/applications/${id}/withdraw`), {}).pipe(map(r => r.data));
  }
  offers(): Observable<Offer[]> { return this.http.get<ApiResponse<Offer[]>>(api(`${BASE}/offers`)).pipe(map(r => r.data)); }
  respond(id: string, accept: boolean): Observable<Offer> {
    return this.http.post<ApiResponse<Offer>>(api(`${BASE}/offers/${id}/respond`), { accept }).pipe(map(r => r.data));
  }
  documents(): Observable<StudentDocument[]> { return this.http.get<ApiResponse<StudentDocument[]>>(api(`${BASE}/documents`)).pipe(map(r => r.data)); }
  upload(docType: DocType, file: File): Observable<StudentDocument> {
    return this.http.post<ApiResponse<StudentDocument>>(api(`${BASE}/documents`), FileService.form(file), { params: { docType } }).pipe(map(r => r.data));
  }
}
