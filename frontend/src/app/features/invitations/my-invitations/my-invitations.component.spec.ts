import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { Invitation } from '../../../core/models/project.model';
import { InvitationService } from '../../../core/services/invitation.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { MyInvitationsComponent } from './my-invitations.component';

function invitation(id: number, projectName: string): Invitation {
  return {
    id, status: 'PENDING', createdAt: '2026-11-14T10:00:00Z', respondedAt: null,
    project: { id: id * 10, name: projectName },
    inviter: { id: 1, fullName: 'Ayşe Sahip', email: 'ayse@ornek.com' },
    invitee: { id: 2, fullName: 'Can', email: 'can@ornek.com' },
  };
}

describe('MyInvitationsComponent', () => {
  let fixture: ComponentFixture<MyInvitationsComponent>;
  let component: MyInvitationsComponent;
  let invitations: jasmine.SpyObj<InvitationService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: Router;

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function byLabel(label: string): HTMLButtonElement {
    return el().querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)!;
  }

  beforeEach(async () => {
    invitations = jasmine.createSpyObj<InvitationService>('InvitationService', ['mine', 'accept', 'reject']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    invitations.mine.and.returnValue(of([invitation(1, 'Web Sitesi'), invitation(2, 'Mobil')]));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [MyInvitationsComponent],
      providers: [
        { provide: InvitationService, useValue: invitations },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(MyInvitationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('bekleyen davetleri proje ve davet edenle listeler', () => {
    const content = el().textContent ?? '';
    expect(content).toContain('Web Sitesi');
    expect(content).toContain('Mobil');
    expect(content).toContain('Ayşe Sahip seni davet etti');
  });

  it('kabul edince listeden kalkar, "Projeye git" ile projeye gider', () => {
    invitations.accept.and.returnValue(of(invitation(1, 'Web Sitesi')));

    byLabel('Kabul et: Web Sitesi').click();

    expect(invitations.accept).toHaveBeenCalledWith(1);
    expect(toast.success).toHaveBeenCalledWith('"Web Sitesi" projesine katıldın.');
    expect(component.invitations.map((i) => i.id)).toEqual([2]);
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Projeye git'))!.click();
    expect(router.navigate).toHaveBeenCalledWith(['/projects', 10]);
  });

  it('red onay ister (24 saat uyarısı), sonra listeden kalkar', () => {
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
    byLabel('Reddet: Mobil').click();
    expect(invitations.reject).not.toHaveBeenCalled();
    expect(confirmSpy.calls.mostRecent().args[0]).toContain('24 saat');

    confirmSpy.and.returnValue(true);
    invitations.reject.and.returnValue(of(invitation(2, 'Mobil')));
    byLabel('Reddet: Mobil').click();

    expect(invitations.reject).toHaveBeenCalledWith(2);
    expect(component.invitations.map((i) => i.id)).toEqual([1]);
  });

  it('davet bu arada geri çekildiyse (409) mesaj gösterip listeyi tazeler', () => {
    invitations.accept.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { status: 409, error: 'INVITATION_NOT_PENDING', message: 'Bu davet zaten yanıtlanmış veya iptal edilmiş', path: '', timestamp: '' },
    })));
    invitations.mine.and.returnValue(of([invitation(2, 'Mobil')]));

    byLabel('Kabul et: Web Sitesi').click();

    expect(toast.error).toHaveBeenCalledWith('Bu davet zaten yanıtlanmış veya iptal edilmiş');
    expect(component.invitations.map((i) => i.id)).toEqual([2]);
    expect(component.respondingId).toBeNull();
  });

  it('davet yoksa boş durum, yüklenemezse tekrar dene', () => {
    invitations.mine.and.returnValue(of([]));
    component.load();
    expect(el().textContent).toContain('Bekleyen davetin yok');

    invitations.mine.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.load();
    expect(el().textContent).toContain('Davetler yüklenemedi');
  });
});
