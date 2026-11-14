import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Page } from '../../../core/models/page.model';
import { UserDetail } from '../../../core/models/user.model';
import { ToastService } from '../../../core/services/toast.service';
import { UserService } from '../../../core/services/user.service';
import { SharedModule } from '../../../shared/shared.module';
import { PAGE_SIZE, toQuery, UserListComponent } from './user-list.component';

function user(id: number, extra: Partial<UserDetail> = {}): UserDetail {
  return {
    id, email: `u${id}@ornek.com`, fullName: `Kullanıcı ${id}`, role: 'USER', enabled: true,
    createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z', ...extra,
  };
}

function page(content: UserDetail[], totalElements = content.length, pageIndex = 0): Page<UserDetail> {
  return { content, page: pageIndex, size: PAGE_SIZE, totalElements, totalPages: Math.ceil(totalElements / PAGE_SIZE) };
}

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let component: UserListComponent;
  let users: jasmine.SpyObj<UserService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: Router;
  let queryParams$: BehaviorSubject<ParamMap>;

  const ADMIN = user(1, { role: 'ADMIN', fullName: 'Ben Admin' });

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    users = jasmine.createSpyObj<UserService>('UserService', ['list', 'adminUpdate']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    queryParams$ = new BehaviorSubject(convertToParamMap({}));
    users.list.and.returnValue(of(page([ADMIN, user(2), user(3, { enabled: false })])));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [UserListComponent],
      providers: [
        { provide: UserService, useValue: users },
        { provide: ToastService, useValue: toast },
        { provide: AuthService, useValue: { currentUser: { id: 1 } } },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams$.asObservable() } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(UserListComponent);
    component = fixture.componentInstance;
  });

  it('URL\'deki arama ve 1 tabanlı sayfayla listeler', () => {
    queryParams$.next(convertToParamMap({ q: 'ali', page: '3' }));
    fixture.detectChanges();

    expect(users.list).toHaveBeenCalledWith('ali', 2, PAGE_SIZE);
    expect(component.search.value).toBe('ali');
  });

  it('kullanıcıları rol ve durumla gösterir; kendi satırında işlem düğmesi yok', () => {
    const content = el().textContent ?? '';

    expect(content).toContain('3 kullanıcı');
    expect(content).toContain('Pasif');
    expect(content).toContain('Kendi hesabın');
    const buttons = Array.from(el().querySelectorAll('table button')).map((b) => b.getAttribute('aria-label'));
    expect(buttons.some((label) => label?.includes('Ben Admin'))).toBeFalse(); // kendi satırında düğme yok
    expect(buttons).toContain('Pasifleştir: Kullanıcı 2');
    expect(buttons).toContain('Aktifleştir: Kullanıcı 3');
  });

  it('arama 300 ms bekler ve URL\'yi ilk sayfayla günceller', fakeAsync(() => {
    fixture.detectChanges();
    component.search.setValue('a');
    component.search.setValue('ay');
    component.search.setValue(' ayşe ');
    tick(299);
    expect(router.navigate).not.toHaveBeenCalled();
    tick(1);

    expect(router.navigate).toHaveBeenCalledOnceWith([], jasmine.objectContaining({
      queryParams: { q: 'ayşe', page: null },
      replaceUrl: true,
    }));
  }));

  it('pasifleştirme onay ister, satırı günceller ve bildirim gösterir', () => {
    fixture.detectChanges();
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
    const target = component.result!.content[1];

    component.setEnabled(target, false);
    expect(users.adminUpdate).not.toHaveBeenCalled();

    confirmSpy.and.returnValue(true);
    users.adminUpdate.and.returnValue(of({ ...target, enabled: false }));
    component.setEnabled(target, false);

    expect(users.adminUpdate).toHaveBeenCalledWith(2, { enabled: false });
    expect(component.result!.content[1].enabled).toBeFalse();
    expect(toast.success).toHaveBeenCalledWith('Kullanıcı 2 pasifleştirildi.');
  });

  it('yöneticilikten çıkarma onaysız, yönetici yapma onaylı', () => {
    fixture.detectChanges();
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(true);
    users.adminUpdate.and.callFake((id, req) => of(user(id, { role: req.role })));

    component.changeRole(component.result!.content[1], 'ADMIN');
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    component.changeRole(component.result!.content[1], 'USER');
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(users.adminUpdate).toHaveBeenCalledWith(2, { role: 'USER' });
  });

  it('sunucu 409 dönerse mesajını bildirir', () => {
    fixture.detectChanges();
    users.adminUpdate.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { status: 409, error: 'SELF_MODIFICATION_NOT_ALLOWED', message: 'Kendi rolünüzü değiştiremezsiniz', path: '', timestamp: '' },
    })));

    component.changeRole(component.result!.content[1], 'USER');

    expect(toast.error).toHaveBeenCalledWith('Kendi rolünüzü değiştiremezsiniz');
    expect(component.pendingId).toBeNull();
  });

  it('liste yüklenemezse tekrar dene düğmesi çalışır', () => {
    users.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(el().textContent).toContain('Kullanıcılar yüklenemedi');

    users.list.and.returnValue(of(page([user(2)])));
    component.retry();
    expect(el().textContent).not.toContain('Kullanıcılar yüklenemedi');
    expect(el().textContent).toContain('1 kullanıcı');
  });

  it('sonuç yoksa aramaya özel boş durum gösterir', () => {
    users.list.and.returnValue(of(page([])));
    queryParams$.next(convertToParamMap({ q: 'xyz' }));

    expect(el().textContent).toContain('“xyz” ile eşleşen kullanıcı yok.');
  });

  it('çok sayfada sayfa düğmeleri ve gezinme', () => {
    users.list.and.returnValue(of(page([user(2)], 200, 4)));
    queryParams$.next(convertToParamMap({ page: '5' }));
    fixture.detectChanges();

    // 10 sayfa, geçerli 5. (index 4): ortak sayfalama bileşeni çizilir, tıklama URL'ye yazılır
    const pageSix = Array.from(el().querySelectorAll<HTMLButtonElement>('app-pagination button'))
      .find((b) => b.textContent?.trim() === '6')!;
    pageSix.click();
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { q: null, page: 6 } }));
  });

  it('toQuery geçersiz sayfayı ilk sayfa sayar', () => {
    expect(toQuery(convertToParamMap({ page: 'abc' }))).toEqual({ q: '', page: 0 });
    expect(toQuery(convertToParamMap({ page: '-2', q: '  ali ' }))).toEqual({ q: 'ali', page: 0 });
    expect(toQuery(convertToParamMap({ page: '2.5' }))).toEqual({ q: '', page: 0 });
  });
});
