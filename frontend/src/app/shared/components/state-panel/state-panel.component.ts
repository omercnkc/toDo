import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Sayfanın gösterecek verisi olmadığında tek tip durum kartı: yüklenemedi, bulunamadı, erişim yok, boş.
 * Düğmeler içerik olarak verilir.
 *
 * ```html
 * <app-state-panel heading="Proje yüklenemedi" message="Bağlantını kontrol edip tekrar dene." tone="error">
 *   <button class="btn btn-outline-secondary" (click)="retry()">Tekrar dene</button>
 * </app-state-panel>
 * ```
 *
 * - `tone="error"` ekran okuyucuya hemen duyurulur (`role="alert"`); boş durumlar sessizdir.
 * - Başlık seviyesi sayfaya göre seçilir: sayfanın tek içeriğiyse 1, sayfa başlığının altındaysa 2.
 * - Eski veri ekrandayken başarısız olan yenilemeler için bu değil, veriyi silmeyen satır içi uyarı kullanılır.
 */
@Component({
  selector: 'app-state-panel',
  template: `
    <div class="card shadow-sm border-0" [attr.role]="tone === 'error' ? 'alert' : null">
      <div class="card-body px-3 py-5 p-md-5 text-center">
        <p class="h5 mb-2" role="heading" [attr.aria-level]="level">{{ heading }}</p>
        <p *ngIf="message" class="text-muted mb-0">{{ message }}</p>
        <div class="state-actions d-flex flex-wrap justify-content-center gap-2 mt-3"><ng-content></ng-content></div>
      </div>
    </div>
  `,
  styles: [
    `
      .state-actions:empty {
        display: none !important;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatePanelComponent {
  @Input() heading = '';
  @Input() message = '';
  @Input() level: 1 | 2 | 3 = 2;
  @Input() tone: 'error' | 'empty' = 'empty';
}
