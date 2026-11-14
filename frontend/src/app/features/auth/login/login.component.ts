import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { extractApiError } from '../../../core/models/api-error.model';
import { LoginRequest } from '../../../core/models/auth.model';
import { applyServerErrors, isSafeReturnUrl } from '../../../shared/forms/server-errors';
import { trimmedEmail } from '../../../shared/forms/validators';

/** Kayıt sayfasından gelirken router state ile taşınan bilgi (e-posta URL'de görünmesin diye). */
export interface LoginNavigationState {
  registeredEmail?: string;
}

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
})
export class LoginComponent {
  // Angular 13: tipli formlar yok (v14+); alanlara get() ile erişilir
  readonly form: FormGroup = this.fb.group({
    email: ['', [Validators.required, trimmedEmail]],
    password: ['', Validators.required],
  });

  submitting = false;
  showPassword = false;
  /** Bir alana bağlanamayan sunucu hatası (örn. "E-posta veya şifre hatalı"). */
  formError: string | null = null;
  /** Kayıttan yeni gelindiyse başarı mesajı gösterilir. */
  readonly justRegistered: boolean;

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    location: Location,
  ) {
    const state = location.getState() as LoginNavigationState | null;
    this.justRegistered = !!state?.registeredEmail;
    if (state?.registeredEmail) {
      this.control('email').setValue(state.registeredEmail);
    }
  }

  control(name: 'email' | 'password'): AbstractControl {
    return this.form.get(name) as AbstractControl;
  }

  isInvalid(name: 'email' | 'password'): boolean {
    const control = this.control(name);
    return control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.formError = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue() as LoginRequest;
    this.submitting = true;
    this.auth
      .login({ email: email.trim(), password })
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.returnUrl()),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private returnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return isSafeReturnUrl(returnUrl) ? returnUrl : '/dashboard';
  }

  private handleError(error: unknown): void {
    // Ağ hatası ve 5xx'i ErrorInterceptor bildirim olarak gösterir
    if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
      return;
    }
    const apiError = extractApiError(error);
    this.formError = apiError ? applyServerErrors(this.form, apiError) : 'Giriş yapılamadı. Lütfen tekrar deneyin.';
    if (apiError?.error === 'INVALID_CREDENTIALS') {
      this.control('password').reset();
    }
  }
}
