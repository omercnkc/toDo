import { Component, ElementRef, ViewChild } from '@angular/core';

/**
 * Giriş gerektiren tüm sayfaların çerçevesi: üstte navbar, altında sayfa içeriği.
 * "İçeriğe atla" bağlantısı klavye kullanıcılarının menüyü her sayfada geçebilmesi için;
 * yalnızca odaklanınca görünür.
 *
 * OnPush DEĞİL: router-outlet'e yerleşen sayfalar bu bileşenin görünümü altında kontrol edilir;
 * OnPush olsaydı OnPush olmayan sayfalar (örn. profil) HTTP yanıtından sonra yeniden çizilmezdi.
 */
@Component({
  selector: 'app-main-layout',
  template: `
    <a href="#" class="skip-link visually-hidden-focusable" (click)="skipToContent($event)">İçeriğe atla</a>
    <app-navbar></app-navbar>
    <main #content id="main-content" class="container py-4" tabindex="-1">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [
    `
      .skip-link {
        position: absolute;
        top: 0.5rem;
        left: 0.5rem;
        z-index: 1100;
        padding: 0.5rem 1rem;
        background: #fff;
        border-radius: 0.5rem;
      }
      main:focus {
        outline: none;
      }
    `,
  ],
})
export class MainLayoutComponent {
  @ViewChild('content', { static: true }) private readonly content!: ElementRef<HTMLElement>;

  /** `href="#..."` router'la çakışmasın diye odak elle taşınır. */
  skipToContent(event: Event): void {
    event.preventDefault();
    this.content.nativeElement.focus();
  }
}
