import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DashboardService } from '../services/dashboard.service';
import { ChangeTrackingInterceptor } from './change-tracking.interceptor';

describe('ChangeTrackingInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let dashboard: jasmine.SpyObj<DashboardService>;

  beforeEach(() => {
    dashboard = jasmine.createSpyObj<DashboardService>('DashboardService', ['markStale']);
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: DashboardService, useValue: dashboard },
        { provide: HTTP_INTERCEPTORS, useClass: ChangeTrackingInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('başarılı değiştirici API isteği özeti bayat işaretler', () => {
    http.post('/api/projects', {}).subscribe();
    backend.expectOne('/api/projects').flush({ id: 1 });
    http.patch('/api/tasks/1/move', {}).subscribe();
    backend.expectOne('/api/tasks/1/move').flush({});
    http.delete('/api/tasks/1').subscribe();
    backend.expectOne('/api/tasks/1').flush(null, { status: 204, statusText: 'No Content' });

    expect(dashboard.markStale).toHaveBeenCalledTimes(3);
  });

  it('okuma, kimlik uçları ve başarısız istekler işaretlemez', () => {
    http.get('/api/projects').subscribe();
    backend.expectOne('/api/projects').flush([]);
    http.post('/api/auth/login', {}).subscribe();
    backend.expectOne('/api/auth/login').flush({});
    http.post('/api/tasks', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/tasks').flush({}, { status: 400, statusText: 'Bad Request' });

    expect(dashboard.markStale).not.toHaveBeenCalled();
  });
});
