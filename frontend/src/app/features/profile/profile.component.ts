import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../core/auth/auth.service';
import { extractApiError } from '../../core/models/api-error.model';
import { LoginNavigationState } from '../../core/models/auth.model';
import { UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { fieldsMatch, newPasswordValidators, PASSWORD_MIN_LENGTH } from '../../shared/forms/validators';

type PasswordField = 'currentPassword' | 'newPassword' | 'confirmPassword';

/**
 * Profil sayfası (docs/06 `/users/me`).
 * - Ad güncellenince navbar'daki ad da değişir (`AuthService.updateCurrentUser`).
 * - Şifre değişince sunucu tüm oturumları kapatır; kullanıcı çıkış yaptırılıp bilgi mesajıyla
 *   login'e gönderilir (e-postası doldurulmuş olarak).
 */
@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit, OnDestroy {
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;

  profile: UserDetail | null = null;
  loading = true;
  loadFailed = false;

  readonly profileForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
  });
  savingProfile = false;
  profileError: string | null = null;

  readonly passwordForm: FormGroup = this.fb.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', newPasswordValidators()],
      confirmPassword: ['', Validators.required],
    },
    { validators: fieldsMatch('newPassword', 'confirmPassword') },
  );
  changingPassword = false;
  passwordError: string | null = null;
  showPasswords = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly users: UserService,
    private readonly auth: AuthService,
    private readonly toast: ToastService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadFailed = false;
    this.users
      .getMe()
      .pipe(
        finalize(() => (this.loading = false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (profile) => this.setProfile(profile),
        error: () => (this.loadFailed = true),
      });
  }

  // ---------- ad ----------

  get fullNameControl(): AbstractControl {
    return this.profileForm.get('fullName') as AbstractControl;
  }

  saveProfile(): void {
    this.profileError = null;
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const fullName = (this.fullNameControl.value as string).trim();
    if (fullName === this.profile?.fullName) {
      this.profileForm.reset({ fullName });
      return;
    }
    this.savingProfile = true;
    this.users
      .updateMe(fullName)
      .pipe(
        finalize(() => (this.savingProfile = false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (profile) => {
          this.setProfile(profile);
          const { id, email, role } = profile;
          this.auth.updateCurrentUser({ id, email, fullName: profile.fullName, role });
          this.toast.success('Profilin güncellendi.');
        },
        error: (error: unknown) => (this.profileError = this.formErrorFor(this.profileForm, error)),
      });
  }

  // ---------- şifre ----------

  passwordControl(name: PasswordField): AbstractControl {
    return this.passwordForm.get(name) as AbstractControl;
  }

  isInvalid(control: AbstractControl): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  get passwordsMismatch(): boolean {
    const confirm = this.passwordControl('confirmPassword');
    return this.passwordForm.hasError('mismatch') && (confirm.touched || confirm.dirty);
  }

  changePassword(): void {
    this.passwordError = null;
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue() as Record<PasswordField, string>;
    this.changingPassword = true;
    this.users
      .changePassword({ currentPassword, newPassword })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          // Sunucu tüm oturumları kapattı: yerel oturumu da kapat, yeni şifreyle giriş iste
          const state: LoginNavigationState = { email: this.profile?.email, notice: 'passwordChanged' };
          this.auth.logout({ state }).subscribe();
        },
        error: (error: unknown) => {
          this.changingPassword = false;
          this.passwordError = this.formErrorFor(this.passwordForm, error);
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setProfile(profile: UserDetail): void {
    this.profile = profile;
    this.profileForm.reset({ fullName: profile.fullName });
  }

  /** Alan hatalarını forma yazar; alana bağlanamayan mesajı döndürür. Ağ/5xx'i ErrorInterceptor gösterir. */
  private formErrorFor(form: FormGroup, error: unknown): string | null {
    if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
      return null;
    }
    const apiError = extractApiError(error);
    return apiError ? applyServerErrors(form, apiError) : 'İşlem tamamlanamadı. Lütfen tekrar deneyin.';
  }
}
