import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, lastValueFrom, Observable, of } from 'rxjs';
import { catchError, distinctUntilChanged, finalize, map, shareReplay, tap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { Role, User } from '../models/user.model';

interface AuthState {
  accessToken: string | null;
  user: User | null;
}

const ANONYMOUS: AuthState = { accessToken: null, user: null };

/**
 * Oturum durumu (docs/07, docs/08).
 *
 * - Access token **yalnızca bellekte** tutulur; localStorage/sessionStorage'a hiçbir token yazılmaz
 *   (XSS ile çalınamasın). Sayfa yenilenince bellek sıfırlanır; oturum, httpOnly refresh cookie'si
 *   ile `initialize()` içinde sessizce geri alınır.
 * - Refresh tek seferliktir: aynı anda birden çok istek 401 alırsa sunucuya tek `/refresh` gider,
 *   hepsi aynı sonucu bekler.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authUrl = `${environment.apiUrl}/auth`;
  private readonly state = new BehaviorSubject<AuthState>(ANONYMOUS);
  private refreshInFlight: Observable<AuthResponse> | null = null;

  readonly currentUser$: Observable<User | null> = this.state.pipe(
    map((s) => s.user),
    distinctUntilChanged(),
  );
  readonly isAuthenticated$: Observable<boolean> = this.currentUser$.pipe(map((user) => user !== null));

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  /** Interceptor'ın Authorization header'ı için (senkron okuma). */
  get accessToken(): string | null {
    return this.state.value.accessToken;
  }

  get currentUser(): User | null {
    return this.state.value.user;
  }

  get isAuthenticated(): boolean {
    return this.state.value.user !== null;
  }

  hasRole(role: Role): boolean {
    return this.state.value.user?.role === role;
  }

  /**
   * Uygulama açılışında (APP_INITIALIZER) sessiz yenileme. Cookie yoksa/geçersizse kullanıcı
   * anonim kalır; hata yutulur ki uygulama yine açılsın.
   */
  initialize(): Promise<void> {
    return lastValueFrom(
      this.refresh().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${this.authUrl}/login`, request).pipe(
      tap((response) => this.setSession(response)),
      map((response) => response.user),
    );
  }

  /** Kayıt otomatik giriş yapmaz (backend 201 + kullanıcı özeti döner). */
  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.authUrl}/register`, request);
  }

  /**
   * Cookie ile yeni access token alır (gövde yok; cookie aynı origin olduğu için tarayıcı ekler).
   * Eşzamanlı çağrılar aynı isteği paylaşır. Başarısızsa oturum temizlenir ve hata iletilir.
   */
  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>(`${this.authUrl}/refresh`, null).pipe(
        tap({
          next: (response) => this.setSession(response),
          error: () => this.clearSession(),
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  /**
   * Sunucuda refresh token iptal edilir ve cookie silinir. Sunucu hatası olsa bile yerel oturum
   * temizlenir ve giriş sayfasına gidilir (kullanıcı "çıkış yapamadım" durumunda kalmasın).
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/logout`, null).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      finalize(() => {
        this.clearSession();
        void this.router.navigate(['/login']);
      }),
    );
  }

  /** Oturum yenilenemedi: belleği temizle, kaldığı sayfaya dönebilsin diye returnUrl ile login'e git. */
  sessionExpired(): void {
    this.clearSession();
    const currentUrl = this.router.url;
    if (!currentUrl.startsWith('/login')) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
    }
  }

  /** Profil güncellemesinden sonra navbar'daki ad vb. güncellensin. */
  updateCurrentUser(user: User): void {
    if (this.state.value.user) {
      this.state.next({ ...this.state.value, user });
    }
  }

  clearSession(): void {
    this.state.next(ANONYMOUS);
  }

  private setSession(response: AuthResponse): void {
    this.state.next({ accessToken: response.accessToken, user: response.user });
  }
}
