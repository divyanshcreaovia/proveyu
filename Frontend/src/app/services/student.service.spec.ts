import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StudentService } from './student.service';
import { environment } from '../core/environments/environment';

describe('StudentService', () => {
  let svc: StudentService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(StudentService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('sends server-side paging, sorting and only non-empty filters', () => {
    let total = 0;
    svc.list({ q: 'asha', branchId: 'b1', minCgpa: null, placed: false, skill: '' }, 2, 50, 'cgpa', 'desc').subscribe(p => (total = p.totalElements));
    const req = http.expectOne(r => r.url === `${environment.apiBaseUrl}/api/v1/college/students`);
    const p = req.request.params;
    expect(p.get('q')).toBe('asha');
    expect(p.get('branchId')).toBe('b1');
    expect(p.get('placed')).toBe('false');
    expect(p.has('minCgpa')).toBe(false);
    expect(p.has('skill')).toBe(false);
    expect([p.get('page'), p.get('size'), p.get('sort'), p.get('dir')]).toEqual(['2', '50', 'cgpa', 'desc']);
    req.flush({ success: true, data: { content: [], page: 2, size: 50, totalElements: 7, totalPages: 1 } });
    expect(total).toBe(7);
  });

  it('never sends a collegeId (tenant comes from the token)', () => {
    svc.create({ rollNumber: 'R1', firstName: 'A', email: 'a@b.c', branchId: 'b', batchYear: 2027 }).subscribe();
    const req = http.expectOne(`${environment.apiBaseUrl}/api/v1/college/students`);
    expect(req.request.method).toBe('POST');
    expect(JSON.stringify(req.request.body)).not.toContain('collegeId');
    req.flush({ success: true, data: {} });
  });

  it('uploads documents as multipart with the doc type', () => {
    const file = new File(['%PDF'], 'r.pdf', { type: 'application/pdf' });
    svc.uploadDocument('s1', 'RESUME', file).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/students/s1/documents'));
    expect(req.request.params.get('docType')).toBe('RESUME');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({ success: true, data: {} });
  });
});
