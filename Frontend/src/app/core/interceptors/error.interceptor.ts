import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AppError } from '../api';
import { ToastService } from '../services/toast.service';

/** Set to true on a request to suppress the global error toast (the caller shows its own message). */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

/** Converts any HTTP failure to a normalized {@link AppError} and shows a toast (except 401 / validation / silent). */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      let body = err.error;
      if (body instanceof Blob || body instanceof ArrayBuffer) body = null;
      const appError: AppError = {
        status: err.status,
        code: body?.errorCode ?? body?.code ?? (err.status === 0 ? 'NETWORK' : 'HTTP_' + err.status),
        message: err.status === 0 ? 'Cannot reach the server. Check your connection.'
          : body?.message ?? (err.status >= 500 ? 'Something went wrong on our side.' : 'Request failed'),
        fieldErrors: body?.fieldErrors ?? {},
        data: body?.data,
      };
      const silent = req.context.get(SILENT_ERRORS);
      if (!silent && err.status !== 401 && !(err.status === 400 && Object.keys(appError.fieldErrors).length)) {
        toast.error(appError.message);
      }
      return throwError(() => appError);
    }),
  );
};
