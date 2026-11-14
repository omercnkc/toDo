import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ProjectService } from './project.service';

describe('ProjectService', () => {
  let service: ProjectService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ProjectService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste: boş arama ve durum gönderilmez', () => {
    service.list({ q: '  ', status: null, page: 0, size: 12 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/projects');
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
  });

  it('liste: arama kırpılır, durum eklenir', () => {
    service.list({ q: ' web ', status: 'ARCHIVED', page: 2, size: 12 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/projects');
    expect(req.request.params.get('q')).toBe('web');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ content: [], page: 2, size: 12, totalElements: 0, totalPages: 0 });
  });

  it('arşivden çıkarma ad ve açıklamayı da gönderir (PUT tam güncelleme)', () => {
    service.restore({ id: 7, name: 'Web', description: 'Açıklama' }).subscribe();

    const req = http.expectOne('/api/projects/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Web', description: 'Açıklama', status: 'ACTIVE' });
    req.flush({});
  });

  it('arşivleme DELETE ile', () => {
    service.archive(7).subscribe();

    const req = http.expectOne('/api/projects/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
