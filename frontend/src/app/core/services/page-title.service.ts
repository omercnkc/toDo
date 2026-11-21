import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

export const APP_NAME = 'Görev Yöneticisi';

/**
 * Sekme başlığı her sayfada ne olduğunu söylesin (WCAG 2.4.2; ekran okuyucular sayfa değişince
 * başlığı okur). Başlık route'un `data.title`'ından gelir: `Projeler · Görev Yöneticisi`.
 * Angular 13'te route `title` özelliği henüz yok (14+), bu yüzden en derindeki tanımlı değer alınır.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleService {
  private started = false;

  constructor(private readonly router: Router, private readonly title: Title) {}

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.title.setTitle(pageTitle(this.router.routerState.snapshot.root)));
  }
}

export function pageTitle(root: ActivatedRouteSnapshot): string {
  let title: string | undefined;
  for (let route: ActivatedRouteSnapshot | null = root; route; route = route.firstChild) {
    const value = route.data?.['title'];
    if (typeof value === 'string' && value) {
      title = value;
    }
  }
  return title ? `${title} · ${APP_NAME}` : APP_NAME;
}
