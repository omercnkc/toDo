import { FormGroup } from '@angular/forms';

import { ApiError } from '../../core/models/api-error.model';

/**
 * Sunucunun `fieldErrors`'ını aynı adlı form alanlarına `server` hatası olarak yazar (docs/08).
 * Kullanıcı alanı değiştirince Angular doğrulamayı yeniden çalıştırır ve `server` hatası kendiliğinden kalkar.
 *
 * @returns formun üstünde gösterilecek mesaj: hiçbir alana eşlenemeyen hata varsa sunucu mesajı, yoksa null
 */
export function applyServerErrors(form: FormGroup, error: ApiError): string | null {
  const fieldErrors = error.fieldErrors ?? [];
  let unmatched = fieldErrors.length === 0;
  for (const fieldError of fieldErrors) {
    const control = form.get(fieldError.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: fieldError.message });
      control.markAsTouched();
    } else {
      unmatched = true;
    }
  }
  return unmatched ? error.message : null;
}

/**
 * Sayfadan ayrılmadan dönülecek iç yol mu? Açık yönlendirmeyi (open redirect) önler:
 * `//kotu-site.com` veya `https://...` gibi dış adresler ve login/register sayfaları kabul edilmez.
 */
export function isSafeReturnUrl(url: unknown): url is string {
  return (
    typeof url === 'string' &&
    url.startsWith('/') &&
    !url.startsWith('//') &&
    !url.startsWith('/\\') &&
    !/^\/(login|register)(\/|\?|$)/.test(url)
  );
}
