import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { extractApiError } from '../models/api-error.model';
import { ToastService } from '../services/toast.service';
import { isAuthUrl } from './api-url';
import { SKIP_ERROR_TOAST } from './http-context.tokens';

/**
 * Sayfaların tek tek ele almadığı genel hatalar için bildirim gösterir:
 * - 0 (ağ / sunucuya ulaşılamıyor), 5xx → genel mesaj
 * - 403 → yetki mesajı (kimlik uçları hariç; login'in 403'ünü sayfa gösterir)
 *
 * 400/404/409 gibi hatalar formlara ve sayfalara bırakılır (alan bazlı gösterilir);
 * 401'i AuthInterceptor yönetir. Hata her durumda çağırana iletilir.
 */
@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor(private readonly toast: ToastService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return next.handle(request).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && !request.context.get(SKIP_ERROR_TOAST)) {
          this.notify(error, request.url);
        }
        return throwError(() => error);
      }),
    );
  }

  private notify(error: HttpErrorResponse, url: string): void {
    if (error.status === 0) {
      this.toast.error('Sunucuya ulaşılamıyor. Bağlantınızı kontrol edip tekrar deneyin.');
    } else if (error.status >= 500) {
      this.toast.error('Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.');
    } else if (error.status === 403 && !isAuthUrl(url)) {
      this.toast.error(extractApiError(error)?.message ?? 'Bu işlem için yetkiniz yok.');
    }
  }
}
