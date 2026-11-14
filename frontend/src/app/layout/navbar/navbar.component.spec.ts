import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { InvitationService } from '../../core/services/invitation.service';
import { User } from '../../core/models/user.model';
import { initialsOf } from '../../shared/components/avatar/avatar.component';
import { SharedModule } from '../../shared/shared.module';
import { NavbarComponent } from './navbar.component';

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let user$: BehaviorSubject<User | null>;
  let auth: { currentUser$: BehaviorSubject<User | null>; logout: jasmine.Spy };
  let pendingCount$: BehaviorSubject<number>;
  let invitations: { pendingCount$: BehaviorSubject<number>; refreshPendingCount: jasmine.Spy; reset: jasmine.Spy };

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    user$ = new BehaviorSubject<User | null>({ id: 1, email: 'ali@ornek.com', fullName: 'Ali Veli', role: 'USER' });
    auth = { currentUser$: user$, logout: jasmine.createSpy('logout').and.returnValue(of(undefined)) };
    pendingCount$ = new BehaviorSubject(0);
    invitations = {
      pendingCount$,
      refreshPendingCount: jasmine.createSpy('refreshPendingCount').and.returnValue(of(0)),
      reset: jasmine.createSpy('reset'),
    };
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([{ path: '**', children: [] }]), SharedModule],
      declarations: [NavbarComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: InvitationService, useValue: invitations },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NavbarComponent);
  });

  it('kullanıcının adını ve baş harflerini gösterir', () => {
    const content = el().textContent ?? '';
    expect(content).toContain('Ali Veli');
    expect(content).toContain('AV');
    expect(content).toContain('ali@ornek.com');
  });

  it('Kullanıcılar bağlantısı yalnızca ADMIN\'e görünür', () => {
    expect(el().querySelector('a[href="/admin/users"]')).toBeNull();

    user$.next({ id: 1, email: 'a@b.com', fullName: 'Admin', role: 'ADMIN' });

    expect(el().querySelector('a[href="/admin/users"]')).not.toBeNull();
    expect(el().textContent).toContain('Yönetici');
  });

  it('çıkış bir kez çağrılır', () => {
    const button = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Çıkış yap'))!;
    button.click();
    button.click();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(el().textContent).toContain('Çıkış yapılıyor');
  });

  it('mobil menü açılır ve sayfa değişince kapanır', async () => {
    const toggler = el().querySelector<HTMLButtonElement>('.navbar-toggler')!;
    toggler.click();
    expect(el().querySelector('#main-nav')?.classList).toContain('show');
    expect(toggler.getAttribute('aria-expanded')).toBe('true');

    await fixture.ngZone!.run(() => TestBed.inject(Router).navigateByUrl('/profile'));

    expect(el().querySelector('#main-nav')?.classList).not.toContain('show');
  });

  it('initialsOf: ilk ve son kelime, Türkçe büyük harf', () => {
    expect(initialsOf('ayşe nur yılmaz')).toBe('AY');
    expect(initialsOf('  ilker  ')).toBe('İ');
    expect(initialsOf('')).toBe('?');
  });

  it('bekleyen davet rozeti: 0 iken gizli, varsa sayı', () => {
    expect(el().querySelector('[data-testid="invitation-badge"]')).toBeNull();

    pendingCount$.next(3);

    expect(el().querySelector('[data-testid="invitation-badge"]')?.textContent?.trim()).toBe('3');
    expect(el().textContent).toContain('bekleyen davet');
  });

  it('rozet açılışta ve sayfa değişiminde tazelenir; kapanınca sıfırlanır', async () => {
    el();
    expect(invitations.refreshPendingCount).toHaveBeenCalledTimes(1);

    await fixture.ngZone!.run(() => TestBed.inject(Router).navigateByUrl('/projects'));
    expect(invitations.refreshPendingCount).toHaveBeenCalledTimes(2);

    fixture.destroy();
    expect(invitations.reset).toHaveBeenCalled();
  });
});
