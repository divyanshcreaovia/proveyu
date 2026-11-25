import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HighchartsChartComponent } from 'highcharts-angular';
import type { Chart, Options } from 'highcharts';

/** Leadership palette: shades of one calm blue, then neutral slate and one warm tone for a rare extra category. */
export const CXO_COLORS = ['#2b59c3', '#8fa9e6', '#1e2f5c', '#c9d6f4', '#64748b', '#b45309', '#cbd5e1'];
const TEXT = '#64748b';
const GRID = 'rgba(15,23,42,0.07)';

/** Highcharts for the CXO portal: light, compact, white tooltip card with a soft shadow. */
@Component({
  selector: 'pm-cxo-chart',
  imports: [HighchartsChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="xc" [style.height.px]="height()" role="img" [attr.aria-label]="label()">
    <highcharts-chart [options]="merged()" [oneToOne]="true" (chartInstance)="guard($event)" class="d-block w-100 h-100" /></div>`,
  styles: [`.xc { width: 100%; }`],
})
export class CxoChart {
  /**
   * When the board swaps widgets, a bar chart can be destroyed while its gradient fill is still animating; Highcharts then paints on
   * a renderer that no longer exists and throws. Painting on a destroyed renderer is skipped (once per page, on the shared prototype).
   */
  guard(chart: Chart): void {
    type Painter = { renderer?: { gradients?: unknown }; complexColor: (...a: unknown[]) => unknown; __xcGuard?: boolean };
    const proto = (chart.renderer as unknown as { Element?: { prototype: Painter } }).Element?.prototype;
    if (!proto || proto.__xcGuard) return;
    const paint = proto.complexColor;
    proto.complexColor = function (this: Painter, ...args: unknown[]) {
      return this.renderer?.gradients ? paint.apply(this, args) : undefined;
    };
    proto.__xcGuard = true;
  }

  options = input.required<Options>();
  height = input(220);
  label = input('Chart');

  merged = computed<Options>(() => {
    const o = this.options();
    const axis = (a: unknown) => ({
      lineColor: GRID, gridLineColor: GRID, tickColor: GRID, ...(a as object),
      labels: { style: { color: TEXT, fontSize: '11px' }, ...((a as { labels?: object })?.labels ?? {}) },
      title: { text: undefined },
    });
    return {
      colors: CXO_COLORS,
      credits: { enabled: false },
      title: { text: undefined },
      legend: { enabled: false },
      ...o,
      chart: { backgroundColor: 'transparent', spacing: [6, 4, 4, 4], style: { fontFamily: 'Inter, sans-serif' }, ...(o.chart ?? {}) },
      tooltip: {
        backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#e2e8f0', borderRadius: 12, padding: 10,
        shadow: { color: 'rgba(15,23,42,0.1)', offsetX: 0, offsetY: 4, width: 10 },
        style: { color: '#0f172a', fontSize: '12px', fontWeight: '600' }, ...(o.tooltip ?? {}),
      },
      xAxis: Array.isArray(o.xAxis) ? o.xAxis.map(axis) : axis(o.xAxis ?? {}),
      yAxis: Array.isArray(o.yAxis) ? o.yAxis.map(axis) : axis(o.yAxis ?? {}),
    } as Options;
  });
}
