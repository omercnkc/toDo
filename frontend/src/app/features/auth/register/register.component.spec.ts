import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { SharedModule } from '../../../shared/shared.module';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['register']);
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [RegisterComponent, AuthShellComponent],
      providers: [{ provide: AuthService, useValue: auth }],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function fill(values: Partial<Record<'fullName' | 'email' | 'password' | 'confirmPassword', string>>): void {
    component.form.patchValue({
      fullName: 'Ayşe Yılmaz', email: 'ayse@ornek.com', password: 'Gizli.Sifre1', confirmPassword: 'Gizli.Sifre1',
      ...values,
    });
  }

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('geçerli formla kayıt olur ve e-postayı state ile login\'e taşır', () => {
    auth.register.and.returnValue(of({ id: 3, email: 'ayse@ornek.com', fullName: 'Ayşe Yılmaz', role: 'USER' }));
    fill({ fullName: '  Ayşe Yılmaz  ', email: ' ayse@ornek.com ' });

    component.submit();

    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Ayşe Yılmaz', email: 'ayse@ornek.com', password: 'Gizli.Sifre1',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { state: { registeredEmail: 'ayse@ornek.com' } });
  });

  it('şifreler eşleşmezse göndermez', () => {
    fill({ confirmPassword: 'Baska.Sifre1' });

    component.submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(text()).toContain('Şifreler eşleşmiyor');
  });

  it('kısa şifreyi reddeder', () => {
    fill({ password: 'kisa', confirmPassword: 'kisa' });

    component.submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(text()).toContain('En az 8 karakter olmalı');
  });

  it('72 baytı aşan şifreyi reddeder', () => {
    const long = 'ş'.repeat(40); // 40 karakter, 80 bayt
    fill({ password: long, confirmPassword: long });

    component.submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(component.form.get('password')?.hasError('maxBytes')).toBeTrue();
  });

  it('kayıtlı e-posta hatası e-posta alanının altında görünür', () => {
    auth.register.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: {
        status: 409, error: 'EMAIL_ALREADY_EXISTS', message: 'Bu e-posta adresi zaten kayıtlı',
        fieldErrors: [{ field: 'email', message: 'bu e-posta adresi zaten kayıtlı' }],
      },
    })));
    fill({});

    component.submit();

    expect(component.formError).toBeNull();
    expect(component.form.get('email')?.errors).toEqual({ server: 'bu e-posta adresi zaten kayıtlı' });
    expect(text()).toContain('Bu e-posta adresi zaten kayıtlı');
    expect(component.submitting).toBeFalse();
  });

  it('sunucu doğrulama hataları alanlara eşlenir', () => {
    auth.register.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        status: 400, error: 'VALIDATION_ERROR', message: 'Doğrulama hatası',
        fieldErrors: [{ field: 'fullName', message: 'en fazla 120 karakter olabilir' }],
      },
    })));
    fill({});

    component.submit();

    expect(text()).toContain('En fazla 120 karakter olabilir');
  });
});
