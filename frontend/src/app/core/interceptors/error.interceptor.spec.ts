import { HTTP_INTERCEPTORS, HttpClient, HttpContext } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ToastService } from '../services/toast.service';
import { ErrorInterceptor } from './error.interceptor';
import { SKIP_ERROR_TOAST } from './http-context.tokens';

describe('ErrorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['error']);
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: ToastService, useValue: toast },
        { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function fail(url: string, status: number, body: object = {}, context?: HttpContext): number {
    let received = -1;
    http.get(url, { context }).subscribe({ error: (e) => (received = e.status) });
    backend.expectOne(url).flush(body, { status, statusText: 'x' });
    return received;
  }

  it('sunucu hatasında genel bildirim gösterir ve hatayı iletir', () => {
    expect(fail('/api/projects', 500)).toBe(500);
    expect(toast.error).toHaveBeenCalledWith('Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.');
  });

  it('ağ hatasında (status 0) bağlantı mesajı gösterir', () => {
    http.get('/api/projects').subscribe({ error: () => undefined });
    backend.expectOne('/api/projects').error(new ProgressEvent('error'));

    expect(toast.error).toHaveBeenCalledWith(jasmine.stringContaining('Sunucuya ulaşılamıyor'));
  });

  it('403\'te sunucunun mesajını gösterir', () => {
    fail('/api/users/5', 403, { status: 403, error: 'FORBIDDEN', message: 'Bu işlem için yetkiniz yok' });

    expect(toast.error).toHaveBeenCalledWith('Bu işlem için yetkiniz yok');
  });

  it('form hatalarını (400, 404, 409) ve 401\'i sayfaya bırakır', () => {
    fail('/api/projects', 400);
    fail('/api/projects/9', 404);
    fail('/api/auth/register', 409);
    fail('/api/projects', 401);

    expect(toast.error).not.toHaveBeenCalled();
  });

  it('kimlik uçlarındaki 403\'ü (pasif hesap) login sayfasına bırakır', () => {
    fail('/api/auth/login', 403, { error: 'ACCOUNT_DISABLED', message: 'Hesabınız pasif durumda' });

    expect(toast.error).not.toHaveBeenCalled();
  });

  it('SKIP_ERROR_TOAST ile bildirim kapatılabilir', () => {
    fail('/api/projects', 500, {}, new HttpContext().set(SKIP_ERROR_TOAST, true));

    expect(toast.error).not.toHaveBeenCalled();
  });
});
