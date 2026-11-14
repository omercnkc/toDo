import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { SharedModule } from '../../shared/shared.module';
import { ProfileComponent } from './profile.component';

const PROFILE: UserDetail = {
  id: 7,
  email: 'ayse@ornek.com',
  fullName: 'Ayşe Yılmaz',
  role: 'USER',
  enabled: true,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
};

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let component: ProfileComponent;
  let users: jasmine.SpyObj<UserService>;
  let auth: jasmine.SpyObj<AuthService>;
  let toast: jasmine.SpyObj<ToastService>;

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(async () => {
    users = jasmine.createSpyObj<UserService>('UserService', ['getMe', 'updateMe', 'changePassword']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['updateCurrentUser', 'logout']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    users.getMe.and.returnValue(of(PROFILE));
    auth.logout.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [ProfileComponent],
      providers: [
        { provide: UserService, useValue: users },
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('profili gösterir ve adı forma doldurur', () => {
    const content = text();
    expect(content).toContain('ayse@ornek.com');
    expect(content).toContain('Kullanıcı');
    expect(content).toContain('01.11.2026');
    expect(component.profileForm.get('fullName')?.value).toBe('Ayşe Yılmaz');
    expect(component.profileForm.pristine).toBeTrue();
  });

  it('profil yüklenemezse tekrar denetir', () => {
    users.getMe.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.profile = null;
    component.load();

    expect(text()).toContain('Profil bilgileri yüklenemedi');
    users.getMe.and.returnValue(of(PROFILE));
    component.load();
    expect(component.profile as UserDetail | null).toEqual(PROFILE);
  });

  it('adı günceller, navbar\'daki kullanıcıyı da günceller', () => {
    const updated = { ...PROFILE, fullName: 'Ayşe Kaya' };
    users.updateMe.and.returnValue(of(updated));
    component.profileForm.get('fullName')?.setValue('  Ayşe Kaya ');

    component.saveProfile();

    expect(users.updateMe).toHaveBeenCalledWith('Ayşe Kaya');
    expect(auth.updateCurrentUser).toHaveBeenCalledWith({ id: 7, email: 'ayse@ornek.com', fullName: 'Ayşe Kaya', role: 'USER' });
    expect(toast.success).toHaveBeenCalled();
    expect(component.profileForm.pristine).toBeTrue();
  });

  it('ad değişmediyse istek göndermez; boş ad gönderilmez', () => {
    component.profileForm.get('fullName')?.setValue('Ayşe Yılmaz ');
    component.saveProfile();
    component.profileForm.get('fullName')?.setValue('');
    component.saveProfile();

    expect(users.updateMe).not.toHaveBeenCalled();
    expect(text()).toContain('Bu alan zorunlu');
  });

  it('şifre değişince çıkış yapıp bilgi mesajıyla login\'e gönderir', () => {
    users.changePassword.and.returnValue(of(undefined));
    component.passwordForm.setValue({
      currentPassword: 'Eski.Sifre1', newPassword: 'Yeni.Sifre1', confirmPassword: 'Yeni.Sifre1',
    });

    component.changePassword();

    expect(users.changePassword).toHaveBeenCalledWith({ currentPassword: 'Eski.Sifre1', newPassword: 'Yeni.Sifre1' });
    expect(auth.logout).toHaveBeenCalledWith({ state: { email: 'ayse@ornek.com', notice: 'passwordChanged' } });
  });

  it('yeni şifreler eşleşmezse veya kısa ise göndermez', () => {
    component.passwordForm.setValue({ currentPassword: 'Eski.Sifre1', newPassword: 'Yeni.Sifre1', confirmPassword: 'Yeni.Sifre2' });
    component.changePassword();
    expect(text()).toContain('Şifreler eşleşmiyor');

    component.passwordForm.setValue({ currentPassword: 'Eski.Sifre1', newPassword: 'kisa', confirmPassword: 'kisa' });
    component.changePassword();
    expect(text()).toContain('En az 8 karakter');
    expect(users.changePassword).not.toHaveBeenCalled();
  });

  it('mevcut şifre hatalıysa sunucu mesajını alanın altında gösterir', () => {
    users.changePassword.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        status: 400, error: 'INVALID_CURRENT_PASSWORD', message: 'Mevcut şifre hatalı', path: '', timestamp: '',
        fieldErrors: [{ field: 'currentPassword', message: 'mevcut şifre hatalı' }],
      },
    })));
    component.passwordForm.setValue({ currentPassword: 'Yanlis.1234', newPassword: 'Yeni.Sifre1', confirmPassword: 'Yeni.Sifre1' });

    component.changePassword();

    expect(auth.logout).not.toHaveBeenCalled();
    expect(component.changingPassword).toBeFalse();
    const error = (fixture.nativeElement as HTMLElement);
    fixture.detectChanges();
    expect(error.querySelector('#current-password-error')?.textContent).toContain('Mevcut şifre hatalı');
    expect(component.passwordError).toBeNull();
  });
});
