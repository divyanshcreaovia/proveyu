import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../../../core/api';
import { SILENT_ERRORS } from '../../../core/interceptors/error.interceptor';
import { FileService } from '../../../core/services/file.service';

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
  /** Camera + microphone check (AI proctoring) is on: the student consents and turns them on before the test starts. */
  aiProctoring?: boolean;
  /** College tests: PRACTICE or EXAM; an exam has a window (status UPCOMING / LIVE / CLOSED), one attempt and maybe hidden results. */
  mode?: TestMode; startsAt?: string | null; endsAt?: string | null; examStatus?: ExamStatus | null; resultsReleased?: boolean; collegeTest?: boolean;
}
export type TestMode = 'PRACTICE' | 'EXAM';
export type ExamStatus = 'UPCOMING' | 'LIVE' | 'CLOSED';
export interface QuestionView {
  id: string; type: 'MCQ' | 'CODING' | 'TEXT'; section: string; title?: string; text: string; options?: string[]; marks: number;
  starterCode?: Partial<Record<PrepLanguage, string>>; samples?: IoCase[];
}
export interface PrepAnswer { option?: number; language?: PrepLanguage; code?: string; text?: string; }
export interface AttemptView {
  attemptId: string; testId: string; title: string; companyTag?: string; durationMinutes: number; startedAt: string; deadlineAt: string;
  serverNow: string; languages: PrepLanguage[]; questions: QuestionView[]; answers: Record<string, PrepAnswer>;
  /** Times the student already left this attempt, and how many of those are only warnings (the next one submits). */
  tabSwitches: number; maxWarnings: number;
  /** AI proctoring: when the student consented (null = not yet), whether it is on, events so far and the limit before review. */
  consentAt?: string | null; aiProctoring?: boolean; aiWarnings?: number; maxAiWarnings?: number; mode?: TestMode;
}

// ---------------------------------------------------------------- proctoring (leaving a running test)
/** TAB_HIDDEN: switched tab or minimised; WINDOW_BLUR: moved to another app or window; SHARE_STOPPED: stopped sharing the screen. */
export type SwitchKind = 'TAB_HIDDEN' | 'WINDOW_BLUR' | 'SHARE_STOPPED';
export const SWITCH_LABELS: Record<SwitchKind, string> = {
  TAB_HIDDEN: 'Switched tab or minimised the window', WINDOW_BLUR: 'Moved to another app or window', SHARE_STOPPED: 'Stopped sharing the screen',
};
/** AI proctoring (camera + microphone). These only flag the attempt for human review; they never submit the test. */
export type AiKind = 'NO_FACE' | 'MULTIPLE_FACES' | 'LOOKING_AWAY' | 'CAMERA_BLOCKED' | 'VOICE_DETECTED';
export type ProctorKind = SwitchKind | AiKind;
export const AI_LABELS: Record<AiKind, string> = {
  NO_FACE: 'No face in the camera', MULTIPLE_FACES: 'More than one face in the camera', LOOKING_AWAY: 'Looking away from the screen',
  CAMERA_BLOCKED: 'Camera turned off or blocked', VOICE_DETECTED: 'Voice detected',
};
export const PROCTOR_LABELS: Record<ProctorKind, string> = { ...SWITCH_LABELS, ...AI_LABELS };
export const isAiKind = (k: string): k is AiKind => k in AI_LABELS;
export interface ProctorEvent {
  id: string; kind: ProctorKind; seq: number; occurredAt: string; hasScreenshot: boolean;
  /** AI proctoring event, with the small JSON the browser sent (faces, head angle, speech length). */
  ai?: boolean; metadata?: string | null;
}
export interface ProctorStatus {
  tabSwitches: number; maxWarnings: number; warningsLeft: number; autoSubmitted: boolean; event?: ProctorEvent;
  aiWarnings?: number; maxAiWarnings?: number; flagged?: boolean; recorded?: boolean;
}
export interface ProctorLog {
  attemptId: string; autoSubmitReason?: string; maxWarnings: number; events: ProctorEvent[];
  aiWarnings?: number; maxAiWarnings?: number; flagged?: boolean;
}
export interface CollegeAttempt {
  attemptId: string; studentId: string; studentName: string; rollNumber: string; branchName?: string; testTitle: string; startedAt: string;
  submittedAt?: string; score?: number; maxScore: number; tabSwitches: number; autoSubmitReason?: string; aiWarnings?: number; testId?: string;
}
export interface CaseResult { index: number; passed: boolean; input?: string; expected?: string; actual?: string; error?: string; timedOut: boolean; }
export interface RunResponse { compileError?: string; cases: CaseResult[]; }
/** Code ground: one run with your own input. exitCode null when it did not compile. */
export interface PlaygroundResult { compileError?: string; stdout: string; stderr: string; exitCode?: number | null; timedOut: boolean; }
export interface QuestionResult {
  id: string; type: 'MCQ' | 'CODING' | 'TEXT'; section: string; title?: string; text: string; options?: string[]; yourOption?: number; correctOption?: number;
  explanation?: string; language?: PrepLanguage; code?: string; testsPassed?: number; testsTotal?: number; compileError?: string;
  marksAwarded: number; marks: number;
  /** TEXT: the student's written answer, and whether a teacher still has to mark it. */
  answerText?: string; pendingReview?: boolean;
}
export interface SectionScore { name: string; score: number; max: number; correct: number; total: number; }
export interface AttemptResult {
  attemptId: string; testId: string; title: string; companyTag?: string; startedAt: string; submittedAt: string; timeTakenSeconds: number;
  score: number | null; maxScore: number; percent: number; sections: SectionScore[]; questions: QuestionResult[];
  /** released = false: an exam whose results the college has not published yet (score and answers hidden). */
  released?: boolean; answersShown?: boolean; pendingReview?: boolean;
}
export interface AttemptHistory {
  attemptId: string; testId: string; title: string; startedAt: string; submittedAt: string; score: number; maxScore: number;
  tabSwitches: number; autoSubmitReason?: string; aiWarnings?: number; released?: boolean;
}
export interface AdminTest {
  id: string; title: string; description?: string; companyTag?: string; durationMinutes: number; questionCount: number; codingCount: number;
  totalMarks: number; published: boolean; attempts: number; createdAt: string; level?: string; order?: number | null;
  /** College tests only: how many students submitted it and their average score. */
  students?: number; averagePercent?: number | null;
  exam?: ExamSettings;
}

export const LANGUAGE_LABELS: Record<PrepLanguage, string> = { python: 'Python 3', java: 'Java 21', cpp: 'C++ 17', javascript: 'JavaScript (Node 20)' };

// ---------------------------------------------------------------- college exams
export interface ExamSettings {
  mode: TestMode; startsAt?: string | null; endsAt?: string | null; branchIds: string[]; batchYears: number[];
  shuffleQuestions: boolean; shuffleOptions: boolean; showAnswers: boolean; resultsReleased: boolean;
}
export type MonitorStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'TIME_UP' | 'SUBMITTED';
export interface MonitorRow {
  studentId: string; studentName: string; rollNumber?: string; branchName?: string; batchYear?: number; status: MonitorStatus;
  attemptId?: string; startedAt?: string; deadlineAt?: string; submittedAt?: string; lastActivityAt?: string; answered: number; questions: number;
  score?: number | null; maxScore: number; tabSwitches: number; aiWarnings: number; autoSubmitReason?: string; reviewStatus?: 'PENDING' | 'DONE' | null;
  extraMinutes: number;
}
export interface ExamMonitor {
  testId: string; title: string; mode: TestMode; startsAt?: string; endsAt?: string; serverNow: string; eligible: number; notStarted: number;
  inProgress: number; submitted: number; pendingReview: number; rows: MonitorRow[];
}
export interface ReviewView { attemptId: string; studentName: string; rollNumber?: string; result: AttemptResult; }

const S = '/api/v1/college/student/prep';
const A = '/api/v1/admin/prep/tests';
const C = '/api/v1/college/prep/tests';
const CP = '/api/v1/college/prep';

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
  /** Code ground: runs the code once with the given input. Nothing is saved or scored. */
  playground(language: PrepLanguage, code: string, stdin: string): Observable<PlaygroundResult> {
    return this.http.post<ApiResponse<PlaygroundResult>>(api(`${S}/playground/run`), { language, code, stdin }).pipe(map(r => r.data));
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
  examSettings(id: string, body: ExamSettings): Observable<AdminTest> {
    return this.http.put<ApiResponse<AdminTest>>(api(`${C}/${id}/settings`), body).pipe(map(r => r.data));
  }
  examMonitor(id: string): Observable<ExamMonitor> { return this.http.get<ApiResponse<ExamMonitor>>(api(`${C}/${id}/monitor`)).pipe(map(r => r.data)); }
  examExtend(id: string, attemptId: string, minutes: number): Observable<MonitorRow> {
    return this.http.post<ApiResponse<MonitorRow>>(api(`${C}/${id}/attempts/${attemptId}/extend`), { minutes }).pipe(map(r => r.data));
  }
  examReview(id: string, attemptId: string): Observable<ReviewView> {
    return this.http.get<ApiResponse<ReviewView>>(api(`${C}/${id}/attempts/${attemptId}/review`)).pipe(map(r => r.data));
  }
  examMark(id: string, attemptId: string, marks: Record<string, number>): Observable<ReviewView> {
    return this.http.put<ApiResponse<ReviewView>>(api(`${C}/${id}/attempts/${attemptId}/marks`), marks).pipe(map(r => r.data));
  }
  examExport(t: AdminTest): Observable<void> { return this.files.download(`${C}/${t.id}/export`, {}, `${t.title} - marks.csv`); }
  collegeDownload(t: AdminTest): Observable<void> { return this.files.download(`${C}/${t.id}/definition`, {}, `${t.title}.json`); }

  // ---------------------------------------------------------------- proctoring
  /** The student left the running test; the screenshot (JPEG) is the proof. The server counts and may submit the test. */
  reportSwitch(attemptId: string, kind: SwitchKind, at: Date, screenshot: Blob | null): Observable<ProctorStatus> {
    const f = new FormData();
    f.append('kind', kind);
    f.append('clientAt', at.toISOString());
    if (screenshot) f.append('screenshot', screenshot, 'screen.jpg');
    return this.http.post<ApiResponse<ProctorStatus>>(api(`${S}/attempts/${attemptId}/proctor`), f).pipe(map(r => r.data));
  }
  /** The student agreed to camera + microphone monitoring for this attempt (idempotent). */
  consent(attemptId: string): Observable<{ attemptId: string; consentAt: string }> {
    return this.http.post<ApiResponse<{ attemptId: string; consentAt: string }>>(api(`${S}/attempts/${attemptId}/consent`), {}).pipe(map(r => r.data));
  }
  /** One AI proctoring event (camera / microphone). Errors stay silent: the camera check must never interrupt the test. */
  aiProctorEvent(attemptId: string, e: { kind: AiKind; at: Date; metadata?: Record<string, unknown>; snapshot?: Blob | null }): Observable<ProctorStatus> {
    const f = new FormData();
    f.append('kind', e.kind);
    f.append('clientAt', e.at.toISOString());
    if (e.metadata) f.append('metadata', JSON.stringify(e.metadata));
    if (e.snapshot) f.append('snapshot', e.snapshot, 'camera.jpg');
    return this.http.post<ApiResponse<ProctorStatus>>(api(`${S}/attempts/${attemptId}/proctor/ai`), f,
      { context: new HttpContext().set(SILENT_ERRORS, true) }).pipe(map(r => r.data));
  }
  proctorLog(attemptId: string): Observable<ProctorLog> {
    return this.http.get<ApiResponse<ProctorLog>>(api(`${S}/attempts/${attemptId}/proctor`)).pipe(map(r => r.data));
  }
  /** Object URL of a screenshot (needs the sign-in token, so it cannot be a plain <img src>). Revoke it when done. */
  screenshotUrl(eventId: string): Observable<string> { return this.files.objectUrl(`${S}/proctor/${eventId}/screenshot`); }

  collegeAttempts(flagged: boolean, testId?: string | null): Observable<CollegeAttempt[]> {
    return this.http.get<ApiResponse<CollegeAttempt[]>>(api(`${CP}/attempts`), { params: { flagged, ...(testId ? { testId } : {}) } }).pipe(map(r => r.data));
  }
  collegeProctorLog(attemptId: string): Observable<ProctorLog> {
    return this.http.get<ApiResponse<ProctorLog>>(api(`${CP}/attempts/${attemptId}/proctor`)).pipe(map(r => r.data));
  }
  collegeScreenshotUrl(eventId: string): Observable<string> { return this.files.objectUrl(`${CP}/proctor/${eventId}/screenshot`); }
}
