import { environment } from './environments/environment';

/** Builds an absolute API URL from a path such as `/api/v1/college/students`. */
export const api = (path: string): string => `${environment.apiBaseUrl}${path}`;

/** Standard backend envelope. */
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  errorCode?: string;
  code?: string;
  status?: number;
  fieldErrors?: Record<string, string>;
  timestamp?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Normalized error produced by the error interceptor. */
export interface AppError {
  status: number;
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  data?: unknown;
}

/** Converts a plain object to HttpParams-compatible record, dropping null / undefined / ''. */
export function params(obj: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v === null || v === undefined || v === '') continue;
    out[k] = String(v);
  }
  return out;
}
