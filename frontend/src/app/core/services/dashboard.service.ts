import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { DashboardScope, DashboardSummary } from '../models/dashboard.model';

/** `/api/dashboard/**` (docs/06 "dashboard-service"). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly url = `${environment.apiUrl}/dashboard`;
  /**
   * Kullanıcının kendi değişikliğinden sonra hâlâ önbellekteki (en fazla 30 sn eski) özeti alabilecek kapsamlar.
   * Bunlar için bir sonraki istek `fresh=true` gönderir: kullanıcı oluşturduğu projeyi panelde hemen görür.
   * Başkalarının değişiklikleri yine önbellek süresi kadar gecikmeli yansır (6-C kararı).
   */
  private readonly staleScopes = new Set<DashboardScope>();

  constructor(private readonly http: HttpClient) {}

  /**
   * Özet. Hatalar genel bildirimle değil sayfada gösterilir (503: "servise ulaşılamıyor, tekrar dene").
   * @param scope `all` yalnızca ADMIN (aksi 403)
   */
  summary(scope: DashboardScope = 'mine'): Observable<DashboardSummary> {
    let params = new HttpParams();
    if (scope === 'all') {
      params = params.set('scope', 'all');
    }
    if (this.staleScopes.has(scope)) {
      params = params.set('fresh', 'true');
    }
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http
      .get<DashboardSummary>(`${this.url}/summary`, { params, context })
      .pipe(tap(() => this.staleScopes.delete(scope)));
  }

  /** Kullanıcı veri değiştirdi (ChangeTrackingInterceptor çağırır). */
  markStale(): void {
    this.staleScopes.add('mine');
    this.staleScopes.add('all');
  }
}
