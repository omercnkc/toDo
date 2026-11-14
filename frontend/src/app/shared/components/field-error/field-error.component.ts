import { Component, Input } from '@angular/core';
import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Doğrulayıcının hata ayrıntısı, örn. minlength için `{ requiredLength, actualLength }`. */
type MessageFn = (detail: Record<string, unknown>) => string;

/** Angular doğrulayıcılarının varsayılan Türkçe mesajları. */
const DEFAULT_MESSAGES: Record<string, MessageFn> = {
  required: () => 'Bu alan zorunlu',
  email: () => 'Geçerli bir e-posta adresi girin',
  minlength: (e) => `En az ${e['requiredLength']} karakter olmalı`,
  maxlength: (e) => `En fazla ${e['requiredLength']} karakter olabilir`,
  maxBytes: () => 'Çok uzun (Türkçe karakterler iki karakter sayılır)',
};

/**
 * Form alanının altında ilk hata mesajını gösterir. Kullanıcı alana dokunmadan (veya form
 * gönderilmeden) hata gösterilmez. Sunucudan gelen `server` hatası önceliklidir.
 *
 * `<app-field-error [control]="form.get('email')" id="email-error"></app-field-error>`
 */
@Component({
  selector: 'app-field-error',
  template: `<div *ngIf="message as text" class="invalid-feedback d-block" [attr.id]="errorId">{{ text }}</div>`,
  // input-group (flex satırı) içinde de alanın ALTINA insin: tam genişlik blok
  styles: [':host { display: block; width: 100%; }'],
})
export class FieldErrorComponent {
  @Input() control: AbstractControl | null = null;
  /** Hata anahtarı → mesaj (varsayılanları ezer), örn. `{ minlength: 'En az 8 karakter' }`. */
  @Input() messages: Record<string, string> = {};
  /** Input'un `aria-describedby`'ı ile eşleşen id (ekran okuyucular hatayı alanla birlikte okur). */
  @Input() errorId?: string;

  get message(): string | null {
    const control = this.control;
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    return messageFor(control.errors, this.messages);
  }
}

export function messageFor(errors: ValidationErrors, overrides: Record<string, string> = {}): string {
  if (typeof errors['server'] === 'string') {
    return capitalize(errors['server']);
  }
  const key = Object.keys(errors)[0];
  return overrides[key] ?? DEFAULT_MESSAGES[key]?.(errors[key]) ?? 'Geçersiz değer';
}

/** Sunucu alan mesajları küçük harfle gelir ("boş olamaz"); alan altında cümle başı büyük olsun. */
function capitalize(text: string): string {
  return text.charAt(0).toLocaleUpperCase('tr-TR') + text.slice(1);
}
