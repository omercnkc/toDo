import { AbstractControl, FormControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * E-posta biçimi, baş/son boşluklar kırpılarak. Kopyala-yapıştırla gelen boşluk yüzünden
 * "geçersiz e-posta" görülmesin; gönderirken değer zaten kırpılır (backend de kırpar).
 */
export function trimmedEmail(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value === 'string' ? Validators.email(new FormControl(value.trim())) : null;
}

/**
 * UTF-8 bayt sınırı. Şifrelerde backend'in 72 bayt (BCrypt) sınırıyla aynı kural:
 * Türkçe karakterler 2 bayt olduğu için karakter sayısı yetmez (docs/07).
 */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    const actual = new TextEncoder().encode(value).length;
    return actual > max ? { maxBytes: { max, actual } } : null;
  };
}

/** Form grubunda iki alanın aynı olması (örn. şifre tekrarı). Hata grup üzerinde: `{ mismatch: true }`. */
export function fieldsMatch(field: string, confirmField: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const value = group.get(field)?.value;
    const confirm = group.get(confirmField)?.value;
    return value && confirm && value !== confirm ? { mismatch: true } : null;
  };
}

/** Şifre kuralları backend ile aynı (docs/07): en az 8 karakter, en fazla 72 bayt (BCrypt sınırı). */
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_BYTES = 72;

/** Yeni şifre alanının doğrulayıcıları (kayıt ve şifre değiştirme). */
export function newPasswordValidators(): ValidatorFn[] {
  return [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), maxUtf8Bytes(PASSWORD_MAX_BYTES)];
}
