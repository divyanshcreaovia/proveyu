import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { HighchartsChartComponent } from 'highcharts-angular';
import type { Options } from 'highcharts';
import { ThemeService } from '../../services/theme.service';

/** Candidate-portal palette: navy first, teal second (navy is swapped for indigo in dark mode). */
const PALETTE = ['#16122b', '#06b6d4', '#16a34a', '#f59e0b', '#7c3aed', '#e11d48', '#64748b', '#ea580c'];
const PALETTE_DARK = ['#818cf8', '#22d3ee', '#4ade80', '#fbbf24', '#c084fc', '#fb7185', '#94a3b8', '#fb923c'];

/** Highcharts wrapper that applies the portal palette and follows light / dark mode. */
@Component({
  selector: 'pm-chart',
  imports: [HighchartsChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="pm-chart" [style.height.px]="height()" role="img" [attr.aria-label]="label()">
    <highcharts-chart [options]="merged()" [oneToOne]="true" class="d-block w-100 h-100" /></div>`,
  styles: [`.pm-chart { width: 100%; }`],
})
export class Chart {
  private theme = inject(ThemeService);
  options = input.required<Options>();
  height = input(280);
  label = input('Chart');

  merged = computed<Options>(() => {
    const dark = this.theme.dark();
    const text = dark ? '#cbd5e1' : '#475569';
    const grid = dark ? '#334155' : '#e2e8f0';
    const o = this.options();
    const axis = (a: unknown) => ({ ...(a as object), labels: { style: { color: text } }, title: { ...((a as { title?: object })?.title ?? {}), style: { color: text } },
      gridLineColor: grid, lineColor: grid });
    return {
      colors: dark ? PALETTE_DARK : PALETTE,
      credits: { enabled: false },
      ...o,
      chart: { backgroundColor: 'transparent', style: { fontFamily: 'Inter, sans-serif' }, ...(o.chart ?? {}) },
      title: { text: undefined, ...(o.title ?? {}) },
      legend: { itemStyle: { color: text, fontWeight: '500' }, ...(o.legend ?? {}) },
      plotOptions: { ...(o.plotOptions ?? {}),
        series: { ...(o.plotOptions?.series ?? {}), dataLabels: { style: { color: text, textOutline: 'none', fontWeight: '600' } } } },
      xAxis: Array.isArray(o.xAxis) ? o.xAxis.map(axis) : axis(o.xAxis ?? {}),
      yAxis: Array.isArray(o.yAxis) ? o.yAxis.map(axis) : axis(o.yAxis ?? {}),
    } as Options;
  });
}
