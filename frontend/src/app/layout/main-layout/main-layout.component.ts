import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

/**
 * Giriş gerektiren tüm sayfaların çerçevesi: üstte navbar, altında sayfa içeriği.
 * "İçeriğe atla" bağlantısı klavye kullanıcılarının menüyü her sayfada geçebilmesi için;
 * yalnızca odaklanınca görünür.
 *
 * Sayfa (yol) değişince odak içeriğe taşınır ve sayfa başa kayar: SPA'da tarayıcı bunu kendisi
 * yapmaz; odak tıklanan bağlantıda kalır, klavye kullanıcısı menüden yeniden geçmek zorunda kalırdı.
 * Yalnızca sorgu parametresi değişen gezinmelerde (filtre, sayfalama, sıralama) odak yerinde kalır;
 * geri/ileri tuşunda tarayıcının kaydırma konumuna dokunulmaz.
 *
 * OnPush DEĞİL: router-outlet'e yerleşen sayfalar bu bileşenin görünümü altında kontrol edilir;
 * OnPush olsaydı OnPush olmayan sayfalar (örn. profil) HTTP yanıtından sonra yeniden çizilmezdi.
 */
@Component({
  selector: 'app-main-layout',
  template: `
    <a href="#main-content" class="skip-link visually-hidden-focusable" (click)="skipToContent($event)">İçeriğe atla</a>
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
export class MainLayoutComponent implements OnInit, OnDestroy {
  @ViewChild('content', { static: true }) private readonly content!: ElementRef<HTMLElement>;

  private subscription?: Subscription;
  private currentPath: string | null = null;
  private popstate = false;

  constructor(private readonly router: Router) {}

  ngOnInit(): void {
    // Layout ilk sayfayla birlikte oluşur; o sayfanın yolu başlangıç kabul edilir (açılışta odak taşınmaz)
    this.currentPath = pathOf(this.router.url);
    this.subscription = this.router.events
      .pipe(filter((e): e is NavigationStart | NavigationEnd => e instanceof NavigationStart || e instanceof NavigationEnd))
      .subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.popstate = event.navigationTrigger === 'popstate';
          return;
        }
        const path = pathOf(event.urlAfterRedirects);
        if (path === this.currentPath) {
          return;
        }
        this.currentPath = path;
        this.content.nativeElement.focus({ preventScroll: true });
        if (!this.popstate) {
          window.scrollTo(0, 0);
        }
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  /** Router `#main-content`'i bir gezinme sanmasın diye odak elle taşınır. */
  skipToContent(event: Event): void {
    event.preventDefault();
    this.content.nativeElement.focus();
  }
}

function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}
