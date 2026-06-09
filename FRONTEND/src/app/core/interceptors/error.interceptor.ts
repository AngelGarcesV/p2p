import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError(err => {
      const msg = err?.error?.message ?? err?.message ?? 'Error de red';
      return throwError(() => new Error(msg));
    })
  );
