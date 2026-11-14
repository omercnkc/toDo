import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { Role, UserDetail, UserSummary } from '../models/user.model';

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** ADMIN güncellemesi: gönderilmeyen alan değişmez, en az biri dolu olmalı. */
export interface AdminUpdateUserRequest {
  role?: Role;
  enabled?: boolean;
}

/** `/api/users/**` uçları (docs/06). Yetki kontrolü backend'de; arayüz yalnızca gösterir/gizler. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly usersUrl = `${environment.apiUrl}/users`;

  constructor(private readonly http: HttpClient) {}

  getMe(): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${this.usersUrl}/me`);
  }

  /** Yalnızca ad değişir; e-posta ve rol buradan değişmez. */
  updateMe(fullName: string): Observable<UserDetail> {
    return this.http.put<UserDetail>(`${this.usersUrl}/me`, { fullName });
  }

  /** 204. Sunucu tüm oturumları (refresh token'ları) kapatır; istemci yeniden giriş istemeli. */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.usersUrl}/me/password`, request);
  }

  /** ADMIN listesi: `q` boşsa tüm kullanıcılar (pasifler dahil), ada/e-postaya göre arama. */
  list(q: string, page: number, size: number): Observable<Page<UserDetail>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Page<UserDetail>>(this.usersUrl, { params });
  }

  adminUpdate(id: number, request: AdminUpdateUserRequest): Observable<UserDetail> {
    return this.http.put<UserDetail>(`${this.usersUrl}/${id}`, request);
  }

  /**
   * Davet için kullanıcı arama (ad veya e-posta). USER için en az 3 karakter gerekir (backend kuralı);
   * pasif kullanıcılar sonuçtan çıkarılır (ADMIN yanıtında da gelebildikleri için).
   */
  search(q: string, size = 8): Observable<UserSummary[]> {
    const params = new HttpParams().set('q', q.trim()).set('page', 0).set('size', size);
    return this.http
      .get<Page<UserSummary>>(this.usersUrl, { params })
      .pipe(map((page) => page.content.filter((user) => user.enabled !== false)));
  }
}
