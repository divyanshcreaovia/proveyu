import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../../../core/api';

// Mirrors com.proveyu.student.codeground.CodeGroundAuthoring / CodeGroundSchedule.
export type ProblemStatus = 'PUBLISHED' | 'DRAFT' | 'CHANGED' | 'HIDDEN';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type Language = 'javascript' | 'python' | 'java' | 'cpp';
export interface ProblemSummary { key: string; title: string; difficulty: Difficulty; builtIn: boolean; status: ProblemStatus; }
export interface Slide { tag: string; title: string; points: string[]; code?: Partial<Record<Language, string>> | null; }
export interface TopicSummary { key: string; title: string; summary: string; builtIn: boolean; guide: Slide[]; problems: ProblemSummary[]; }
export interface Arg { name: string; type: string; }
export interface IoCase { input: string; output: string; }
export interface ProblemSpec {
  key: string; topicKey: string; title: string; difficulty: Difficulty; statement: string; args: Arg[]; returnType: string; hint: string;
  note?: string | null; tests: IoCase[]; hiddenTests: IoCase[]; solution: { language: Language; code: string } | null;
}
export interface Failure { test: string; input: string; expected: string; got: string; error?: string | null; }
export interface Published { ok: boolean; errors: string[]; compileError?: string | null; failures: Failure[]; }
export interface Template { language: Language; starter: string; solve: string; }
export interface DailyDay {
  date: string; problemKey: string | null; title: string | null; difficulty: Difficulty | null; auto: boolean; unavailable: boolean; bonusesGiven: number;
}

const A = '/api/v1/admin/code-ground';

@Injectable({ providedIn: 'root' })
export class CodeGroundAdminService {
  private http = inject(HttpClient);
  private get<T>(path: string): Observable<T> { return this.http.get<ApiResponse<T>>(api(A + path)).pipe(map(r => r.data)); }
  private key = encodeURIComponent;

  catalogue() { return this.get<TopicSummary[]>(''); }
  saveTopic(t: { key: string; title: string; summary: string; guide: Slide[] }) { return this.http.put(api(`${A}/topics`), t); }
  deleteTopic(key: string) { return this.http.delete(api(`${A}/topics/${this.key(key)}`)); }
  problem(key: string) { return this.get<{ spec: ProblemSpec; status: ProblemStatus }>(`/problems/${this.key(key)}`); }
  saveProblem(spec: ProblemSpec) { return this.http.put(api(`${A}/problems`), spec); }
  publish(key: string): Observable<Published> {
    return this.http.post<ApiResponse<Published>>(api(`${A}/problems/${this.key(key)}/publish`), {}).pipe(map(r => r.data));
  }
  unpublish(key: string) { return this.http.post(api(`${A}/problems/${this.key(key)}/unpublish`), {}); }
  deleteProblem(key: string) { return this.http.delete(api(`${A}/problems/${this.key(key)}`)); }
  setHidden(key: string, hidden: boolean) { return this.http.put(api(`${A}/problems/${this.key(key)}/hidden`), { hidden }); }
  templates(args: Arg[], returnType: string): Observable<Template[]> {
    return this.http.post<ApiResponse<Template[]>>(api(`${A}/templates`), { args, returnType }).pipe(map(r => r.data));
  }
  upload(file: unknown): Observable<{ topics: number; problems: string[] }> {
    return this.http.post<ApiResponse<{ topics: number; problems: string[] }>>(api(`${A}/upload`), file).pipe(map(r => r.data));
  }
  sample(): Observable<string> { return this.http.get(api(`${A}/sample`), { responseType: 'text' }); }
  daily(days: number) { return this.get<DailyDay[]>(`/daily?days=${days}`); }
  setDaily(date: string, problemKey: string) { return this.http.put(api(`${A}/daily/${date}`), { problemKey }); }
  clearDaily(date: string) { return this.http.delete(api(`${A}/daily/${date}`)); }
}
