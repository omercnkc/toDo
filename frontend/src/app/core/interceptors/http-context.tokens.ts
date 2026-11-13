import { HttpContextToken } from '@angular/common/http';

/**
 * ErrorInterceptor genel hata bildirimini göstermesin (hatayı çağıran kendisi ele alıyor).
 * Kullanım: `http.get(url, { context: new HttpContext().set(SKIP_ERROR_TOAST, true) })`
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);
