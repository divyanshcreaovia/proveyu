import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../../../core/api';
import { MySubmissions, SubmissionQuery, Activity, Submission, Board, Guide, LanguageKey, Overview, Period, Problem, RunResult, Sample, Scope } from './code-ground.models';

const C = '/api/v1/college/student/code-ground';

const LANGUAGE_KEY = 'proveyu_cg_language';
const KEYS: LanguageKey[] = ['javascript', 'python', 'java', 'cpp'];
function storedLanguage(): LanguageKey {
  try { const v = localStorage.getItem(LANGUAGE_KEY) as LanguageKey | null; return v && KEYS.includes(v) ? v : 'javascript'; } catch { return 'javascript'; }
}

/** The language the student codes in: shared by the editor and the learning guides, and remembered in this browser. */
@Injectable({ providedIn: 'root' })
export class CodeLanguage {
  readonly current = signal<LanguageKey>(storedLanguage());
  set(language: LanguageKey): void {
    this.current.set(language);
    try { localStorage.setItem(LANGUAGE_KEY, language); } catch { /* storage blocked */ }
  }
}

@Injectable({ providedIn: 'root' })
export class CodeGroundService {
  private http = inject(HttpClient);

  overview(): Observable<Overview> {
    return this.http.get<ApiResponse<Overview>>(api(C)).pipe(map(r => r.data));
  }
  activity(): Observable<Activity> {
    return this.http.get<ApiResponse<Activity>>(api(`${C}/activity`)).pipe(map(r => r.data));
  }
  submissions(key: string): Observable<Submission[]> {
    return this.http.get<ApiResponse<Submission[]>>(api(`${C}/problems/${encodeURIComponent(key)}/submissions`)).pipe(map(r => r.data));
  }
  mySubmissions(query: SubmissionQuery): Observable<MySubmissions> {
    // empty filters are left out
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== undefined && v !== '' && v !== false)) as Record<string, string>;
    return this.http.get<ApiResponse<MySubmissions>>(api(`${C}/submissions`), { params }).pipe(map(r => r.data));
  }
  submissionCode(id: number): Observable<{ code: string }> {
    return this.http.get<ApiResponse<{ code: string }>>(api(`${C}/submissions/${id}`)).pipe(map(r => r.data));
  }
  leaderboard(period: Period, scope: Scope): Observable<Board> {
    return this.http.get<ApiResponse<Board>>(api(`${C}/leaderboard`), { params: { period, scope } }).pipe(map(r => r.data));
  }
  guide(topic: string): Observable<Guide> {
    return this.http.get<ApiResponse<Guide>>(api(`${C}/topics/${encodeURIComponent(topic)}/guide`)).pipe(map(r => r.data));
  }
  completeGuide(topic: string): Observable<unknown> {
    return this.http.post(api(`${C}/topics/${encodeURIComponent(topic)}/guide/complete`), {});
  }
  problem(key: string): Observable<Problem> {
    return this.http.get<ApiResponse<Problem>>(api(`${C}/problems/${encodeURIComponent(key)}`)).pipe(map(r => r.data));
  }
  /** One input only (null = the example); never marks the problem solved. */
  run(key: string, language: LanguageKey, code: string, input: string | null): Observable<Sample> {
    return this.http.post<ApiResponse<Sample>>(api(`${C}/problems/${encodeURIComponent(key)}/run`), { language, code, input }).pipe(map(r => r.data));
  }
  /** All the tests. */
  submit(key: string, language: LanguageKey, code: string): Observable<RunResult> {
    return this.http.post<ApiResponse<RunResult>>(api(`${C}/problems/${encodeURIComponent(key)}/submit`), { language, code }).pipe(map(r => r.data));
  }
  /** Empty code removes the saved draft. */
  saveDraft(key: string, language: LanguageKey, code: string): Observable<unknown> {
    return this.http.put(api(`${C}/problems/${encodeURIComponent(key)}/draft`), { language, code });
  }
}
