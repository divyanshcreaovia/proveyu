import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse, Page } from '../../../core/api';
import { ImportHistory, ImportResult } from '../../../core/models/college.models';
import { FileService } from '../../../core/services/file.service';

@Injectable({ providedIn: 'root' })
export class StudentImportService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  template(): Observable<void> {
    return this.files.download('/api/v1/college/students/import/template', {}, 'student-import-template.xlsx');
  }
  upload(file: File, dryRun: boolean): Observable<ImportResult> {
    return this.http.post<ApiResponse<ImportResult>>(api('/api/v1/college/students/import'), FileService.form(file), { params: { dryRun } })
      .pipe(map(r => r.data));
  }
  history(page = 0, size = 10): Observable<Page<ImportHistory>> {
    return this.http.get<ApiResponse<Page<ImportHistory>>>(api('/api/v1/college/students/import/history'), { params: { page, size } }).pipe(map(r => r.data));
  }
}
