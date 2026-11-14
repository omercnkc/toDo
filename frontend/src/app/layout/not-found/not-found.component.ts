import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Bilinmeyen adresler. Çerçevesiz tek sayfa: giriş yapmamış biri de görebilir.
 * "Ana sayfa" `/` adresine gider; oradan giriş durumuna göre panele veya login'e yönlenir.
 */
@Component({
  selector: 'app-not-found',
  template: `
    <main class="min-vh-100 d-flex align-items-center justify-content-center px-3 text-center">
      <div>
        <p class="display-3 fw-bold text-primary mb-2" aria-hidden="true">404</p>
        <h1 class="h4">Sayfa bulunamadı</h1>
        <p class="text-muted mb-4">Aradığın sayfa taşınmış, silinmiş ya da hiç var olmamış olabilir.</p>
        <a routerLink="/" class="btn btn-primary">Ana sayfaya dön</a>
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
