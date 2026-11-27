import { coverage } from './cxo-coverage';
import { DashboardSpec, WidgetSpec } from './cxo.models';

const w = (extra: Partial<WidgetSpec>): WidgetSpec =>
  ({ id: 'w1', title: 'T', type: 'COLUMN', metric: 'offers', dimension: 'BATCH', size: 'md', ...extra });
const spec = (...widgets: WidgetSpec[]): DashboardSpec => ({ title: 'D', summary: null, widgets });

describe('coverage line', () => {
  it('names exactly the years of a side-by-side comparison', () => {
    expect(coverage(spec(w({ batches: [2024, 2026] }), w({ id: 'w2', batches: [2024, 2026] })), {}, [2025, 2026]))
      .toBe('Batches 2024, 2026 side by side · all branches');
  });

  it('names one batch', () => {
    expect(coverage(spec(w({ type: 'KPI', dimension: 'NONE', batchYear: 2025 })), {}, [2025, 2026])).toContain('Batch 2025');
  });

  it('says all batches when no year was asked', () => {
    expect(coverage(spec(w({ type: 'KPI', dimension: 'NONE' })), {}, [2025, 2026])).toContain('All batches (2025, 2026)');
  });
});
