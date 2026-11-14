import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of, Subject, throwError } from 'rxjs';

import { UserSummary } from '../../../core/models/user.model';
import { UserService } from '../../../core/services/user.service';
import { SharedModule } from '../../../shared/shared.module';
import { InvitePanelComponent } from './invite-panel.component';

const users: UserSummary[] = [
  { id: 1, email: 'ben@ornek.com', fullName: 'Ben' },
  { id: 2, email: 'uye@ornek.com', fullName: 'Üye Kişi' },
  { id: 3, email: 'bekleyen@ornek.com', fullName: 'Bekleyen Kişi' },
  { id: 4, email: 'yeni@ornek.com', fullName: 'Yeni Kişi' },
];

describe('InvitePanelComponent', () => {
  let fixture: ComponentFixture<InvitePanelComponent>;
  let component: InvitePanelComponent;
  let userService: jasmine.SpyObj<UserService>;

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    userService = jasmine.createSpyObj<UserService>('UserService', ['search']);
    userService.search.and.returnValue(of(users));
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, SharedModule],
      declarations: [InvitePanelComponent],
      providers: [{ provide: UserService, useValue: userService }],
    }).compileComponents();
    fixture = TestBed.createComponent(InvitePanelComponent);
    component = fixture.componentInstance;
    component.currentUserId = 1;
    component.memberIds = [1, 2];
    component.pendingInviteeIds = [3];
    fixture.detectChanges();
  });

  it('3 karakterden kısa aramada istek atmaz', fakeAsync(() => {
    component.search.setValue('ab');
    tick(300);

    expect(userService.search).not.toHaveBeenCalled();
    expect(el().textContent).toContain('en az 3 karakter yaz');
  }));

  it('300 ms bekler, yalnızca son metni arar', fakeAsync(() => {
    component.search.setValue('kiş');
    tick(100);
    component.search.setValue('kişi');
    tick(300);

    expect(userService.search).toHaveBeenCalledOnceWith('kişi');
  }));

  it('sonuçlarda kendisi, üye ve bekleyen işaretlenir; yalnızca davet edilebilene düğme', fakeAsync(() => {
    component.search.setValue('kişi');
    tick(300);
    const rows = Array.from(el().querySelectorAll('li')).map((li) => li.textContent ?? '');

    expect(rows[0]).toContain('Sen');
    expect(rows[1]).toContain('Üye');
    expect(rows[2]).toContain('Davet bekliyor');
    expect(el().querySelectorAll('button').length).toBe(1);

    const emitted: UserSummary[] = [];
    component.invite.subscribe((u) => emitted.push(u));
    el().querySelector<HTMLButtonElement>('[aria-label="Davet et: Yeni Kişi"]')!.click();
    expect(emitted).toEqual([users[3]]);
  }));

  it('yeni arama eski isteği iptal eder; hata ve boş sonuç mesajları', fakeAsync(() => {
    const slow = new Subject<UserSummary[]>();
    userService.search.and.returnValues(slow, of([]));
    component.search.setValue('ilk arama');
    tick(300);
    component.search.setValue('ikinci');
    tick(300);
    slow.next(users); // geç gelen eski yanıt yok sayılır

    expect(el().textContent).toContain('Eşleşen kullanıcı yok.');

    userService.search.and.returnValue(throwError(() => new Error('ağ')));
    component.search.setValue('üçüncü');
    tick(300);
    expect(el().textContent).toContain('Arama yapılamadı');
  }));
});
