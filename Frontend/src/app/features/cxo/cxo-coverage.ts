import { DashboardSpec, WidgetData } from './cxo.models';

const DAY: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' };

/** "2026-01-01" -> "1 Jan 2026". */
export function shortDate(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', DAY);
}

/** A widget's own period: "Since 1 Jan 2026", "1 Jan 2025 – 31 Dec 2025", or '' when it has none. */
export function period(from?: string | null, to?: string | null): string {
  if (from && to) return `${shortDate(from)} – ${shortDate(to)}`;
  if (from) return `Since ${shortDate(from)}`;
  if (to) return `Until ${shortDate(to)}`;
  return '';
}

/**
 * One line that says which data the dashboard shows: the batch(es) it is filtered to (or all batches on record), the branch, the
 * dates, and what the tiles are compared with. E.g. "Batch 2027 · compared with batch 2026 · side by side: 2026, 2027".
 */
export function coverage(spec: DashboardSpec | null, data: Partial<Record<string, WidgetData>>, onRecord: number[]): string {
  if (!spec?.widgets?.length) return '';
  const ws = spec.widgets;
  const parts: string[] = [];
  const side = [...new Set(ws.flatMap(w => w.batches ?? []))].sort();
  if (side.length) { // a comparison: exactly the years asked for
    const parts2: string[] = [`Batches ${side.join(', ')} side by side`];
    const br = [...new Set(ws.map(w => w.branchCode).filter((b): b is string => !!b))];
    parts2.push(br.length ? br.join(', ') : 'all branches');
    return parts2.join(' · ');
  }
  const batches = [...new Set(ws.filter(w => w.batchYear && w.dimension !== 'BATCH').map(w => w.batchYear as number))].sort();
  const all = [...onRecord].sort();
  if (batches.length) {
    parts.push(`Batch ${batches.join(', ')}`);
    if (ws.some(w => !w.batchYear && w.dimension !== 'BATCH' && w.dimension !== 'NONE')) parts.push('some charts: all batches');
  } else {
    parts.push(all.length ? `All batches (${all.join(', ')})` : 'All batches');
  }
  if (batches.length && ws.some(w => w.dimension === 'BATCH') && all.length > 1) parts.push(`side by side: ${all.join(', ')}`);
  const branches = [...new Set(ws.map(w => w.branchCode).filter((b): b is string => !!b))];
  parts.push(branches.length ? branches.join(', ') : 'all branches');
  const periods = [...new Set(ws.map(w => period(w.from, w.to)).filter(Boolean))];
  if (periods.length === 1) parts.push(periods[0]);
  else if (periods.length > 1) parts.push('periods vary by chart');
  else if (ws.some(w => w.dimension === 'MONTH')) parts.push('trends: last 12 months');
  const compared = [...new Set(Object.values(data).map(d => d?.previousLabel).filter((l): l is string => !!l))];
  if (compared.length) parts.push(`compared with ${compared.join(', ')}`);
  return parts.join(' · ');
}
