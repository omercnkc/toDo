import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { AuthResponse } from '../models/auth.model';
import { AuthService } from './auth.service';

const RESPONSE: AuthResponse = {
  accessToken: 'access-1',
  expiresIn: 900,
  user: { id: 7, email: 'ali@ornek.com', fullName: 'Ali Veli', role: 'USER' },
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => http.verify());

  it('başlangıçta anonimdir', () => {
    expect(service.isAuthenticated).toBeFalse();
    expect(service.accessToken).toBeNull();
  });

  it('giriş token ve kullanıcıyı belleğe alır, tarayıcı deposuna hiçbir şey yazmaz', () => {
    const localSet = spyOn(localStorage, 'setItem');
    const sessionSet = spyOn(sessionStorage, 'setItem');
    let emitted: unknown;

    service.login({ email: 'ali@ornek.com', password: 'x' }).subscribe((user) => (emitted = user));
    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'ali@ornek.com', password: 'x' });
    req.flush(RESPONSE);

    expect(emitted).toEqual(RESPONSE.user);
    expect(service.accessToken).toBe('access-1');
    expect(service.currentUser).toEqual(RESPONSE.user);
    expect(service.hasRole('USER')).toBeTrue();
    expect(service.hasRole('ADMIN')).toBeFalse();
    expect(localSet).not.toHaveBeenCalled();
    expect(sessionSet).not.toHaveBeenCalled();
  });

  it('başarısız giriş oturum açmaz', () => {
    service.login({ email: 'a@b.com', password: 'yanlis' }).subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush(
      { status: 401, error: 'INVALID_CREDENTIALS', message: 'E-posta veya şifre hatalı' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(service.isAuthenticated).toBeFalse();
  });

  it('kayıt otomatik giriş yapmaz', () => {
    service.register({ email: 'a@b.com', password: 'Gizli.Sifre1', fullName: 'A' }).subscribe();
    http.expectOne('/api/auth/register').flush(RESPONSE.user, { status: 201, statusText: 'Created' });

    expect(service.isAuthenticated).toBeFalse();
  });

  it('eşzamanlı refresh çağrıları tek isteği paylaşır', () => {
    const results: string[] = [];
    service.refresh().subscribe((r) => results.push(r.accessToken));
    service.refresh().subscribe((r) => results.push(r.accessToken));
    service.refresh().subscribe((r) => results.push(r.accessToken));

    const req = http.expectOne('/api/auth/refresh'); // birden fazla olsaydı hata verirdi
    expect(req.request.body).toBeNull();
    req.flush(RESPONSE);

    expect(results).toEqual(['access-1', 'access-1', 'access-1']);
    expect(service.accessToken).toBe('access-1');
  });

  it('refresh bittikten sonraki çağrı yeni istek açar', () => {
    service.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(RESPONSE);

    service.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush({ ...RESPONSE, accessToken: 'access-2' });

    expect(service.accessToken).toBe('access-2');
  });

  it('başarısız refresh oturumu temizler ve hatayı iletir', () => {
    service.login({ email: 'a@b.com', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(RESPONSE);
    let failed = false;

    service.refresh().subscribe({ error: () => (failed = true) });
    http.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBeTrue();
    expect(service.isAuthenticated).toBeFalse();
  });

  it('initialize cookie geçersizse hata vermeden anonim kalır', async () => {
    const done = service.initialize();
    http.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    await expectAsync(done).toBeResolved();
    expect(service.isAuthenticated).toBeFalse();
  });

  it('initialize cookie geçerliyse oturumu geri alır', async () => {
    const done = service.initialize();
    http.expectOne('/api/auth/refresh').flush(RESPONSE);

    await done;
    expect(service.currentUser).toEqual(RESPONSE.user);
  });

  it('çıkış sunucu hata verse de oturumu temizler ve login\'e gider', () => {
    service.login({ email: 'a@b.com', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(RESPONSE);

    service.logout().subscribe();
    http.expectOne('/api/auth/logout').flush({}, { status: 500, statusText: 'Server Error' });

    expect(service.isAuthenticated).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('oturum düşünce kaldığı sayfayı returnUrl olarak taşır', () => {
    spyOnProperty(router, 'url', 'get').and.returnValue('/projects/5');

    service.sessionExpired();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/projects/5' } });
  });

  it('zaten login sayfasındaysa tekrar yönlendirmez', () => {
    spyOnProperty(router, 'url', 'get').and.returnValue('/login?returnUrl=%2Fx');

    service.sessionExpired();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('currentUser$ değişiklikleri yayınlar', () => {
    const seen: (string | null)[] = [];
    service.currentUser$.subscribe((u) => seen.push(u?.fullName ?? null));

    service.login({ email: 'a@b.com', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(RESPONSE);
    service.updateCurrentUser({ ...RESPONSE.user, fullName: 'Yeni Ad' });
    service.clearSession();

    expect(seen).toEqual([null, 'Ali Veli', 'Yeni Ad', null]);
  });
});
