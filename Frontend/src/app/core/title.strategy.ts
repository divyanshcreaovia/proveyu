import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Browser tab titles carry the product name: "Offers · ProveYu Student", "Drives · ProveYu Campus". */
@Injectable({ providedIn: 'root' })
export class BrandTitleStrategy extends TitleStrategy {
  private title = inject(Title);

  override updateTitle(state: RouterStateSnapshot): void {
    const page = this.buildTitle(state);
    const product = state.url.startsWith('/student') ? 'ProveYu Student' : state.url.startsWith('/college') ? 'ProveYu Campus' : 'ProveYu';
    this.title.setTitle(page ? `${page} · ${product}` : product);
  }
}
