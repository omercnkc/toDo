import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { extractApiError } from '../../../core/models/api-error.model';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { fieldsMatch, maxUtf8Bytes, trimmedEmail } from '../../../shared/forms/validators';
import { LoginNavigationState } from '../login/login.component';

type RegisterField = 'fullName' | 'email' | 'password' | 'confirmPassword';

/** Şifre kuralları backend ile aynı (docs/07): en az 8 karakter, en fazla 72 bayt. */
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_BYTES = 72;

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;

  readonly form: FormGroup = this.fb.group(
    {
      fullName: ['', [Validators.required, Validators.maxLength(120)]],
      email: ['', [Validators.required, trimmedEmail, Validators.maxLength(150)]],
      password: [
        '',
        [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), maxUtf8Bytes(PASSWORD_MAX_BYTES)],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: fieldsMatch('password', 'confirmPassword') },
  );

  submitting = false;
  showPassword = false;
  formError: string | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  control(name: RegisterField): AbstractControl {
    return this.form.get(name) as AbstractControl;
  }

  isInvalid(name: RegisterField): boolean {
    const control = this.control(name);
    const touched = control.touched || control.dirty;
    if (name === 'confirmPassword') {
      return touched && (control.invalid || this.passwordsMismatch);
    }
    return touched && control.invalid;
  }

  get passwordsMismatch(): boolean {
    const confirm = this.control('confirmPassword');
    return this.form.hasError('mismatch') && (confirm.touched || confirm.dirty);
  }

  submit(): void {
    this.formError = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { fullName, email, password } = this.form.getRawValue() as Record<RegisterField, string>;
    this.submitting = true;
    this.auth
      .register({ fullName: fullName.trim(), email: email.trim(), password })
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: (user) => {
          const state: LoginNavigationState = { registeredEmail: user.email };
          void this.router.navigate(['/login'], { state });
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  private handleError(error: unknown): void {
    if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
      return; // ErrorInterceptor bildirim gösterir
    }
    const apiError = extractApiError(error);
    this.formError = apiError ? applyServerErrors(this.form, apiError) : 'Kayıt yapılamadı. Lütfen tekrar deneyin.';
  }
}
