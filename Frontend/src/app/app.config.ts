import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling, withViewTransitions } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHighcharts } from 'highcharts-angular';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { loadingInterceptor } from './core/interceptors/loading.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    // Responses travel back through interceptors in REVERSE order, so authInterceptor is last: it sees the raw
    // HttpErrorResponse (401 / 403 codes) before errorInterceptor normalizes it into an AppError and toasts.
    provideHttpClient(withInterceptors([loadingInterceptor, errorInterceptor, authInterceptor])),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })),
    provideHighcharts({ instance: () => import('highcharts').then(m => m.default ?? m) }),
  ],
};
