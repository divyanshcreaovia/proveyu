import { afterNextRender, Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Code ground on the student page: four tabs that take turns, each with a small live picture (sample data). */
@Component({
  selector: 'app-code-arena',
  imports: [RouterLink],
  templateUrl: './code-arena.html',
  styleUrl: './code-arena.scss',
})
export class CodeArena {
  readonly tabs = [
    { title: 'DSA practice', text: '10 topics and 74 problems from easy to hard, with a learning guide for every topic.', file: 'arrays / two-sum.py' },
    { title: 'Problem of the day', text: 'One challenge for everyone, every day. Solve it that day to earn its points again and keep your streak.', file: 'today / daily-challenge' },
    { title: 'Leaderboard', text: 'This week or all time, across your college, your branch or your batch.', file: 'leaderboard / this-week' },
    { title: 'Free compiler', text: 'Try any idea: write a program, give it input and run it. Nothing is scored.', file: 'compiler / main.py' },
  ];
  readonly topics = ['Basics', 'Arrays', 'Strings', 'Hashing', 'Stacks', 'Recursion', 'Sorting', 'DP', 'Maths', 'Graphs'];
  readonly days = Array.from({ length: 84 }, (_, i) => (i > 70 ? 3 : (i * 7 + (i % 5)) % 4));
  readonly board = [
    { place: 2, name: 'Aarav', points: 185, h: 62 },
    { place: 1, name: 'Aditi', points: 275, h: 90 },
    { place: 3, name: 'Imran', points: 125, h: 44 },
  ];
  readonly at = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
    afterNextRender(() => {
      if (!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) this.play();
    });
  }

  /** A click picks a tab and ends the automatic turns, so the reader stays in charge. */
  pick(i: number): void {
    this.stop();
    this.at.set(i);
  }

  private play(): void {
    this.timer = setInterval(() => this.at.update(i => (i + 1) % this.tabs.length), 5000);
  }

  private stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }
}
