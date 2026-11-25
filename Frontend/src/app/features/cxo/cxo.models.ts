/** Types of the CXO portal (mirror com.proveyu.cxo.domain.CxoModel). */

export type WidgetType = 'KPI' | 'BAR' | 'COLUMN' | 'LINE' | 'AREA' | 'DONUT' | 'TABLE';
export type Dimension = 'NONE' | 'BRANCH' | 'BATCH' | 'MONTH' | 'COMPANY' | 'STATUS' | 'TYPE' | 'TIER' | 'CTC_BAND' | 'TEST' | 'DIFFICULTY' | 'STAGE'
  | 'ROUND' | 'STUDENT' | 'ANNOUNCEMENT' | 'JOB';
export type Unit = 'COUNT' | 'PERCENT' | 'LPA' | 'SCORE' | 'CGPA';
export type WidgetSize = 'sm' | 'md' | 'lg';

export interface WidgetSpec {
  id: string; title: string; type: WidgetType; metric: string; dimension: Dimension;
  batchYear?: number | null; branchCode?: string | null; from?: string | null; to?: string | null; limit?: number | null; size: WidgetSize;
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

export interface MetricInfo { key: string; label: string; description: string; unit: Unit; dimensions: Dimension[]; }

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
