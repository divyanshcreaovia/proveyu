import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';

/** Blocks of the public pages that animate in; two-column blocks come from their own side. */
const BLOCKS = [
  '.hero-text-col > *', '.hero-visual-col', '.hero-section > .container-custom > :not(.hero-container)',
  '.section-header-banner', '.card', '.row > [class*="col-"] > .p-3', '.timeline-step', '.timeline-item', '.faq-item',
  '.pr-intro > *', '.pr-details > *', '.pr-form', '.pr-points li', '.pv-head', '.pv-tile', '.pv-copy > *', 'app-scene', '.pv-card', '.pv-step', '.pv-messy .note', '.pv-tidy .note', '.pv-faq details', '.pv-cta', '.pv-photo',
].join(',');

/**
 * Animates the blocks of a public page as they scroll into view. Uses the Web Animations API, so the page's own
 * CSS transforms and hover effects are left alone. Off for people who ask for reduced motion.
 */
@Directive({ selector: '[appScrollAnimate]' })
export class ScrollAnimate {
  constructor() {
    const root: HTMLElement = inject(ElementRef).nativeElement;
    const destroy = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;

      const blocks = Array.from(root.querySelectorAll<HTMLElement>(BLOCKS));
      const bars = Array.from(root.querySelectorAll<HTMLElement>('.progress-bar'));
      blocks.forEach(el => (el.style.opacity = '0'));

      const observer = new IntersectionObserver(entries => {
        let order = 0; // blocks arriving together follow one another
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const el = entry.target as HTMLElement;
          observer.unobserve(el);
          el.style.opacity = '';
          el.animate(
            [{ opacity: 0, translate: from(el), scale: '0.97' }, { opacity: 1, translate: '0 0', scale: '1' }],
            { duration: 700, delay: Math.min(order++, 6) * 110, easing: 'cubic-bezier(.2,.7,.2,1)', fill: 'backwards' },
          );
        }
      }, { threshold: 0.08 });
      blocks.forEach(el => observer.observe(el));

      const barObserver = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          barObserver.unobserve(entry.target);
          const bar = entry.target as HTMLElement;
          bar.animate([{ width: '0%' }, { width: bar.style.width || '100%' }], { duration: 1100, delay: 300, easing: 'ease-out', fill: 'backwards' });
        }
      });
      bars.forEach(el => barObserver.observe(el));

      destroy.onDestroy(() => { observer.disconnect(); barObserver.disconnect(); });
    });
  }
}

/** Where a block starts: from the side for a half-width column, otherwise from below. */
function from(el: HTMLElement): string {
  const column = el.closest('.col-lg-6');
  if (column?.parentElement && window.innerWidth >= 992) {
    return Array.from(column.parentElement.children).indexOf(column) % 2 === 0 ? '-40px 0' : '40px 0';
  }
  return '0 36px';
}
