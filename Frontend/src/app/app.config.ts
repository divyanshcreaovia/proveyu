import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideHighcharts } from 'highcharts-angular';
import * as Highcharts from 'highcharts';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHighcharts({ instance: () => Promise.resolve(Highcharts) })
  ]
};
