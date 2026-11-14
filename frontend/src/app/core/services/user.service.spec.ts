import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UserSummary } from '../models/user.model';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('davet araması kırpılmış metinle ilk sayfayı ister, pasifleri ayıklar', () => {
    let result: UserSummary[] = [];
    service.search('  ayşe ').subscribe((users) => (result = users));

    const req = http.expectOne((r) => r.url === '/api/users');
    expect(req.request.params.get('q')).toBe('ayşe');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({
      content: [
        { id: 1, email: 'a@x.com', fullName: 'Aktif' },
        { id: 2, email: 'p@x.com', fullName: 'Pasif', enabled: false },
        { id: 3, email: 'b@x.com', fullName: 'Admin görür', enabled: true },
      ],
      page: 0, size: 8, totalElements: 3, totalPages: 1,
    });

    expect(result.map((u) => u.id)).toEqual([1, 3]);
  });
});
