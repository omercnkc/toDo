import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { Invitation } from '../models/project.model';
import { InvitationService } from './invitation.service';

function invitation(id: number): Invitation {
  return {
    id, status: 'PENDING', createdAt: '2026-11-14T10:00:00Z', respondedAt: null, project: { id: 1, name: 'P' },
    inviter: { id: 1, fullName: 'A', email: 'a@x.com' }, invitee: { id: 2, fullName: 'B', email: 'b@x.com' },
  };
}

describe('InvitationService', () => {
  let service: InvitationService;
  let http: HttpTestingController;
  let count = -1;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(InvitationService);
    http = TestBed.inject(HttpTestingController);
    service.pendingCount$.subscribe((c) => (count = c));
  });

  afterEach(() => http.verify());

  it('bekleyen liste sayıyı günceller; kabul ve red sayıyı azaltır', () => {
    service.mine().subscribe();
    http.expectOne('/api/invitations?status=PENDING').flush([invitation(1), invitation(2)]);
    expect(count).toBe(2);

    service.accept(1).subscribe();
    const accept = http.expectOne('/api/invitations/1/accept');
    expect(accept.request.method).toBe('POST');
    accept.flush(invitation(1));
    expect(count).toBe(1);

    service.reject(2).subscribe();
    http.expectOne('/api/invitations/2/reject').flush(invitation(2));
    expect(count).toBe(0);
  });

  it('geçmiş davetler sayıyı değiştirmez', () => {
    service.mine('ACCEPTED').subscribe();
    http.expectOne('/api/invitations?status=ACCEPTED').flush([invitation(1)]);
    expect(count).toBe(0);
  });

  it('rozet tazelemesi sessizdir; hata olursa son değer kalır', () => {
    service.refreshPendingCount().subscribe();
    const req = http.expectOne('/api/invitations?status=PENDING');
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBeTrue();
    req.flush([invitation(1), invitation(2), invitation(3)]);
    expect(count).toBe(3);

    let emitted = -1;
    service.refreshPendingCount().subscribe((c) => (emitted = c));
    http.expectOne('/api/invitations?status=PENDING').flush({}, { status: 500, statusText: 'Hata' });
    expect(emitted).toBe(3);
    expect(count).toBe(3);

    service.reset();
    expect(count).toBe(0);
  });
});
