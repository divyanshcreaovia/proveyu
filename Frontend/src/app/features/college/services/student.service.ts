import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page, params } from '../../../core/api';
import {
  DocType, Student, StudentDetail, StudentDocument, StudentFilter, StudentRequest, StudentStatus, VerificationStatus,
} from '../../../core/models/college.models';
import { FileService } from '../../../core/services/file.service';

const BASE = '/api/v1/college/students';

@Injectable({ providedIn: 'root' })
export class StudentService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  list(filter: StudentFilter, page = 0, size = 20, sort = 'rollNumber', dir: 'asc' | 'desc' = 'asc'): Observable<Page<Student>> {
    return this.http.get<ApiResponse<Page<Student>>>(api(BASE), { params: params({ ...filter, page, size, sort, dir }) }).pipe(map(r => r.data));
  }
  get(id: string): Observable<StudentDetail> {
    return this.http.get<ApiResponse<StudentDetail>>(api(`${BASE}/${id}`)).pipe(map(r => r.data));
  }
  create(body: StudentRequest): Observable<Student> {
    return this.http.post<ApiResponse<Student>>(api(BASE), body).pipe(map(r => r.data));
  }
  update(id: string, body: StudentRequest): Observable<Student> {
    return this.http.put<ApiResponse<Student>>(api(`${BASE}/${id}`), body).pipe(map(r => r.data));
  }
  remove(id: string): Observable<unknown> { return this.http.delete(api(`${BASE}/${id}`)); }
  restore(id: string): Observable<Student> {
    return this.http.post<ApiResponse<Student>>(api(`${BASE}/${id}/restore`), {}).pipe(map(r => r.data));
  }
  changeStatus(id: string, status: StudentStatus, reason: string): Observable<Student> {
    return this.http.patch<ApiResponse<Student>>(api(`${BASE}/${id}/status`), { status, reason }).pipe(map(r => r.data));
  }
  export(filter: StudentFilter, format: 'xlsx' | 'csv' | 'pdf'): Observable<void> {
    return this.files.download(`${BASE}/export`, { ...filter, format }, `students.${format}`);
  }
  uploadDocument(id: string, docType: DocType, file: File): Observable<StudentDocument> {
    return this.http.post<ApiResponse<StudentDocument>>(api(`${BASE}/${id}/documents`), FileService.form(file), { params: { docType } }).pipe(map(r => r.data));
  }
  verifyDocument(docId: string, status: VerificationStatus, remarks?: string): Observable<StudentDocument> {
    return this.http.patch<ApiResponse<StudentDocument>>(api(`${BASE}/documents/${docId}/verify`), { status, remarks }).pipe(map(r => r.data));
  }
  deleteDocument(docId: string): Observable<unknown> { return this.http.delete(api(`${BASE}/documents/${docId}`)); }
  openDocument(docId: string): void { this.files.open(`${BASE}/documents/${docId}/file`); }
}
