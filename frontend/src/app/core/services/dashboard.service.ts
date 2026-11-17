import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { DashboardScope, DashboardSummary } from '../models/dashboard.model';

/** `/api/dashboard/**` (docs/06 "dashboard-service"). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly url = `${environment.apiUrl}/dashboard`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Özet. Hatalar genel bildirimle değil sayfada gösterilir (503: "servise ulaşılamıyor, tekrar dene").
   * @param scope `all` yalnızca ADMIN (aksi 403)
   */
  summary(scope: DashboardScope = 'mine'): Observable<DashboardSummary> {
    const params = scope === 'all' ? new HttpParams().set('scope', 'all') : undefined;
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http.get<DashboardSummary>(`${this.url}/summary`, { params, context });
  }
}
