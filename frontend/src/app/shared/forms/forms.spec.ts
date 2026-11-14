import { FormControl, FormGroup, Validators } from '@angular/forms';

import { messageFor } from '../components/field-error/field-error.component';
import { applyServerErrors, isSafeReturnUrl } from './server-errors';
import { fieldsMatch, maxUtf8Bytes, trimmedEmail } from './validators';

describe('Form araçları', () => {
  describe('maxUtf8Bytes', () => {
    const validator = maxUtf8Bytes(72);

    it('bayt sayısına bakar (Türkçe karakter 2 bayt)', () => {
      expect(validator(new FormControl('a'.repeat(72)))).toBeNull();
      expect(validator(new FormControl('ş'.repeat(36)))).toBeNull(); // 72 bayt
      expect(validator(new FormControl('ş'.repeat(37)))).toEqual({ maxBytes: { max: 72, actual: 74 } });
    });

    it('boş değeri başka doğrulayıcılara bırakır', () => {
      expect(validator(new FormControl(''))).toBeNull();
      expect(validator(new FormControl(null))).toBeNull();
    });
  });

  describe('trimmedEmail', () => {
    it('baş/son boşluğu olan geçerli e-postayı kabul eder', () => {
      expect(trimmedEmail(new FormControl(' ali@ornek.com '))).toBeNull();
    });

    it('geçersiz e-postayı reddeder', () => {
      expect(trimmedEmail(new FormControl('ali@'))).toEqual({ email: true });
      expect(trimmedEmail(new FormControl('ali veli@ornek.com'))).toEqual({ email: true });
    });
  });

  describe('fieldsMatch', () => {
    const group = (a: string, b: string) =>
      new FormGroup({ password: new FormControl(a), confirm: new FormControl(b) }, fieldsMatch('password', 'confirm'));

    it('farklıysa grup hatası verir', () => {
      expect(group('Gizli.Sifre1', 'Gizli.Sifre2').hasError('mismatch')).toBeTrue();
      expect(group('Gizli.Sifre1', 'Gizli.Sifre1').hasError('mismatch')).toBeFalse();
    });

    it('tekrar alanı boşken hata vermez (önce required görünsün)', () => {
      expect(group('Gizli.Sifre1', '').hasError('mismatch')).toBeFalse();
    });
  });

  describe('applyServerErrors', () => {
    let form: FormGroup;

    beforeEach(() => {
      form = new FormGroup({ email: new FormControl('a@b.com', Validators.required), password: new FormControl('x') });
    });

    it('alan hatalarını ilgili kontrollere yazar, form mesajı döndürmez', () => {
      const message = applyServerErrors(form, {
        status: 409, error: 'EMAIL_ALREADY_EXISTS', message: 'Bu e-posta adresi zaten kayıtlı', path: '/auth/register',
        timestamp: '', fieldErrors: [{ field: 'email', message: 'bu e-posta adresi zaten kayıtlı' }],
      });

      expect(message).toBeNull();
      expect(form.get('email')?.errors).toEqual({ server: 'bu e-posta adresi zaten kayıtlı' });
      expect(form.get('email')?.touched).toBeTrue();
    });

    it('alan değişince sunucu hatası kalkar', () => {
      applyServerErrors(form, {
        status: 409, error: 'X', message: 'm', path: '', timestamp: '',
        fieldErrors: [{ field: 'email', message: 'zaten kayıtlı' }],
      });
      form.get('email')?.setValue('yeni@b.com');

      expect(form.get('email')?.errors).toBeNull();
    });

    it('alan hatası yoksa veya alan bilinmiyorsa form mesajı döner', () => {
      expect(applyServerErrors(form, {
        status: 401, error: 'INVALID_CREDENTIALS', message: 'E-posta veya şifre hatalı', path: '', timestamp: '',
      })).toBe('E-posta veya şifre hatalı');
      expect(applyServerErrors(form, {
        status: 400, error: 'VALIDATION_ERROR', message: 'Doğrulama hatası', path: '', timestamp: '',
        fieldErrors: [{ field: 'bilinmeyen', message: 'x' }],
      })).toBe('Doğrulama hatası');
    });
  });

  describe('isSafeReturnUrl', () => {
    it('site içi yolları kabul eder', () => {
      expect(isSafeReturnUrl('/dashboard')).toBeTrue();
      expect(isSafeReturnUrl('/projects/5?tab=uyeler')).toBeTrue();
    });

    it('dış adresleri ve login/register\'ı reddeder', () => {
      for (const url of ['//kotu-site.com', '/\\kotu-site.com', 'https://kotu-site.com', 'javascript:alert(1)',
        '/login', '/login?returnUrl=%2Fx', '/register', '', null, undefined]) {
        expect(isSafeReturnUrl(url)).withContext(String(url)).toBeFalse();
      }
    });

    it('login ile başlayan başka yolları reddetmez', () => {
      expect(isSafeReturnUrl('/logins-raporu')).toBeTrue();
    });
  });

  describe('alan hata mesajları', () => {
    it('sunucu mesajı önceliklidir ve büyük harfle başlar', () => {
      expect(messageFor({ required: true, server: 'boş olamaz' })).toBe('Boş olamaz');
    });

    it('varsayılan Türkçe mesajlar ve özel mesajlar', () => {
      expect(messageFor({ required: true })).toBe('Bu alan zorunlu');
      expect(messageFor({ minlength: { requiredLength: 8, actualLength: 3 } })).toBe('En az 8 karakter olmalı');
      expect(messageFor({ email: true }, { email: 'E-posta hatalı' })).toBe('E-posta hatalı');
      expect(messageFor({ bilinmeyen: true })).toBe('Geçersiz değer');
    });
  });
});
