import { Injectable, computed, signal } from '@angular/core';

/** Global count of in-flight HTTP requests (driven by the loading interceptor). */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly pending = signal(0);
  readonly busy = computed(() => this.pending() > 0);
  start() { this.pending.update(n => n + 1); }
  stop() { this.pending.update(n => Math.max(0, n - 1)); }
}
