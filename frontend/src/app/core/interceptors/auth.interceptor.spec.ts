import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { AuthResponse } from '../models/auth.model';
import { AuthInterceptor } from './auth.interceptor';

/** AuthService'in interceptor'ın kullandığı kısmının sahte hâli. */
class FakeAuthService {
  accessToken: string | null = 'eski-token';
  refreshCalls = 0;
  sessionExpiredCalls = 0;
  /** Testin kontrol ettiği refresh sonucu. */
  refreshResult: Observable<AuthResponse> = of({} as AuthResponse);

  refresh(): Observable<AuthResponse> {
    this.refreshCalls++;
    return this.refreshResult;
  }

  sessionExpired(): void {
    this.sessionExpiredCalls++;
    this.accessToken = null;
  }
}

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: FakeAuthService;

  beforeEach(() => {
    auth = new FakeAuthService();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('API isteğine Bearer ekler', () => {
    http.get('/api/projects').subscribe();

    expect(backend.expectOne('/api/projects').request.headers.get('Authorization')).toBe('Bearer eski-token');
  });

  it('token yoksa header eklemez', () => {
    auth.accessToken = null;
    http.get('/api/projects').subscribe();

    expect(backend.expectOne('/api/projects').request.headers.has('Authorization')).toBeFalse();
  });

  it('token başka adreslere gönderilmez', () => {
    http.get('https://baska-site.example/api/x').subscribe();
    http.get('/assets/config.json').subscribe();

    expect(backend.expectOne('https://baska-site.example/api/x').request.headers.has('Authorization')).toBeFalse();
    expect(backend.expectOne('/assets/config.json').request.headers.has('Authorization')).toBeFalse();
  });

  it('kimlik uçlarına Bearer eklemez ve 401\'de yenileme denemez', () => {
    let status = 0;
    http.post('/api/auth/login', {}).subscribe({ error: (e) => (status = e.status) });
    const req = backend.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.refreshCalls).toBe(0);
  });

  it('401 alınca yeniler ve isteği yeni token ile bir kez tekrar dener', () => {
    auth.refreshResult = new Observable((subscriber) => {
      auth.accessToken = 'yeni-token';
      subscriber.next({} as AuthResponse);
      subscriber.complete();
    });
    let body: unknown;
    http.get('/api/projects').subscribe((b) => (body = b));

    backend.expectOne('/api/projects').flush({}, { status: 401, statusText: 'Unauthorized' });
    const retry = backend.expectOne('/api/projects');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer yeni-token');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
    expect(auth.refreshCalls).toBe(1);
    expect(auth.sessionExpiredCalls).toBe(0);
  });

  it('eşzamanlı 401\'ler aynı yenilemeyi bekler', () => {
    // Gerçek AuthService tek isteği paylaşır; burada interceptor'ın her biri için refresh() çağırıp
    // sonucunu beklediğini ve hepsini tekrar denediğini doğrularız.
    const refresh$ = new Subject<AuthResponse>();
    auth.refreshResult = refresh$;
    const results: string[] = [];
    http.get('/api/a').subscribe(() => results.push('a'));
    http.get('/api/b').subscribe(() => results.push('b'));

    backend.expectOne('/api/a').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/b').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectNone('/api/a'); // yenileme bitmeden tekrar denenmez
    auth.accessToken = 'yeni-token';
    refresh$.next({} as AuthResponse);
    refresh$.complete();

    backend.expectOne('/api/a').flush({});
    backend.expectOne('/api/b').flush({});
    expect(results.sort()).toEqual(['a', 'b']);
  });

  it('yenileme başarısızsa oturumu bitirir ve orijinal 401\'i iletir', () => {
    auth.refreshResult = throwError(() => new Error('refresh 401'));
    let status = 0;
    http.get('/api/projects').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/projects').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.sessionExpiredCalls).toBe(1);
    backend.expectNone('/api/projects');
  });

  it('yeni token ile de 401 gelirse döngüye girmez, oturumu bitirir', () => {
    let status = 0;
    http.get('/api/projects').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/projects').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/projects').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.refreshCalls).toBe(1);
    expect(auth.sessionExpiredCalls).toBe(1);
  });

  it('401 dışındaki hatalara dokunmaz', () => {
    let status = 0;
    http.get('/api/projects').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/projects').flush({}, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    expect(auth.refreshCalls).toBe(0);
  });
});
