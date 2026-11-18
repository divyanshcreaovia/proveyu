import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { api, ApiResponse } from '../../../core/api';
import { FileService } from '../../../core/services/file.service';

// ---------------------------------------------------------------- models (mirror com.proveyu.interview.domain.InterviewModel)
export type InterviewType = 'TECHNICAL' | 'HR' | 'MIXED';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type SessionStatus = 'IN_PROGRESS' | 'EVALUATING' | 'COMPLETED' | 'FAILED';
export type Recommendation = 'READY' | 'ALMOST_READY' | 'NEEDS_PRACTICE';
/** freeLeft 1 = the free interview is available now; otherwise nextFreeAt says when it unlocks (null while one is in progress). */
export interface Credits {
  freeLeft: number; paidBalance: number; canStart: boolean; nextFreeAt?: string | null; freeEveryDays: number; pricePerInterview: number;
  maxPerPurchase: number; paymentsReady: boolean;
}
export type PurchaseStatus = 'PENDING' | 'SUBMITTED' | 'PAID' | 'REJECTED';
/** A UPI purchase: PENDING = QR shown, not submitted; SUBMITTED = waiting for verification. upiUri is set while PENDING. */
export interface StudentPurchase {
  id: string; reference: string; quantity: number; amountRupees: number; status: PurchaseStatus; utr?: string; createdAt: string;
  submittedAt?: string; reviewedAt?: string; note?: string; upiUri?: string; payeeName?: string; upiId?: string;
}
export interface AdminPurchase {
  id: string; reference: string; studentName: string; email?: string; rollNumber?: string; collegeName?: string; quantity: number; amountRupees: number;
  status: PurchaseStatus; utr?: string; createdAt: string; submittedAt?: string; reviewedAt?: string; note?: string;
}
/** A UPI payment request: the QR holds upiUri (exact amount, payee, reference in the note). */
export interface PurchaseOrder { purchaseId: string; reference: string; quantity: number; amountRupees: number; upiUri: string; payeeName: string; upiId: string; }
export interface ServiceHealth { llm: boolean; speechToText: boolean; textToSpeech: boolean; }
export interface DriveOption { driveId: string; title: string; companyName?: string; roleTitle?: string; applied: boolean; }
export interface HistoryItem {
  id: string; type: InterviewType; difficulty: Difficulty; roleTitle: string; companyName?: string; status: SessionStatus; startedAt: string; endedAt?: string;
  answered: number; overallScore?: number; recommendation?: Recommendation;
}
export interface Overview {
  credits: Credits; services: ServiceHealth; drives: DriveOption[]; history: HistoryItem[]; openSessionId?: string; purchases: StudentPurchase[];
}
export interface AskedQuestion { id: string; number: number; followUp: boolean; text: string; topic?: string; transcript?: string; }
export interface SessionView {
  id: string; type: InterviewType; difficulty: Difficulty; roleTitle: string; companyName?: string; status: SessionStatus; plannedQuestions: number; answered: number;
  startedAt: string; done: AskedQuestion[]; current?: AskedQuestion; speechAvailable: boolean; maxAnswerSeconds: number;
  /** Technologies the questions are limited to (empty = general fundamentals). */
  focus: string[];
}
/** ANSWERED, SKIPPED by the student (0), or NOT_REACHED because the interview was ended early (0). */
export type AnswerStatus = 'ANSWERED' | 'SKIPPED' | 'NOT_REACHED';
/** One question in the report. Scores 0-5; percent = the answer's score out of 100. answerId is missing when NOT_REACHED. */
export interface AnswerReview {
  questionId: string; number: number; followUp: boolean; question: string; topic?: string; status: AnswerStatus; transcript?: string;
  inputMode?: 'VOICE' | 'TEXT'; durationSeconds?: number; wordsPerMinute?: number; hasAudio: boolean; answerId?: string;
  content?: number; communication?: number; confidence?: number; relevance?: number; percent?: number; feedback?: string; betterAnswer?: string;
}
/** Average of each criterion over all planned questions, 0-5. */
export interface Criteria { content: number; relevance: number; communication: number; confidence: number; }
export interface ReportView {
  sessionId: string; type: InterviewType; difficulty: Difficulty; roleTitle: string; companyName?: string; status: SessionStatus; startedAt: string; endedAt?: string;
  durationSeconds?: number; evaluated: number; total: number; plannedQuestions: number; answeredCount: number; skippedCount: number; notReachedCount: number;
  focus: string[]; ownJobDescription: boolean; overallScore?: number; recommendation?: Recommendation; criteria: Criteria; summary?: string;
  strengths: string[]; improvements: string[]; answers: AnswerReview[]; errorMessage?: string;
}

export const TYPE_LABELS: Record<InterviewType, string> = { TECHNICAL: 'Technical', HR: 'HR', MIXED: 'Technical + HR' };
export const DIFFICULTY_LABELS: Record<Difficulty, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
export const RECOMMENDATION_LABELS: Record<Recommendation, string> = {
  READY: 'Interview ready', ALMOST_READY: 'Almost ready', NEEDS_PRACTICE: 'Needs practice',
};

const B = '/api/v1/college/student/interview';
const A = '/api/v1/admin/interview';

/** Platform admin: check UPI payments against the bank statement. */
@Injectable({ providedIn: 'root' })
export class InterviewPaymentsAdminService {
  private http = inject(HttpClient);
  list(status: PurchaseStatus = 'SUBMITTED'): Observable<AdminPurchase[]> {
    return this.http.get<ApiResponse<AdminPurchase[]>>(api(`${A}/purchases`), { params: { status } }).pipe(map(r => r.data));
  }
  approve(id: string): Observable<AdminPurchase> { return this.http.post<ApiResponse<AdminPurchase>>(api(`${A}/purchases/${id}/approve`), {}).pipe(map(r => r.data)); }
  reject(id: string, note: string): Observable<AdminPurchase> {
    return this.http.post<ApiResponse<AdminPurchase>>(api(`${A}/purchases/${id}/reject`), { note }).pipe(map(r => r.data));
  }
}

/** AI mock interview (student side). */
@Injectable({ providedIn: 'root' })
export class InterviewService {
  private http = inject(HttpClient);
  private files = inject(FileService);

  overview(): Observable<Overview> { return this.http.get<ApiResponse<Overview>>(api(B)).pipe(map(r => r.data)); }
  start(b: { type: InterviewType; difficulty: Difficulty; driveId?: string | null; roleTitle?: string; jobDescription?: string; consent: boolean }): Observable<SessionView> {
    return this.http.post<ApiResponse<SessionView>>(api(`${B}/sessions`), b).pipe(map(r => r.data));
  }
  session(id: string): Observable<SessionView> { return this.http.get<ApiResponse<SessionView>>(api(`${B}/sessions/${id}`)).pipe(map(r => r.data)); }
  answerVoice(id: string, questionId: string, wav: Blob, durationSeconds: number): Observable<SessionView> {
    const f = new FormData();
    f.append('audio', wav, 'answer.wav');
    return this.http.post<ApiResponse<SessionView>>(api(`${B}/sessions/${id}/answers/voice`), f, { params: { questionId, durationSeconds } })
      .pipe(map(r => r.data));
  }
  answerText(id: string, questionId: string, text: string): Observable<SessionView> {
    return this.http.post<ApiResponse<SessionView>>(api(`${B}/sessions/${id}/answers/text`), { questionId, text }).pipe(map(r => r.data));
  }
  /** Returns null when the interview had no answers and was removed (credit returned). */
  end(id: string): Observable<SessionView | null> { return this.http.post<ApiResponse<SessionView | null>>(api(`${B}/sessions/${id}/end`), {}).pipe(map(r => r.data)); }
  report(id: string): Observable<ReportView> { return this.http.get<ApiResponse<ReportView>>(api(`${B}/sessions/${id}/report`)).pipe(map(r => r.data)); }
  retryReport(id: string): Observable<ReportView> { return this.http.post<ApiResponse<ReportView>>(api(`${B}/sessions/${id}/report/retry`), {}).pipe(map(r => r.data)); }
  /** Buy interviews: a UPI payment request at the server's price. */
  buy(quantity: number): Observable<PurchaseOrder> {
    return this.http.post<ApiResponse<PurchaseOrder>>(api(`${B}/purchases`), { quantity }).pipe(map(r => r.data));
  }
  /** UPI: the 12-digit reference the student copied from their payment app. */
  submitUtr(purchaseId: string, utr: string): Observable<StudentPurchase> {
    return this.http.post<ApiResponse<StudentPurchase>>(api(`${B}/purchases/${purchaseId}/utr`), { utr }).pipe(map(r => r.data));
  }
  /** Object URLs: the caller revokes them. */
  questionSpeech(questionId: string): Observable<string> { return this.files.objectUrl(`${B}/questions/${questionId}/speech`); }
  answerAudio(answerId: string): Observable<string> { return this.files.objectUrl(`${B}/answers/${answerId}/audio`); }
}
