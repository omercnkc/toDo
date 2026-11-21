import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('varsayılan kapsam parametresiz; hata bildirimi sayfaya bırakılır', () => {
    service.summary().subscribe();
    const req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.keys()).toEqual([]);
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBeTrue();
    req.flush({});
  });

  it('tüm sistem: scope=all', () => {
    service.summary('all').subscribe();
    const req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.get('scope')).toBe('all');
    req.flush({});
  });

  it('değişiklikten sonra her kapsam bir kez fresh=true ister; başarısızsa tekrar ister', () => {
    service.markStale();

    service.summary().subscribe({ error: () => undefined });
    let req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.get('fresh')).toBe('true');
    req.flush({}, { status: 503, statusText: 'Service Unavailable' });

    service.summary().subscribe();
    req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.get('fresh')).toBe('true');
    req.flush({});

    service.summary().subscribe();
    req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.has('fresh')).toBeFalse();
    req.flush({});

    service.summary('all').subscribe();
    req = http.expectOne((r) => r.url === '/api/dashboard/summary');
    expect(req.request.params.get('fresh')).toBe('true');
    req.flush({});
  });
});
