import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../core/api';
import { FileService } from './file.service';

// ---------------------------------------------------------------- models (mirror com.proveyu.prep.domain.PrepModel)
export type PrepLanguage = 'python' | 'java' | 'cpp' | 'javascript';
export interface IoCase { input: string; output: string; }
export interface TestCard {
  id: string; title: string; description?: string; companyTag?: string; durationMinutes: number; questionCount: number; codingCount: number;
  totalMarks: number; sections: string[]; attempts: number; bestScore?: number; inProgressAttemptId?: string; lastAttemptId?: string;
  /** Ladder: level label, position (null = standalone, always open), best % so far, lock state and what unlocks it. */
  level?: 'EASY' | 'MEDIUM' | 'HARD'; order?: number | null; bestPercent?: number | null; locked: boolean; unlockAfter?: string; unlockPercent: number;
  /** Scored more than unlockPercent at least once (exact, as the server decides unlocking). */
  passed: boolean;
}
export interface QuestionView {
  id: string; type: 'MCQ' | 'CODING'; section: string; title?: string; text: string; options?: string[]; marks: number;
  starterCode?: Partial<Record<PrepLanguage, string>>; samples?: IoCase[];
}
export interface PrepAnswer { option?: number; language?: PrepLanguage; code?: string; }
export interface AttemptView {
  attemptId: string; testId: string; title: string; companyTag?: string; durationMinutes: number; startedAt: string; deadlineAt: string;
  serverNow: string; languages: PrepLanguage[]; questions: QuestionView[]; answers: Record<string, PrepAnswer>;
}
export interface CaseResult { index: number; passed: boolean; input?: string; expected?: string; actual?: string; error?: string; timedOut: boolean; }
export interface RunResponse { compileError?: string; cases: CaseResult[]; }
export interface QuestionResult {
  id: string; type: 'MCQ' | 'CODING'; section: string; title?: string; text: string; options?: string[]; yourOption?: number; correctOption?: number;
  explanation?: string; language?: PrepLanguage; code?: string; testsPassed?: number; testsTotal?: number; compileError?: string;
  marksAwarded: number; marks: number;
}
export interface SectionScore { name: string; score: number; max: number; correct: number; total: number; }
export interface AttemptResult {
  attemptId: string; testId: string; title: string; companyTag?: string; startedAt: string; submittedAt: string; timeTakenSeconds: number;
  score: number; maxScore: number; percent: number; sections: SectionScore[]; questions: QuestionResult[];
}
export interface AttemptHistory { attemptId: string; testId: string; title: string; startedAt: string; submittedAt: string; score: number; maxScore: number; }
export interface AdminTest {
  id: string; title: string; description?: string; companyTag?: string; durationMinutes: number; questionCount: number; codingCount: number;
  totalMarks: number; published: boolean; attempts: number; createdAt: string; level?: string; order?: number | null;
  /** College tests only: how many students submitted it and their average score. */
  students?: number; averagePercent?: number | null;
}

export const LANGUAGE_LABELS: Record<PrepLanguage, string> = { python: 'Python 3', java: 'Java 21', cpp: 'C++ 17', javascript: 'JavaScript (Node 20)' };

const S = '/api/v1/college/student/prep';
const A = '/api/v1/admin/prep/tests';
const C = '/api/v1/college/prep/tests';

/** Mock tests: student practice + platform-admin upload. */
@Injectable({ providedIn: 'root' })
export class PrepService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  tests(): Observable<TestCard[]> { return this.http.get<ApiResponse<TestCard[]>>(api(`${S}/tests`)).pipe(map(r => r.data)); }
  history(): Observable<AttemptHistory[]> { return this.http.get<ApiResponse<AttemptHistory[]>>(api(`${S}/attempts`)).pipe(map(r => r.data)); }
  start(testId: string): Observable<AttemptView> { return this.http.post<ApiResponse<AttemptView>>(api(`${S}/tests/${testId}/start`), {}).pipe(map(r => r.data)); }
  attempt(id: string): Observable<AttemptView> { return this.http.get<ApiResponse<AttemptView>>(api(`${S}/attempts/${id}`)).pipe(map(r => r.data)); }
  save(id: string, answers: Record<string, PrepAnswer>): Observable<unknown> { return this.http.put(api(`${S}/attempts/${id}/answers`), answers); }
  run(id: string, questionId: string, language: PrepLanguage, code: string): Observable<RunResponse> {
    return this.http.post<ApiResponse<RunResponse>>(api(`${S}/attempts/${id}/run`), { questionId, language, code }).pipe(map(r => r.data));
  }
  submit(id: string, answers: Record<string, PrepAnswer>): Observable<AttemptResult> {
    return this.http.post<ApiResponse<AttemptResult>>(api(`${S}/attempts/${id}/submit`), answers).pipe(map(r => r.data));
  }
  result(id: string): Observable<AttemptResult> { return this.http.get<ApiResponse<AttemptResult>>(api(`${S}/attempts/${id}/result`)).pipe(map(r => r.data)); }

  adminList(): Observable<AdminTest[]> { return this.http.get<ApiResponse<AdminTest[]>>(api(A)).pipe(map(r => r.data)); }
  adminUpload(file: File): Observable<AdminTest> { return this.http.post<ApiResponse<AdminTest>>(api(A), FileService.form(file)).pipe(map(r => r.data)); }
  adminPublish(id: string, published: boolean): Observable<AdminTest> {
    return this.http.patch<ApiResponse<AdminTest>>(api(`${A}/${id}/publish`), { published }).pipe(map(r => r.data));
  }
  adminDelete(id: string): Observable<unknown> { return this.http.delete(api(`${A}/${id}`)); }
  adminDownload(t: AdminTest): Observable<void> { return this.files.download(`${A}/${t.id}/definition`, {}, `${t.title}.json`); }

  // ---------------------------------------------------------------- college placement team: tests for its own students
  collegeList(): Observable<AdminTest[]> { return this.http.get<ApiResponse<AdminTest[]>>(api(C)).pipe(map(r => r.data)); }
  collegeUpload(file: File): Observable<AdminTest> { return this.http.post<ApiResponse<AdminTest>>(api(C), FileService.form(file)).pipe(map(r => r.data)); }
  collegePublish(id: string, published: boolean): Observable<AdminTest> {
    return this.http.patch<ApiResponse<AdminTest>>(api(`${C}/${id}/publish`), { published }).pipe(map(r => r.data));
  }
  collegeDelete(id: string): Observable<unknown> { return this.http.delete(api(`${C}/${id}`)); }
  collegeDownload(t: AdminTest): Observable<void> { return this.files.download(`${C}/${t.id}/definition`, {}, `${t.title}.json`); }
}
