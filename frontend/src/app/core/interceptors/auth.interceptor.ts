import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { AuthService } from '../auth/auth.service';
import { isApiUrl, isAuthUrl } from './api-url';

/**
 * `/api` isteklerine `Authorization: Bearer` ekler. 401 gelirse (access token süresi dolmuş)
 * refresh cookie'si ile yeni token alır ve isteği **bir kez** tekrar dener (docs/08).
 *
 * - Eşzamanlı 401'ler tek refresh'i paylaşır (AuthService.refresh).
 * - Yenileme başarısızsa veya yenilenmiş token ile de 401 gelirse oturum biter → login.
 * - Kimlik uçlarında (login/refresh...) yenileme denenmez; aksi halde refresh'in 401'i
 *   yeni bir refresh'i tetikleyip döngüye girerdi.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private readonly auth: AuthService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (!isApiUrl(request.url) || isAuthUrl(request.url)) {
      return next.handle(request);
    }

    return next.handle(this.withToken(request)).pipe(
      catchError((error: unknown) => {
        if (!isUnauthorized(error)) {
          return throwError(() => error);
        }
        return this.auth.refresh().pipe(
          catchError(() => {
            this.auth.sessionExpired();
            return throwError(() => error);
          }),
          // Tekrar deneme next.handle ile doğrudan gider (bu interceptor'dan yeniden geçmez),
          // bu yüzden onun 401'i burada yakalanır: yeni token'la da reddedildiyse
          // (örn. hesap pasifleştirildi) oturum biter, ikinci kez yenileme denenmez.
          switchMap(() =>
            next.handle(this.withToken(request)).pipe(
              catchError((retryError: unknown) => {
                if (isUnauthorized(retryError)) {
                  this.auth.sessionExpired();
                }
                return throwError(() => retryError);
              }),
            ),
          ),
        );
      }),
    );
  }

  private withToken(request: HttpRequest<unknown>): HttpRequest<unknown> {
    const token = this.auth.accessToken;
    return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  }
}

function isUnauthorized(error: unknown): error is HttpErrorResponse {
  return error instanceof HttpErrorResponse && error.status === 401;
}
