import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { Invitation, InvitationStatus } from '../models/project.model';

/**
 * Bana gelen davetler (`/api/invitations`, docs/06) ve navbar rozeti için bekleyen davet sayısı.
 *
 * Sayı tek yerde tutulur ({@link pendingCount$}): Davetlerim sayfası yanıt verince, navbar sayfa değişiminde
 * ve periyodik olarak {@link refreshPendingCount} çağırır. Bildirim altyapısı kapsam dışı (docs/01), bu yüzden
 * anlık değil en fazla ~60 sn gecikmeli.
 */
@Injectable({ providedIn: 'root' })
export class InvitationService {
  private readonly invitationsUrl = `${environment.apiUrl}/invitations`;
  private readonly pendingCountSubject = new BehaviorSubject<number>(0);

  readonly pendingCount$: Observable<number> = this.pendingCountSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /** Yeni önce. Varsayılan: bekleyenler. */
  mine(status: InvitationStatus = 'PENDING'): Observable<Invitation[]> {
    const params = new HttpParams().set('status', status);
    return this.http.get<Invitation[]>(this.invitationsUrl, { params }).pipe(
      tap((list) => {
        if (status === 'PENDING') {
          this.pendingCountSubject.next(list.length);
        }
      }),
    );
  }

  /** Kabul: projeye MEMBER olarak eklenir. */
  accept(id: number): Observable<Invitation> {
    return this.http.post<Invitation>(`${this.invitationsUrl}/${id}/accept`, null).pipe(tap(() => this.decrement()));
  }

  /** Red: üyelik oluşmaz; aynı projeden 24 saat yeni davet gelmez. */
  reject(id: number): Observable<Invitation> {
    return this.http.post<Invitation>(`${this.invitationsUrl}/${id}/reject`, null).pipe(tap(() => this.decrement()));
  }

  /**
   * Rozet için arka planda sayıyı tazeler. Hata sessizce yutulur (genel bildirim de gösterilmez):
   * rozet güncellenemedi diye kullanıcı rahatsız edilmez, son bilinen değer kalır.
   */
  refreshPendingCount(): Observable<number> {
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    const params = new HttpParams().set('status', 'PENDING');
    return this.http.get<Invitation[]>(this.invitationsUrl, { params, context }).pipe(
      map((list) => list.length),
      tap((count) => this.pendingCountSubject.next(count)),
      catchError(() => of(this.pendingCountSubject.value)),
    );
  }

  /** Çıkışta rozet sıfırlanır (bir sonraki kullanıcı eski sayıyı görmesin). */
  reset(): void {
    this.pendingCountSubject.next(0);
  }

  private decrement(): void {
    this.pendingCountSubject.next(Math.max(0, this.pendingCountSubject.value - 1));
  }
}
