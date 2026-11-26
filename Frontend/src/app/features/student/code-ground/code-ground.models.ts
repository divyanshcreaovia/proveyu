// Code ground (practice problems by topic). Mirrors com.proveyu.student.codeground.CodeGroundService.
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
/** attempted = run at least once without passing every test. */
export interface ProblemCard { key: string; title: string; difficulty: Difficulty; solved: boolean; attempted: boolean; }
/** done / total count the problems and the learning guide together (the progress ring). */
export interface TopicCard {
  key: string; title: string; summary: string; guideSlides: number; guideRead: boolean; solved: number; done: number; total: number;
  problems: ProblemCard[];
}
export type LanguageKey = 'javascript' | 'python' | 'java' | 'cpp';
export interface Language { key: LanguageKey; label: string; }
export interface DailyProblem { date: string; key: string; topicKey: string; title: string; difficulty: Difficulty; bonus: number; solvedToday: boolean; }
export interface Overview { languages: Language[]; topics: TopicCard[]; daily: DailyProblem; }
export interface Activity {
  from: string; to: string; days: { date: string; count: number }[]; currentStreak: number; longestStreak: number; activeDays: number; solved: number;
}
export interface Submission {
  id: number; language: LanguageKey; code: string; verdict: 'ACCEPTED' | 'WRONG_ANSWER' | 'TIME_LIMIT' | 'RUNTIME_ERROR' | 'COMPILE_ERROR';
  passed: number; total: number; createdAt: string;
}
/** tag: Why it matters, Real-world use, Core idea, Syntax, Pattern, ... code: one snippet per language. */
export interface Slide { tag: string; title: string; points: string[]; code?: Record<LanguageKey, string> | null; }
export interface Guide { topicKey: string; title: string; slides: Slide[]; read: boolean; }
export interface TestIo { input: string; output: string; }
export interface Problem {
  key: string; topicKey: string; title: string; difficulty: Difficulty; statement: string; inputFormat: string[]; example: TestIo;
  hint: string; note?: string | null; starters: Record<LanguageKey, string>; testCount: number; hiddenCount: number; solved: boolean;
  /** Code saved on the server, per language. */
  drafts: Partial<Record<LanguageKey, string>>;
}
/** printed: what the student's code printed while debugging, apart from the answer. */
export interface CaseResult {
  index: number; passed: boolean; input: string; expected: string; actual: string; printed: string; error?: string | null; timedOut: boolean;
}
/** Run on one input. expected and passed are null for the student's own input. */
export interface Sample {
  compileError?: string | null; input: string; expected: string | null; actual: string; printed: string; error?: string | null; timedOut: boolean;
  passed: boolean | null;
}
/** A hidden test says only whether it passed. */
export interface HiddenCase { index: number; passed: boolean; timedOut: boolean; }
export interface RunResult { compileError?: string | null; cases: CaseResult[]; hidden: HiddenCase[]; allPassed: boolean; solved: boolean; bonus: number; }
export type Period = 'week' | 'all';
export type Scope = 'college' | 'branch' | 'batch';
export interface BoardRow { rank: number; name: string; branch: string | null; batchYear: number; points: number; solved: number; streak: number; you: boolean; }
export interface Standing { rank: number | null; points: number; solved: number; streak: number; pointsToNext: number | null; }
export interface Board {
  period: Period; scope: Scope; weekStart: string; participants: number; top: BoardRow[]; me: Standing; myBranch: string | null; myBatchYear: number;
}
export interface SubmissionQuery {
  page: number; days?: number; from?: string; to?: string; verdict?: string; language?: string; difficulty?: string; q?: string; stuck?: boolean;
}
export interface MySubmission {
  id: number; problemKey: string; title: string; difficulty: Difficulty | null; language: LanguageKey; verdict: Submission['verdict'];
  passed: number; total: number; createdAt: string; available: boolean;
}
export interface MySubmissions {
  summary: { total: number; accepted: number; solvedProblems: number; problems: number; topLanguage: LanguageKey | null };
  items: MySubmission[]; hasMore: boolean;
}
