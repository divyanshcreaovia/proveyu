import { Component, ElementRef, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Home page hero: message on the left, a student with floating product cards (sample data) on the right. */
@Component({
  selector: 'app-home-hero',
  imports: [RouterLink],
  templateUrl: './home-hero.html',
  styleUrl: './home-hero.scss',
})
export class HomeHero {
  readonly readiness = ['Resume uploaded', 'Mock tests', 'Coding practice'];
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /** Moves to the section after the hero, just under the sticky header. */
  scrollDown(): void {
    const header = document.querySelector('nav.navbar')?.getBoundingClientRect().height ?? 0;
    window.scrollTo({ top: window.scrollY + this.host.nativeElement.getBoundingClientRect().bottom - header, behavior: 'smooth' });
  }
}
