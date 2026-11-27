import { Component, computed, input } from '@angular/core';
import { PUBLIC_IMAGES, SceneKind } from './public-images';

interface Float { title: string; sub: string; mark: string; tone?: 'ok' | 'wave'; }

/** What the picture is about, and the two small cards that float over it. */
const SCENES: Record<SceneKind, { alt: string; floats: [Float, Float] }> = {
  drive: { alt: 'A team planning a placement drive', floats: [{ title: 'Eligibility checked', sub: 'Every student', mark: '✓', tone: 'ok' }, { title: 'Report ready', sub: 'Excel, CSV, PDF', mark: '↓' }] },
  eligibility: { alt: 'Students in a classroom', floats: [{ title: 'Rules applied', sub: 'CGPA, branch, batch', mark: '✓', tone: 'ok' }, { title: 'Students notified', sub: 'By e-mail', mark: '@' }] },
  student: { alt: 'Students working together on laptops', floats: [{ title: 'Applied to a drive', sub: 'One click', mark: '✓', tone: 'ok' }, { title: 'Offer letter', sub: 'Accept online', mark: '★' }] },
  company: { alt: 'A recruiter shaking hands with a candidate', floats: [{ title: 'Candidate shortlisted', sub: 'In the Drive Room', mark: '✓', tone: 'ok' }, { title: 'Scorecard saved', sub: 'Technical 9 / 10', mark: '9' }] },
  interview: { alt: 'An interview across a table', floats: [{ title: 'Listening', sub: 'Speak your answer', mark: '', tone: 'wave' }, { title: 'Scored report', sub: 'After every interview', mark: '★' }] },
  test: { alt: 'A laptop with code on the screen', floats: [{ title: 'All tests passed', sub: 'Code submitted', mark: '✓', tone: 'ok' }, { title: 'Next test unlocked', sub: 'Scored above 50%', mark: '→' }] },
  import: { alt: 'Two people working on laptops', floats: [{ title: 'Batch imported', sub: 'Excel or CSV', mark: '↑' }, { title: 'Duplicates flagged', sub: 'Before saving', mark: '!' }] },
  calendar: { alt: 'A planner with a calendar', floats: [{ title: 'Clash blocked', sub: 'Student already booked', mark: '!' }, { title: 'Reminder set', sub: '24 hours before', mark: '✓', tone: 'ok' }] },
  offers: { alt: 'Colleagues celebrating', floats: [{ title: 'Offer accepted', sub: 'Online', mark: '✓', tone: 'ok' }, { title: 'Policy checked', sub: 'One-offer, dream', mark: '★' }] },
  reports: { alt: 'Charts on a laptop screen', floats: [{ title: 'NIRF / NAAC', sub: 'Style summary', mark: '★' }, { title: 'Scheduled e-mail', sub: 'Reports on time', mark: '@' }] },
  announce: { alt: 'A paper speech bubble', floats: [{ title: 'Announcement sent', sub: 'To a chosen audience', mark: '✓', tone: 'ok' }, { title: 'Chat', sub: 'Student ↔ coordinator', mark: '…' }] },
  leadership: { alt: 'A dashboard on a laptop screen', floats: [{ title: 'Question answered', sub: 'Dashboard ready', mark: '✓', tone: 'ok' }, { title: 'Board report', sub: 'PDF or PowerPoint', mark: '↓' }] },
  ask: { alt: 'A college leader in an office', floats: [{ title: 'Question typed', sub: 'In plain words', mark: '?' }, { title: 'Dashboard ready', sub: 'With AI summary', mark: '✓', tone: 'ok' }] },
  boardroom: { alt: 'A report being reviewed on paper', floats: [{ title: 'PDF or PowerPoint', sub: 'Board report', mark: '↓' }, { title: 'Link expires', sub: 'Read only', mark: '✓', tone: 'ok' }] },
  forecast: { alt: 'Two people looking at a screen', floats: [{ title: 'What changed', sub: 'Last 30 days', mark: '↻' }, { title: 'Forecast', sub: 'By year end', mark: '→' }] },
  studentHero: { alt: 'A student with books in a classroom', floats: [{ title: 'Applied to a drive', sub: 'One click', mark: '✓', tone: 'ok' }, { title: 'Interview on Tuesday', sub: '10:00', mark: '★' }] },
  howItWorks: { alt: 'An illustration of steps leading to a published update', floats: [{ title: 'Eligibility checked', sub: 'Every student', mark: '✓', tone: 'ok' }, { title: 'Report ready', sub: 'Excel, CSV, PDF', mark: '↓' }] },
  team: { alt: 'A team meeting in an office', floats: [{ title: 'Roles set', sub: 'Admin, coordinator, viewer', mark: '✓', tone: 'ok' }, { title: 'Audit log', sub: 'Who changed what', mark: '↺' }] },
};

/**
 * A photo (file named in public-images.ts) in the home hero's style, with floating cards.
 * `compact` is the version used at the top of a small card.
 */
@Component({
  selector: 'app-scene',
  host: { '[class.compact]': 'compact()' },
  template: `
    @if (!compact()) { <span class="back" aria-hidden="true"></span><span class="orbit" aria-hidden="true"></span> }
    <div class="photo"><img [src]="src()" [alt]="data().alt" width="1200" height="900" loading="lazy" referrerpolicy="no-referrer"></div>
    @for (f of floats(); track f.title; let i = $index) {
      <div class="float" [class.top]="i === 0" [class.bottom]="i === 1" aria-hidden="true">
        @if (f.tone === 'wave') { <span class="wave">@for (b of bars; track b) { <i></i> }</span> }
        @else { <span class="mark" [class.ok]="f.tone === 'ok'">{{ f.mark }}</span> }
        <span><b>{{ f.title }}</b><small>{{ f.sub }}</small></span>
      </div>
    }
    @if (!compact()) { <span class="spark s1" aria-hidden="true"></span><span class="spark s2" aria-hidden="true"></span> }`,
  styleUrl: './scene.scss',
})
export class Scene {
  readonly kind = input.required<SceneKind>();
  readonly compact = input(false);
  readonly data = computed(() => SCENES[this.kind()]);
  readonly src = computed(() => PUBLIC_IMAGES[this.kind()]);
  readonly floats = computed(() => (this.compact() ? this.data().floats.slice(0, 1) : this.data().floats));
  readonly bars = [0, 1, 2, 3, 4, 5];
}
