/** Types of the CXO portal (mirror com.proveyu.cxo.domain.CxoModel). */

export type WidgetType = 'KPI' | 'BAR' | 'COLUMN' | 'LINE' | 'AREA' | 'DONUT' | 'TABLE';
export type Dimension = 'NONE' | 'BRANCH' | 'BATCH' | 'MONTH' | 'COMPANY' | 'STATUS' | 'TYPE' | 'TIER' | 'CTC_BAND' | 'TEST' | 'DIFFICULTY' | 'STAGE'
  | 'ROUND' | 'STUDENT' | 'ANNOUNCEMENT' | 'JOB' | 'YEAR' | 'LANGUAGE' | 'GENDER' | 'CGPA_BAND';
export type Unit = 'COUNT' | 'PERCENT' | 'LPA' | 'SCORE' | 'CGPA';
export type WidgetSize = 'sm' | 'md' | 'lg';

export interface WidgetSpec {
  id: string; title: string; type: WidgetType; metric: string; dimension: Dimension;
  batchYear?: number | null; batches?: number[] | null; branchCode?: string | null; from?: string | null; to?: string | null; limit?: number | null; size: WidgetSize;
}

export interface DashboardSpec { title: string; summary?: string | null; widgets: WidgetSpec[]; }

export interface WidgetData {
  widgetId: string; unit: Unit; value: number | null; labels: string[]; values: (number | null)[];
  /** KPI only: the same number for the previous batch or the same period last year, and which one it is. */
  previous?: number | null; previousLabel?: string | null;
}

export interface Insight { headline: string; bullets: string[]; }

export interface BuildStatus { step: 'understand' | 'plan' | 'query' | 'insight'; message: string; }

export interface BuildDone { spec: DashboardSpec; source: 'AI' | 'RULES'; cached: boolean; durationMs: number; notices?: string[]; }

/** The last dashboard on screen, kept in the browser so the next visit renders instantly. */
export interface Snapshot { spec: DashboardSpec; data: Record<string, WidgetData>; insight: Insight | null; savedId: string | null;
  prompt: string; source: 'AI' | 'RULES' | null; at: number; }

export interface MetricInfo { key: string; label: string; description: string; unit: Unit; dimensions: Dimension[]; drillable?: boolean; }

export interface Catalog { metrics: MetricInfo[]; branches: string[]; batches: number[]; suggestions: string[]; aiEnabled: boolean; }

export interface CxoMe { userId: string; fullName: string; email: string; designation?: string; collegeId: string; collegeName: string; }

export interface DashboardSummary { id: string; title: string; prompt?: string; widgets: number; pinned: boolean; updatedAt: string; }

export interface DashboardDetail { id: string; title: string; prompt?: string; spec: DashboardSpec; pinned: boolean; updatedAt: string; }

/** One event of the live build stream. */
export type BuildEvent =
  | { type: 'status'; data: BuildStatus }
  | { type: 'plan'; data: DashboardSpec }
  | { type: 'widget'; data: WidgetData }
  | { type: 'insight'; data: Insight }
  | { type: 'done'; data: BuildDone }
  | { type: 'error'; data: { message: string } };

// ---------------------------------------------------------------- drill-down, changes, forecast, benchmark, sharing

export interface DrillRow { name: string; roll: string | null; branch: string | null; batch: number | null; cgpa: number | null; status: string | null; placed: boolean; }
export interface Drill { rows: DrillRow[]; truncated: boolean; note: string | null; }

export interface Change { key: string; label: string; unit: Unit; current: number | null; previous: number | null; higherIsBetter: boolean; }
export interface ChangeReport {
  days: number; from: string; to: string; previousFrom: string; previousTo: string; changes: Change[];
  topCompanies: { name: string; count: number }[]; highlights: string[];
}

export interface ForecastPoint { month: string; placed: number | null; low: number | null; base: number | null; high: number | null; }
export interface Forecast {
  batch: number; asOf: string; until: string; eligible: number; placed: number; placedPercent: number; recent30: number; recent60: number; recent90: number;
  perMonthLow: number; perMonthBase: number; perMonthHigh: number; projectedLow: number; projectedBase: number; projectedHigh: number;
  projectedBasePercent: number; confidence: 'OK' | 'LOW'; series: ForecastPoint[]; lastYear: string | null; notes: string[];
}

export interface BenchmarkRow { label: string; values: string[]; note: string | null; }
export interface Benchmark { year: number; years: number[]; rows: BenchmarkRow[]; branchHeader: string[]; branches: string[][]; notes: string[]; }

export interface ShareLink { id: string; dashboardId: string; token: string | null; path: string | null; expiresAt: string; createdAt: string; views: number; revoked: boolean; }
export interface SharedBoard {
  title: string; college: string; sharedBy: string; spec: DashboardSpec; data: WidgetData[]; insight: Insight | null; expiresAt: string; generatedAt: string;
}
