import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { APP_INITIALIZER, NgModule, Optional, SkipSelf } from '@angular/core';

import { AuthService } from './auth/auth.service';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';

export function initializeAuth(auth: AuthService): () => Promise<void> {
  return () => auth.initialize();
}

/**
 * Uygulama genelinde tek sefer yüklenen altyapı (docs/08 "core").
 * Interceptor sırası önemli: istek Auth → Error yönünde, yanıt Error → Auth yönünde geçer.
 * Böylece Error ilk 401'i görmezden gelir, Auth yenileyip tekrar dener; tekrar denemenin
 * hatası yine Error'dan geçer.
 */
@NgModule({
  imports: [HttpClientModule],
  providers: [
    // Sayfa yenilenince oturumu cookie ile sessizce geri al; uygulama bu bitmeden açılmaz
    { provide: APP_INITIALIZER, useFactory: initializeAuth, deps: [AuthService], multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
  ],
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parent?: CoreModule) {
    if (parent) {
      throw new Error('CoreModule yalnızca AppModule içinde import edilmeli');
    }
  }
}
