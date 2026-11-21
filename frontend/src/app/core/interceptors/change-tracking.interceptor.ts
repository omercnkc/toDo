import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { DashboardService } from '../services/dashboard.service';
import { isApiUrl, isAuthUrl } from './api-url';

const READ_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/**
 * Başarılı her değiştirici API isteğinden (proje, görev, davet, üyelik…) sonra panel özetini bayat işaretler;
 * böylece kullanıcı kendi değişikliğini panelde önbellek süresini beklemeden görür. Tek tek servislere
 * çağrı eklemek yerine burada: yeni bir değiştirici uç eklendiğinde unutulamaz. Kimlik uçları sayılmaz.
 */
@Injectable()
export class ChangeTrackingInterceptor implements HttpInterceptor {
  constructor(private readonly dashboard: DashboardService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (READ_METHODS.has(request.method) || !isApiUrl(request.url) || isAuthUrl(request.url)) {
      return next.handle(request);
    }
    return next.handle(request).pipe(
      tap((event) => {
        if (event instanceof HttpResponse) {
          this.dashboard.markStale();
        }
      }),
    );
  }
}
