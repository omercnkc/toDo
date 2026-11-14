import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
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

  it('detay sessiz istenirse genel hata bildirimi kapatılır', () => {
    service.get(7, true).subscribe();
    const req = http.expectOne('/api/projects/7');
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBeTrue();
    req.flush({});
  });

  it('üye ve davet uçları', () => {
    service.members(7).subscribe();
    http.expectOne('/api/projects/7/members').flush([]);

    service.removeMember(7, 3).subscribe();
    expect(http.expectOne('/api/projects/7/members/3').request.method).toBe('DELETE');

    service.invitations(7, 'PENDING').subscribe();
    http.expectOne('/api/projects/7/invitations?status=PENDING').flush([]);

    service.invite(7, 9).subscribe();
    const invite = http.expectOne('/api/projects/7/invitations');
    expect(invite.request.method).toBe('POST');
    expect(invite.request.body).toEqual({ userId: 9 });

    service.cancelInvitation(7, 50).subscribe();
    expect(http.expectOne('/api/projects/7/invitations/50').request.method).toBe('DELETE');
  });
});
