import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/models/user.model';
import { SharedModule } from '../../../shared/shared.module';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';
import { LoginComponent } from './login.component';

const USER: User = { id: 1, email: 'ali@ornek.com', fullName: 'Ali', role: 'USER' };

function httpError(status: number, body: object): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;
  let queryParams: Record<string, string>;
  let navigationState: unknown;

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [LoginComponent, AuthShellComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    }).compileComponents();
    spyOn(TestBed.inject(Location), 'getState').and.returnValue(navigationState);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fill(email: string, password: string): void {
    component.form.setValue({ email, password });
  }

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    queryParams = {};
    navigationState = null;
  });

  it('geçersiz formu göndermez ve alan hatalarını gösterir', async () => {
    await create();

    component.submit();

    expect(auth.login).not.toHaveBeenCalled();
    expect(text()).toContain('Bu alan zorunlu');
  });

  it('başarılı girişte dashboard\'a gider', async () => {
    await create();
    auth.login.and.returnValue(of(USER));
    fill('ali@ornek.com', 'Gizli.Sifre1');

    component.submit();

    expect(auth.login).toHaveBeenCalledWith({ email: 'ali@ornek.com', password: 'Gizli.Sifre1' });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('e-postadaki baş/son boşlukları kırpar', async () => {
    await create();
    auth.login.and.returnValue(of(USER));
    fill('  ali@ornek.com ', 'Gizli.Sifre1');

    component.submit();

    expect(auth.login).toHaveBeenCalledWith({ email: 'ali@ornek.com', password: 'Gizli.Sifre1' });
  });

  it('güvenli returnUrl varsa oraya döner', async () => {
    queryParams = { returnUrl: '/projects/5' };
    await create();
    auth.login.and.returnValue(of(USER));
    fill('ali@ornek.com', 'Gizli.Sifre1');

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/projects/5');
  });

  it('dış adrese yönlendirmez (açık yönlendirme koruması)', async () => {
    queryParams = { returnUrl: '//kotu-site.com' };
    await create();
    auth.login.and.returnValue(of(USER));
    fill('ali@ornek.com', 'Gizli.Sifre1');

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('hatalı bilgilerde mesaj gösterir ve şifreyi temizler', async () => {
    await create();
    auth.login.and.returnValue(throwError(() => httpError(401, {
      status: 401, error: 'INVALID_CREDENTIALS', message: 'E-posta veya şifre hatalı',
    })));
    fill('ali@ornek.com', 'yanlis');

    component.submit();

    expect(text()).toContain('E-posta veya şifre hatalı');
    expect(component.form.get('password')?.value).toBeFalsy();
    expect(component.form.get('email')?.value).toBe('ali@ornek.com');
    expect(component.submitting).toBeFalse();
  });

  it('pasif hesap mesajını gösterir', async () => {
    await create();
    auth.login.and.returnValue(throwError(() => httpError(403, {
      status: 403, error: 'ACCOUNT_DISABLED', message: 'Hesabınız pasif durumda',
    })));
    fill('ali@ornek.com', 'Gizli.Sifre1');

    component.submit();

    expect(text()).toContain('Hesabınız pasif durumda');
  });

  it('sunucu hatasında form mesajı göstermez (bildirimi interceptor gösterir)', async () => {
    await create();
    auth.login.and.returnValue(throwError(() => httpError(500, {
      status: 500, error: 'INTERNAL_ERROR', message: 'Beklenmeyen bir hata oluştu',
    })));
    fill('ali@ornek.com', 'Gizli.Sifre1');

    component.submit();

    expect(component.formError).toBeNull();
  });

  it('kayıttan gelince e-postayı doldurur ve başarı mesajı gösterir', async () => {
    navigationState = { registeredEmail: 'yeni@ornek.com' };
    await create();

    expect(component.form.get('email')?.value).toBe('yeni@ornek.com');
    expect(text()).toContain('Kaydın oluşturuldu');
  });

  it('şifre göster/gizle', async () => {
    await create();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#login-password');
    expect(input?.type).toBe('password');

    component.showPassword = true;
    fixture.detectChanges();

    expect(input?.type).toBe('text');
  });
});
